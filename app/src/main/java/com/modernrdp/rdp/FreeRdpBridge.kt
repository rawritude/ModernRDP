package com.modernrdp.rdp

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.modernrdp.data.model.RdpConnection
import com.modernrdp.data.model.ResolutionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CountDownLatch

/**
 * Bridge to FreeRDP native library (libmodernrdp-native.so).
 *
 * This class wraps JNI calls to FreeRDP's C library. The native code
 * calls back into static methods on this class (see companion object)
 * for session lifecycle events and frame updates.
 */
class FreeRdpBridge {

    private val _sessionState = MutableStateFlow(SessionState.DISCONNECTED)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _framebuffer = MutableStateFlow<Bitmap?>(null)
    val framebuffer: StateFlow<Bitmap?> = _framebuffer.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _remoteClipboard = MutableStateFlow<String?>(null)
    val remoteClipboard: StateFlow<String?> = _remoteClipboard.asStateFlow()

    // Certificate verification state
    private val _pendingCertificate = MutableStateFlow<CertificateInfo?>(null)
    val pendingCertificate: StateFlow<CertificateInfo?> = _pendingCertificate.asStateFlow()

    @Volatile
    private var certLatch: CountDownLatch? = null

    @Volatile
    private var certAccepted: Boolean = false

    private var nativeInstance: Long = 0
    private var sessionBitmap: Bitmap? = null

    private var sessionWidth: Int = 0
    private var sessionHeight: Int = 0

    /** Whether cert verification is enabled (vs auto-accept). */
    var certVerificationEnabled: Boolean = false

    fun initialize(context: Context): Boolean {
        if (nativeInstance != 0L) {
            Log.w(TAG, "Already initialized, freeing previous instance")
            release()
        }

        nativeInstance = nativeNew(context)
        if (nativeInstance == 0L) {
            Log.e(TAG, "nativeNew failed")
            return false
        }

        synchronized(instanceMap) {
            instanceMap[nativeInstance] = this
        }

        Log.i(TAG, "Initialized FreeRDP instance: $nativeInstance")
        return true
    }

    fun connect(connection: RdpConnection, screenWidth: Int, screenHeight: Int): Boolean {
        if (nativeInstance == 0L) {
            Log.e(TAG, "Not initialized")
            return false
        }

        _sessionState.value = SessionState.CONNECTING
        _errorMessage.value = null

        val (width, height) = when (connection.resolutionMode) {
            ResolutionMode.MATCH_DEVICE -> screenWidth to screenHeight
            ResolutionMode.CUSTOM -> connection.customWidth to connection.customHeight
            ResolutionMode.FIT_SCREEN -> screenWidth to screenHeight
        }

        val args = buildConnectionArgs(connection, width, height)
        Log.d(TAG, "Connection args: ${args.joinToString(" ")}")

        if (!nativeParseArguments(nativeInstance, args)) {
            Log.e(TAG, "Failed to parse connection arguments")
            _sessionState.value = SessionState.ERROR
            _errorMessage.value = "Invalid connection settings"
            return false
        }

        if (!nativeConnect(nativeInstance)) {
            Log.e(TAG, "Failed to start connection")
            _sessionState.value = SessionState.ERROR
            _errorMessage.value = nativeGetLastError(nativeInstance)
            return false
        }

        return true
    }

    private fun buildConnectionArgs(conn: RdpConnection, width: Int, height: Int): Array<String> {
        val args = mutableListOf<String>()

        args += "ModernRDP"
        args += "/v:${conn.hostname}"
        if (conn.port != 3389) args += "/port:${conn.port}"

        if (conn.username.isNotBlank()) args += "/u:${conn.username}"
        if (conn.password.isNotBlank()) args += "/p:${conn.password}"
        if (conn.domain.isNotBlank()) args += "/d:${conn.domain}"

        args += "/size:${width}x${height}"
        args += "/bpp:${conn.colorDepth}"
        args += "/gdi:sw"
        args += "/gfx"
        args += "/rfx"

        val sec = when {
            conn.useNla -> "nla"
            conn.useTls -> "tls"
            else -> "rdp"
        }
        args += "/sec:$sec"

        if (conn.enableWallpaper) args += "+wallpaper" else args += "-wallpaper"
        if (conn.enableFontSmoothing) args += "+fonts" else args += "-fonts"
        if (conn.enableFullWindowDrag) args += "+window-drag" else args += "-window-drag"
        args += "-aero"
        args += "-menu-anims"
        args += "-themes"

        args += "/kbd:unicode:on"
        args += "/clipboard"

        if (!certVerificationEnabled) {
            args += "/cert:ignore"
        }

        // Dynamic resolution support for fold/unfold resize
        args += "/dynamic-resolution"

        if (conn.gatewayHostname.isNotBlank()) {
            val gwArgs = buildString {
                append("g:${conn.gatewayHostname}")
                append(",p:${conn.gatewayPort}")
                if (conn.gatewayUsername.isNotBlank()) append(",u:${conn.gatewayUsername}")
                if (conn.gatewayPassword.isNotBlank()) append(",pw:${conn.gatewayPassword}")
            }
            args += "/gateway:$gwArgs"
        }

        return args.toTypedArray()
    }

