package com.modernrdp.rdp

import android.graphics.Bitmap
import com.modernrdp.data.model.RdpConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridge to FreeRDP native library (libfreerdp-android.so).
 *
 * This class wraps the JNI calls to the FreeRDP C library. The native library
 * will be provided by the freeRDPCore module (from the FreeRDP project, Apache 2.0).
 *
 * For now this is a stub that defines the full API surface. The native implementations
 * will be connected once we integrate the FreeRDP native build.
 */
class FreeRdpBridge {

    private val _sessionState = MutableStateFlow(SessionState.DISCONNECTED)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _framebuffer = MutableStateFlow<Bitmap?>(null)
    val framebuffer: StateFlow<Bitmap?> = _framebuffer.asStateFlow()

    private var nativeInstance: Long = 0

    /**
     * Initialize FreeRDP context. Call once before connecting.
     */
    fun initialize(): Boolean {
        // TODO: nativeInstance = nativeInit()
        return true
    }

    /**
     * Connect to an RDP server using the given connection parameters.
     */
    fun connect(connection: RdpConnection, screenWidth: Int, screenHeight: Int): Boolean {
        _sessionState.value = SessionState.CONNECTING

        val width: Int
        val height: Int
        when (connection.resolutionMode) {
            com.modernrdp.data.model.ResolutionMode.MATCH_DEVICE -> {
                width = screenWidth
                height = screenHeight
            }
            com.modernrdp.data.model.ResolutionMode.CUSTOM -> {
                width = connection.customWidth
                height = connection.customHeight
            }
            com.modernrdp.data.model.ResolutionMode.FIT_SCREEN -> {
                width = screenWidth
                height = screenHeight
            }
        }

        // TODO: Call native connect with parameters:
        // nativeConnect(nativeInstance, connection.hostname, connection.port,
        //     connection.username, connection.password, connection.domain,
        //     width, height, connection.colorDepth,
        //     connection.useTls, connection.useNla,
        //     connection.enableWallpaper, connection.enableFontSmoothing,
        //     connection.enableFullWindowDrag)

        // Placeholder - will be replaced by native callback
        _sessionState.value = SessionState.CONNECTED
        return true
    }

    /**
     * Resize the remote desktop session. Critical for foldable devices —
     * called when the device folds/unfolds and the screen dimensions change.
     */
    fun resize(width: Int, height: Int) {
        if (_sessionState.value != SessionState.CONNECTED) return
        // TODO: nativeResize(nativeInstance, width, height)
    }

    /**
     * Send mouse/touch event to the remote session.
     */
    fun sendMouseEvent(x: Int, y: Int, flags: Int) {
        if (_sessionState.value != SessionState.CONNECTED) return
        // TODO: nativeSendMouseEvent(nativeInstance, x, y, flags)
    }

    /**
     * Send keyboard event to the remote session.
     */
    fun sendKeyEvent(keyCode: Int, down: Boolean) {
        if (_sessionState.value != SessionState.CONNECTED) return
        // TODO: nativeSendKeyEvent(nativeInstance, keyCode, down)
    }

    /**
     * Send Unicode character input.
     */
    fun sendUnicodeKey(character: Char) {
        if (_sessionState.value != SessionState.CONNECTED) return
        // TODO: nativeSendUnicodeKey(nativeInstance, character.code)
    }

    /**
     * Disconnect from the current session.
     */
    fun disconnect() {
        _sessionState.value = SessionState.DISCONNECTING
        // TODO: nativeDisconnect(nativeInstance)
        _sessionState.value = SessionState.DISCONNECTED
    }

    /**
     * Release native resources. Call when done with this bridge instance.
     */
    fun release() {
        if (nativeInstance != 0L) {
            // TODO: nativeRelease(nativeInstance)
            nativeInstance = 0
        }
    }

    // --- Native callbacks (called from C/JNI) ---

    /** Called by native code when connection succeeds. */
    @Suppress("unused") // Called from JNI
    private fun onConnected() {
        _sessionState.value = SessionState.CONNECTED
    }

    /** Called by native code when connection fails. */
    @Suppress("unused") // Called from JNI
    private fun onConnectionFailed(errorCode: Int, errorMessage: String) {
        _sessionState.value = SessionState.ERROR
    }

    /** Called by native code when disconnected. */
    @Suppress("unused") // Called from JNI
    private fun onDisconnected() {
        _sessionState.value = SessionState.DISCONNECTED
    }

    /** Called by native code when a frame is ready to display. */
    @Suppress("unused") // Called from JNI
    private fun onFrameUpdated(bitmap: Bitmap) {
        _framebuffer.value = bitmap
    }

    // --- Native method declarations (to be linked to libfreerdp) ---
    // private external fun nativeInit(): Long
    // private external fun nativeConnect(instance: Long, hostname: String, port: Int, ...): Boolean
    // private external fun nativeResize(instance: Long, width: Int, height: Int)
    // private external fun nativeSendMouseEvent(instance: Long, x: Int, y: Int, flags: Int)
    // private external fun nativeSendKeyEvent(instance: Long, keyCode: Int, down: Boolean)
    // private external fun nativeSendUnicodeKey(instance: Long, code: Int)
    // private external fun nativeDisconnect(instance: Long)
    // private external fun nativeRelease(instance: Long)

    companion object {
        init {
            // TODO: Uncomment when native library is available
            // System.loadLibrary("freerdp-android")
        }

        // Mouse event flags (matching FreeRDP PTR_FLAGS)
        const val MOUSE_FLAG_MOVE = 0x0800
        const val MOUSE_FLAG_BUTTON1 = 0x1000
        const val MOUSE_FLAG_BUTTON2 = 0x2000
        const val MOUSE_FLAG_BUTTON3 = 0x4000
        const val MOUSE_FLAG_DOWN = 0x8000
        const val MOUSE_FLAG_WHEEL = 0x0200
        const val MOUSE_FLAG_WHEEL_NEGATIVE = 0x0100
    }
}

enum class SessionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR,
}
