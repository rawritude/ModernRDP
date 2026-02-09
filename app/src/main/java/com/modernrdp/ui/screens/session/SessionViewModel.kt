package com.modernrdp.ui.screens.session

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    private val connectionId: Long = savedStateHandle["connectionId"] ?: -1L

    private val _connection = MutableStateFlow<RdpConnection?>(null)
    val connection: StateFlow<RdpConnection?> = _connection.asStateFlow()

    val rdpBridge = FreeRdpBridge()

    val sessionState: StateFlow<SessionState> = rdpBridge.sessionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionState.DISCONNECTED)

    val framebuffer: StateFlow<Bitmap?> = rdpBridge.framebuffer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val errorMessage: StateFlow<String?> = rdpBridge.errorMessage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val pendingCertificate: StateFlow<CertificateInfo?> = rdpBridge.pendingCertificate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _toolbarVisible = MutableStateFlow(true)
    val toolbarVisible: StateFlow<Boolean> = _toolbarVisible.asStateFlow()

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
                    }
                }
            }
        }

        // Track session lifecycle for multi-session indicator
        viewModelScope.launch {
            rdpBridge.sessionState.collect { state ->
                val hostname = _connection.value?.hostname ?: ""
                when (state) {
                    SessionState.CONNECTED -> sessionTracker.registerSession(connectionId, hostname)
                    SessionState.DISCONNECTED, SessionState.ERROR -> sessionTracker.unregisterSession(connectionId)
                    else -> {}
                }
            }
        }
    }

    fun connect(screenWidth: Int, screenHeight: Int) {
        val conn = _connection.value ?: return
        viewModelScope.launch {
            rdpBridge.certVerificationEnabled = prefs.certVerification.first()
            rdpBridge.initialize(application)
            rdpBridge.connect(conn, screenWidth, screenHeight)
            repository.markConnected(connectionId)
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
        val clip = clipboardManager.primaryClip ?: return
        if (clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: return
            rdpBridge.sendClipboardData(text)
        }
    }

    fun respondToCertificate(accept: Boolean) {
        rdpBridge.respondToCertificate(accept)
    }

    /** Handle fold/unfold — request resize via display control channel. */
    fun onScreenResize(newWidth: Int, newHeight: Int) {
        rdpBridge.requestResize(newWidth, newHeight)
    }

    fun toggleToolbar() {
        _toolbarVisible.value = !_toolbarVisible.value
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