    fun updateGraphics(x: Int, y: Int, width: Int, height: Int) {
        val bitmap = sessionBitmap ?: return
        if (nativeInstance == 0L) return
        nativeUpdateGraphics(nativeInstance, bitmap, x, y, width, height)
        _framebuffer.value = bitmap
    }

    fun sendMouseEvent(x: Int, y: Int, flags: Int) {
        if (nativeInstance == 0L || _sessionState.value != SessionState.CONNECTED) return
        nativeSendCursorEvent(nativeInstance, x, y, flags)
    }

    fun sendKeyEvent(keyCode: Int, down: Boolean) {
        if (nativeInstance == 0L || _sessionState.value != SessionState.CONNECTED) return
        nativeSendKeyEvent(nativeInstance, keyCode, down)
    }

    fun sendUnicodeKey(code: Int, down: Boolean) {
        if (nativeInstance == 0L || _sessionState.value != SessionState.CONNECTED) return
        nativeSendUnicodeKeyEvent(nativeInstance, code, down)
    }

    fun sendClipboardData(text: String) {
        if (nativeInstance == 0L || _sessionState.value != SessionState.CONNECTED) return
        nativeSendClipboardData(nativeInstance, text)
    }

    /** Request remote desktop resize via display control channel. */
    fun requestResize(width: Int, height: Int) {
        if (nativeInstance == 0L || _sessionState.value != SessionState.CONNECTED) return
        nativeSendResizeEvent(nativeInstance, width, height)
    }

    /** Respond to a pending certificate verification prompt. */
    fun respondToCertificate(accept: Boolean) {
        certAccepted = accept
        certLatch?.countDown()
    }

    fun disconnect() {
        if (nativeInstance == 0L) return
        _sessionState.value = SessionState.DISCONNECTING
        nativeDisconnect(nativeInstance)
    }

    fun release() {
        if (nativeInstance != 0L) {
            synchronized(instanceMap) {
                instanceMap.remove(nativeInstance)
            }
            nativeFree(nativeInstance)
            nativeInstance = 0
        }
        sessionBitmap?.recycle()
        sessionBitmap = null
        _framebuffer.value = null
    }

    // ================================================================
    // Native method declarations — implemented in modernrdp_jni.c
    // ================================================================

    private external fun nativeNew(context: Context): Long
    private external fun nativeFree(instance: Long)
    private external fun nativeParseArguments(instance: Long, args: Array<String>): Boolean
    private external fun nativeConnect(instance: Long): Boolean
    private external fun nativeDisconnect(instance: Long): Boolean
    private external fun nativeUpdateGraphics(
        instance: Long, bitmap: Bitmap, x: Int, y: Int, width: Int, height: Int,
    ): Boolean
    private external fun nativeSendCursorEvent(instance: Long, x: Int, y: Int, flags: Int): Boolean
    private external fun nativeSendKeyEvent(instance: Long, keyCode: Int, down: Boolean): Boolean
    private external fun nativeSendUnicodeKeyEvent(instance: Long, code: Int, down: Boolean): Boolean
    private external fun nativeSendClipboardData(instance: Long, data: String): Boolean
    private external fun nativeSendResizeEvent(instance: Long, width: Int, height: Int): Boolean
    private external fun nativeGetVersion(): String
    private external fun nativeGetBuildConfig(): String
    private external fun nativeHasH264(): Boolean
    private external fun nativeGetLastError(instance: Long): String

    // ================================================================
    // Static callbacks — called from native C code via JNI
    // ================================================================

