package com.modernrdp.rdp

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.modernrdp.data.model.RdpConnection
import com.modernrdp.data.model.ResolutionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridge to FreeRDP native library (libmodernrdp-native.so).
 *
 * This class wraps JNI calls to FreeRDP's C library. The native code
 * calls back into static methods on this class (see companion object)
 * for session lifecycle events and frame updates.
 *
 * Architecture:
 * - Kotlin creates a FreeRDP instance via [initialize]
 * - Connection params are passed as FreeRDP CLI args via [connect]
 * - Native code spawns a background thread for the RDP event loop
 * - Frame updates flow: native EndPaint -> onNativeGraphicsUpdate -> [updateGraphics] -> Compose
 * - Input flows: Compose touch/key -> [sendMouseEvent]/[sendKeyEvent] -> native event queue
 */
class FreeRdpBridge {

    private val _sessionState = MutableStateFlow(SessionState.DISCONNECTED)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _framebuffer = MutableStateFlow<Bitmap?>(null)
    val framebuffer: StateFlow<Bitmap?> = _framebuffer.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var nativeInstance: Long = 0
    private var sessionBitmap: Bitmap? = null

    // Dimensions of the current remote session
    private var sessionWidth: Int = 0
    private var sessionHeight: Int = 0

    /**
     * Create a FreeRDP instance. Must be called before [connect].
     */
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

        // Register this instance for native callbacks
        synchronized(instanceMap) {
            instanceMap[nativeInstance] = this
        }

