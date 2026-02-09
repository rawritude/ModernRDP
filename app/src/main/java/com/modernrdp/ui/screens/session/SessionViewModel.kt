package com.modernrdp.ui.screens.session

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.modernrdp.data.model.RdpConnection
import com.modernrdp.data.repository.ConnectionRepository
import com.modernrdp.rdp.FreeRdpBridge
import com.modernrdp.rdp.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val application: Application,
    private val repository: ConnectionRepository,
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

    private val _toolbarVisible = MutableStateFlow(true)
    val toolbarVisible: StateFlow<Boolean> = _toolbarVisible.asStateFlow()

    init {
        viewModelScope.launch {
            _connection.value = repository.getConnectionById(connectionId)
        }
    }

    fun connect(screenWidth: Int, screenHeight: Int) {
        val conn = _connection.value ?: return
        viewModelScope.launch {
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

    fun sendClipboard(text: String) {
        rdpBridge.sendClipboardData(text)
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
        super.onCleared()
    }
}