    companion object {
        private const val TAG = "FreeRdpBridge"

        init {
            System.loadLibrary("modernrdp-native")
        }

        private val instanceMap = HashMap<Long, FreeRdpBridge>()

        private fun getBridge(instance: Long): FreeRdpBridge? {
            synchronized(instanceMap) {
                return instanceMap[instance]
            }
        }

        // Mouse event flags (matching FreeRDP PTR_FLAGS)
        const val MOUSE_FLAG_MOVE = 0x0800
        const val MOUSE_FLAG_BUTTON1 = 0x1000
        const val MOUSE_FLAG_BUTTON2 = 0x2000
        const val MOUSE_FLAG_BUTTON3 = 0x4000
        const val MOUSE_FLAG_DOWN = 0x8000
        const val MOUSE_FLAG_WHEEL = 0x0200
        const val MOUSE_FLAG_WHEEL_NEGATIVE = 0x0100

        @JvmStatic
        @Suppress("unused")
        fun onNativePreConnect(instance: Long) {
            Log.d(TAG, "onNativePreConnect: $instance")
        }

        @JvmStatic
        @Suppress("unused")
        fun onNativeConnected(instance: Long) {
            Log.i(TAG, "onNativeConnected: $instance")
            getBridge(instance)?._sessionState?.value = SessionState.CONNECTED
        }

        @JvmStatic
        @Suppress("unused")
        fun onNativeConnectionFailed(instance: Long) {
            Log.e(TAG, "onNativeConnectionFailed: $instance")
            val bridge = getBridge(instance)
            bridge?._sessionState?.value = SessionState.ERROR
            bridge?._errorMessage?.value = "Connection failed"
        }

        @JvmStatic
        @Suppress("unused")
        fun onNativeDisconnecting(instance: Long) {
            Log.d(TAG, "onNativeDisconnecting: $instance")
            getBridge(instance)?._sessionState?.value = SessionState.DISCONNECTING
        }

        @JvmStatic
        @Suppress("unused")
        fun onNativeDisconnected(instance: Long) {
            Log.i(TAG, "onNativeDisconnected: $instance")
            getBridge(instance)?._sessionState?.value = SessionState.DISCONNECTED
        }

        @JvmStatic
        @Suppress("unused")
        fun onNativeSettingsChanged(instance: Long, width: Int, height: Int, bpp: Int) {
            Log.i(TAG, "onNativeSettingsChanged: ${width}x${height} @${bpp}bpp")
            val bridge = getBridge(instance) ?: return

            if (bridge.sessionWidth != width || bridge.sessionHeight != height) {
                bridge.sessionBitmap?.recycle()
                bridge.sessionBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bridge.sessionWidth = width
                bridge.sessionHeight = height
            }
        }

        @JvmStatic
        @Suppress("unused")
        fun onNativeGraphicsUpdate(instance: Long, x: Int, y: Int, width: Int, height: Int) {
            getBridge(instance)?.updateGraphics(x, y, width, height)
        }

        @JvmStatic
        @Suppress("unused")
        fun onNativeGraphicsResize(instance: Long, width: Int, height: Int, bpp: Int) {
            Log.i(TAG, "onNativeGraphicsResize: ${width}x${height} @${bpp}bpp")
            onNativeSettingsChanged(instance, width, height, bpp)
        }

        @JvmStatic
        @Suppress("unused")
        fun onNativeRemoteClipboardChanged(instance: Long, data: String) {
            Log.d(TAG, "Remote clipboard: ${data.take(50)}...")
            getBridge(instance)?._remoteClipboard?.value = data
        }

        /**
         * Called from native code when the server presents a certificate.
         * Blocks the RDP thread until the user accepts or rejects.
         *
         * @return 0 = reject, 1 = accept temporarily, 2 = accept permanently
         */
        @JvmStatic
        @Suppress("unused")
        fun onNativeVerifyCertificate(
            instance: Long,
            host: String,
            subject: String,
            issuer: String,
            fingerprint: String,
            hostMismatch: Boolean,
        ): Int {
            val bridge = getBridge(instance) ?: return 0

            val latch = CountDownLatch(1)
            bridge.certLatch = latch
            bridge.certAccepted = false

            bridge._pendingCertificate.value = CertificateInfo(
                host = host,
                subject = subject,
                issuer = issuer,
                fingerprint = fingerprint,
                hostMismatch = hostMismatch,
            )

            // Block the RDP thread until user responds via UI
            latch.await()

            bridge._pendingCertificate.value = null
            return if (bridge.certAccepted) 1 else 0
        }
    }
}

data class CertificateInfo(
    val host: String,
    val subject: String,
    val issuer: String,
    val fingerprint: String,
    val hostMismatch: Boolean,
)

enum class SessionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR,
}