        Log.i(TAG, "Initialized FreeRDP instance: $nativeInstance")
        return true
    }

    /**
     * Connect to an RDP server using the given connection parameters.
     * Builds FreeRDP CLI arguments and starts the connection thread.
     */
    fun connect(connection: RdpConnection, screenWidth: Int, screenHeight: Int): Boolean {
        if (nativeInstance == 0L) {
            Log.e(TAG, "Not initialized")
            return false
        }

        _sessionState.value = SessionState.CONNECTING
        _errorMessage.value = null

        // Resolve effective resolution
        val (width, height) = when (connection.resolutionMode) {
            ResolutionMode.MATCH_DEVICE -> screenWidth to screenHeight
            ResolutionMode.CUSTOM -> connection.customWidth to connection.customHeight
            ResolutionMode.FIT_SCREEN -> screenWidth to screenHeight
        }

        // Build FreeRDP command-line arguments
        val args = buildConnectionArgs(connection, width, height)
        Log.d(TAG, "Connection args: ${args.joinToString(" ")}")

        // Parse arguments into FreeRDP settings
        if (!nativeParseArguments(nativeInstance, args)) {
            Log.e(TAG, "Failed to parse connection arguments")
            _sessionState.value = SessionState.ERROR
            _errorMessage.value = "Invalid connection settings"
            return false
        }

        // Start the connection (spawns background thread)
        if (!nativeConnect(nativeInstance)) {
            Log.e(TAG, "Failed to start connection")
            _sessionState.value = SessionState.ERROR
            _errorMessage.value = nativeGetLastError(nativeInstance)
            return false
        }

        return true
    }

    /**
     * Build FreeRDP command-line arguments from our connection model.
     * FreeRDP uses a /flag:value syntax.
     */
    private fun buildConnectionArgs(conn: RdpConnection, width: Int, height: Int): Array<String> {
        val args = mutableListOf<String>()

        // Program name (argv[0])
        args += "ModernRDP"

        // Server
        args += "/v:${conn.hostname}"
        if (conn.port != 3389) {
            args += "/port:${conn.port}"
        }

        // Credentials
        if (conn.username.isNotBlank()) args += "/u:${conn.username}"
        if (conn.password.isNotBlank()) args += "/p:${conn.password}"
        if (conn.domain.isNotBlank()) args += "/d:${conn.domain}"

        // Display
        args += "/size:${width}x${height}"
        args += "/bpp:${conn.colorDepth}"
        args += "/gdi:sw" // Always software GDI on Android

        // Codecs — prefer GFX pipeline for modern servers
        args += "/gfx"
        args += "/rfx"

        // Security
        val sec = when {
            conn.useNla -> "nla"
            conn.useTls -> "tls"
            else -> "rdp"
        }
        args += "/sec:$sec"

        // Performance flags
        if (conn.enableWallpaper) args += "+wallpaper" else args += "-wallpaper"
        if (conn.enableFontSmoothing) args += "+fonts" else args += "-fonts"
        if (conn.enableFullWindowDrag) args += "+window-drag" else args += "-window-drag"
        args += "-aero"
        args += "-menu-anims"
        args += "-themes"

        // Keyboard
        args += "/kbd:unicode:on"

        // Clipboard
        args += "/clipboard"

        // Certificate handling — auto-accept for now
        args += "/cert:ignore"

        // Gateway
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

    /**
     * Blit pixels from native GDI buffer into Android Bitmap for the given dirty region.
     * Called internally after [onNativeGraphicsUpdate].
     */
    fun updateGraphics(x: Int, y: Int, width: Int, height: Int) {
        val bitmap = sessionBitmap ?: return
        if (nativeInstance == 0L) return

        nativeUpdateGraphics(nativeInstance, bitmap, x, y, width, height)

        // Notify Compose to recompose with updated bitmap
        _framebuffer.value = bitmap
    }

    /**
     * Send mouse/touch event to the remote session.
     */
    fun sendMouseEvent(x: Int, y: Int, flags: Int) {
        if (nativeInstance == 0L || _sessionState.value != SessionState.CONNECTED) return
        nativeSendCursorEvent(nativeInstance, x, y, flags)
    }

    /**
     * Send keyboard scancode event to the remote session.
     */
    fun sendKeyEvent(keyCode: Int, down: Boolean) {
        if (nativeInstance == 0L || _sessionState.value != SessionState.CONNECTED) return
        nativeSendKeyEvent(nativeInstance, keyCode, down)
    }

    /**
     * Send Unicode character input.
     */
    fun sendUnicodeKey(code: Int, down: Boolean) {
        if (nativeInstance == 0L || _sessionState.value != SessionState.CONNECTED) return
        nativeSendUnicodeKeyEvent(nativeInstance, code, down)
    }

    /**
     * Send clipboard text to the remote session.
     */
    fun sendClipboardData(text: String) {
        if (nativeInstance == 0L || _sessionState.value != SessionState.CONNECTED) return
        nativeSendClipboardData(nativeInstance, text)
    }

    /**
     * Disconnect from the current session.
     */
    fun disconnect() {
        if (nativeInstance == 0L) return
        _sessionState.value = SessionState.DISCONNECTING
        nativeDisconnect(nativeInstance)
    }

    /**
     * Release native resources. Call when done with this bridge instance.
     */
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
        instance: Long, bitmap: Bitmap, x: Int, y: Int, width: Int, height: Int
    ): Boolean
    private external fun nativeSendCursorEvent(instance: Long, x: Int, y: Int, flags: Int): Boolean
    private external fun nativeSendKeyEvent(instance: Long, keyCode: Int, down: Boolean): Boolean
    private external fun nativeSendUnicodeKeyEvent(instance: Long, code: Int, down: Boolean): Boolean
    private external fun nativeSendClipboardData(instance: Long, data: String): Boolean
    private external fun nativeGetVersion(): String
    private external fun nativeGetBuildConfig(): String
    private external fun nativeHasH264(): Boolean
    private external fun nativeGetLastError(instance: Long): String

    // ================================================================
    // Static callbacks — called from native C code via JNI
    // These are static because native code looks up methods on the class,
    // and uses the instance handle (Long) to find the right bridge object.
    // ================================================================

    companion object {
        private const val TAG = "FreeRdpBridge"

        init {
            System.loadLibrary("modernrdp-native")
        }

        // Map native instance handles to FreeRdpBridge objects
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

        // --- JNI callbacks (called from native code) ---

        @JvmStatic
        @Suppress("unused") // Called from JNI
        fun onNativePreConnect(instance: Long) {
            Log.d(TAG, "onNativePreConnect: $instance")
        }

        @JvmStatic
        @Suppress("unused") // Called from JNI
        fun onNativeConnected(instance: Long) {
            Log.i(TAG, "onNativeConnected: $instance")
            getBridge(instance)?._sessionState?.value = SessionState.CONNECTED
        }

        @JvmStatic
        @Suppress("unused") // Called from JNI
        fun onNativeConnectionFailed(instance: Long) {
            Log.e(TAG, "onNativeConnectionFailed: $instance")
            val bridge = getBridge(instance)
            bridge?._sessionState?.value = SessionState.ERROR
            bridge?._errorMessage?.value = "Connection failed"
        }

        @JvmStatic
        @Suppress("unused") // Called from JNI
        fun onNativeDisconnecting(instance: Long) {
            Log.d(TAG, "onNativeDisconnecting: $instance")
            getBridge(instance)?._sessionState?.value = SessionState.DISCONNECTING
        }

        @JvmStatic
        @Suppress("unused") // Called from JNI
        fun onNativeDisconnected(instance: Long) {
            Log.i(TAG, "onNativeDisconnected: $instance")
            getBridge(instance)?._sessionState?.value = SessionState.DISCONNECTED
        }

        /**
         * Called when the remote desktop negotiates final resolution.
         * We create the Android Bitmap that will receive frame data.
         */
        @JvmStatic
        @Suppress("unused") // Called from JNI
        fun onNativeSettingsChanged(instance: Long, width: Int, height: Int, bpp: Int) {
            Log.i(TAG, "onNativeSettingsChanged: ${width}x${height} @${bpp}bpp")
            val bridge = getBridge(instance) ?: return

            // Recycle old bitmap if size changed
            if (bridge.sessionWidth != width || bridge.sessionHeight != height) {
                bridge.sessionBitmap?.recycle()
                bridge.sessionBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bridge.sessionWidth = width
                bridge.sessionHeight = height
                Log.i(TAG, "Created session bitmap: ${width}x${height}")
            }
        }

        /**
         * Called when a region of the remote desktop has been updated.
         * We blit the updated pixels into our Bitmap and notify Compose.
         */
        @JvmStatic
        @Suppress("unused") // Called from JNI
        fun onNativeGraphicsUpdate(instance: Long, x: Int, y: Int, width: Int, height: Int) {
            getBridge(instance)?.updateGraphics(x, y, width, height)
        }

        /**
         * Called when the remote desktop is resized (e.g., server-initiated).
         * Reallocates the Bitmap at the new size.
         */
        @JvmStatic
        @Suppress("unused") // Called from JNI
        fun onNativeGraphicsResize(instance: Long, width: Int, height: Int, bpp: Int) {
            Log.i(TAG, "onNativeGraphicsResize: ${width}x${height} @${bpp}bpp")
            onNativeSettingsChanged(instance, width, height, bpp)
        }

        /**
         * Called when remote clipboard content changes.
         */
        @JvmStatic
        @Suppress("unused") // Called from JNI
        fun onNativeRemoteClipboardChanged(instance: Long, data: String) {
            Log.d(TAG, "Remote clipboard: ${data.take(50)}...")
            // TODO: Copy to local clipboard via ClipboardManager
        }
    }
}

enum class SessionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR,
}
