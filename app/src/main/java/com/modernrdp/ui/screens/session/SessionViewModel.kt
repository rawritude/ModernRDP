package com.modernrdp.ui.screens.session

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.modernrdp.data.model.RdpConnection
import com.modernrdp.data.preferences.AppPreferences
import com.modernrdp.data.repository.ConnectionRepository
import com.modernrdp.rdp.CertificateInfo
import com.modernrdp.rdp.FreeRdpBridge
import com.modernrdp.rdp.SessionState
import com.modernrdp.rdp.SessionTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val application: Application,
    private val repository: ConnectionRepository,
    private val sessionTracker: SessionTracker,
    private val prefs: AppPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        private const val TAG = "SessionViewModel"
        private const val MAX_RETRIES = 3
        private val RETRY_DELAYS_MS = longArrayOf(2000, 4000, 8000)
    }

    private val connectionId: Long = savedStateHandle["connectionId"] ?: -1L

    private val _connection = MutableStateFlow<RdpConnection?>(null)
    val connection: StateFlow<RdpConnection?> = _connection.asStateFlow()

    val rdpBridge = FreeRdpBridge()

    val sessionState: StateFlow<SessionState> = rdpBridge.sessionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionState.DISCONNECTED)

    val framebuffer: StateFlow<Bitmap?> = rdpBridge.framebuffer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val frameVersion: StateFlow<Long> = rdpBridge.frameVersion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val errorMessage: StateFlow<String?> = rdpBridge.errorMessage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val pendingCertificate: StateFlow<CertificateInfo?> = rdpBridge.pendingCertificate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _toolbarVisible = MutableStateFlow(true)
    val toolbarVisible: StateFlow<Boolean> = _toolbarVisible.asStateFlow()

    /** One-shot UI events (snackbar messages, toasts). */
    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    private val _retryCount = MutableStateFlow(0)
    val retryCount: StateFlow<Int> = _retryCount.asStateFlow()

    private val _keyboardVisible = MutableStateFlow(false)
    val keyboardVisible: StateFlow<Boolean> = _keyboardVisible.asStateFlow()

    val confirmDisconnect: StateFlow<Boolean> = prefs.confirmDisconnect
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val clipboardManager =
        application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    init {
        viewModelScope.launch {
            _connection.value = repository.getConnectionById(connectionId)
        }

        // Sync remote clipboard -> local
        viewModelScope.launch {
            rdpBridge.remoteClipboard.collect { text ->
                if (text != null) {
                    val clipSync = prefs.clipboardSync.first()
                    if (clipSync) {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("RDP", text))
                        _uiEvent.emit(UiEvent.ShowSnackbar("Clipboard received from remote"))
                    }
                }
            }
        }

        // Track session lifecycle for multi-session indicator
        viewModelScope.launch {
            rdpBridge.sessionState.collect { state ->
                val hostname = _connection.value?.hostname ?: ""
                when (state) {
                    SessionState.CONNECTED -> {
                        sessionTracker.registerSession(connectionId, hostname)
                        _retryCount.value = 0
                    }
                    SessionState.DISCONNECTED, SessionState.ERROR -> {
                        sessionTracker.unregisterSession(connectionId)
                    }
                    else -> {}
                }
            }
        }
    }

    fun connect(screenWidth: Int, screenHeight: Int) {
        val conn = _connection.value ?: return
        viewModelScope.launch {
            _retryCount.value = 0
            connectWithRetry(conn, screenWidth, screenHeight)
        }
    }

    private suspend fun connectWithRetry(conn: RdpConnection, screenWidth: Int, screenHeight: Int) {
        rdpBridge.certVerificationEnabled = prefs.certVerification.first()

        if (!rdpBridge.initialize(application)) {
            Log.e(TAG, "Failed to initialize FreeRDP")
            _uiEvent.emit(UiEvent.ShowSnackbar("Failed to initialize RDP engine"))
            return
        }

        val success = rdpBridge.connect(conn, screenWidth, screenHeight)
        if (success) {
            repository.markConnected(connectionId)
        }
    }

    fun retry(screenWidth: Int, screenHeight: Int) {
        val conn = _connection.value ?: return
        val currentRetry = _retryCount.value

        if (currentRetry >= MAX_RETRIES) {
            viewModelScope.launch {
                _uiEvent.emit(UiEvent.ShowSnackbar("Max retries reached. Check your connection settings."))
            }
            return
        }

        viewModelScope.launch {
            _retryCount.value = currentRetry + 1
            val delayMs = RETRY_DELAYS_MS.getOrElse(currentRetry) { RETRY_DELAYS_MS.last() }
            Log.i(TAG, "Retry ${currentRetry + 1}/$MAX_RETRIES after ${delayMs}ms")
            _uiEvent.emit(UiEvent.ShowSnackbar("Retrying (${currentRetry + 1}/$MAX_RETRIES)..."))
            delay(delayMs)
            rdpBridge.release()
            connectWithRetry(conn, screenWidth, screenHeight)
        }
    }

    fun onTouchEvent(x: Int, y: Int, flags: Int) {
        rdpBridge.sendMouseEvent(x, y, flags)
    }

    fun onKeyEvent(keyCode: Int, down: Boolean) {
        rdpBridge.sendKeyEvent(keyCode, down)
    }

    fun onUnicodeKey(code: Int, down: Boolean) {
        rdpBridge.sendUnicodeKey(code, down)
    }

    /** Send each character of text input as unicode key events. */
    fun onTextInput(text: String) {
        for (char in text) {
            rdpBridge.sendUnicodeKey(char.code, true)
            rdpBridge.sendUnicodeKey(char.code, false)
        }
    }

    fun sendClipboard(text: String) {
        rdpBridge.sendClipboardData(text)
    }

    /** Send local clipboard to remote. */
    fun syncLocalClipboard() {
        val clip = clipboardManager.primaryClip ?: run {
            viewModelScope.launch { _uiEvent.emit(UiEvent.ShowSnackbar("Clipboard is empty")) }
            return
        }
        if (clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: run {
                viewModelScope.launch { _uiEvent.emit(UiEvent.ShowSnackbar("No text in clipboard")) }
                return
            }
            rdpBridge.sendClipboardData(text)
            viewModelScope.launch { _uiEvent.emit(UiEvent.ShowSnackbar("Clipboard sent to remote")) }
        }
    }

    fun respondToCertificate(accept: Boolean) {
        rdpBridge.respondToCertificate(accept)
    }

    /** Handle fold/unfold -- request resize via display control channel. */
    fun onScreenResize(newWidth: Int, newHeight: Int) {
        rdpBridge.requestResize(newWidth, newHeight)
    }

    fun toggleToolbar() {
        _toolbarVisible.value = !_toolbarVisible.value
    }

    fun toggleKeyboard() {
        _keyboardVisible.value = !_keyboardVisible.value
    }

    /** Send scroll wheel event (positive = up, negative = down). */
    fun onScrollWheel(deltaY: Float) {
        val flags = if (deltaY > 0) {
            FreeRdpBridge.MOUSE_FLAG_WHEEL or (120 and 0x00FF)
        } else {
            FreeRdpBridge.MOUSE_FLAG_WHEEL or FreeRdpBridge.MOUSE_FLAG_WHEEL_NEGATIVE or (120 and 0x00FF)
        }
        rdpBridge.sendMouseEvent(0, 0, flags)
    }

    fun disconnect() {
        rdpBridge.disconnect()
    }

    override fun onCleared() {
        rdpBridge.disconnect()
        rdpBridge.release()
        sessionTracker.unregisterSession(connectionId)
        super.onCleared()
    }
}

sealed interface UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent
}
