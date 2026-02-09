package com.modernrdp.ui.screens.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.modernrdp.data.model.ConnectionGroup
import com.modernrdp.data.model.RdpConnection
import com.modernrdp.data.model.ResolutionMode
import com.modernrdp.data.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: ConnectionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val connectionId: Long = savedStateHandle["connectionId"] ?: -1L
    val isEditing: Boolean = connectionId != -1L

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    val availableGroups: StateFlow<List<ConnectionGroup>> = repository.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (isEditing) {
            viewModelScope.launch {
                repository.getConnectionById(connectionId)?.let { conn ->
                    _state.value = EditorState(
                        name = conn.name,
                        hostname = conn.hostname,
                        port = conn.port.toString(),
                        username = conn.username,
                        password = conn.password,
                        domain = conn.domain,
                        groupId = conn.groupId,
                        resolutionMode = conn.resolutionMode,
                        customWidth = conn.customWidth.toString(),
                        customHeight = conn.customHeight.toString(),
                        colorDepth = conn.colorDepth,
                        useTls = conn.useTls,
                        useNla = conn.useNla,
                        enableWallpaper = conn.enableWallpaper,
                        enableFontSmoothing = conn.enableFontSmoothing,
                        gatewayHostname = conn.gatewayHostname,
                        gatewayPort = conn.gatewayPort.toString(),
                        gatewayUsername = conn.gatewayUsername,
                        gatewayPassword = conn.gatewayPassword,
                        macAddress = conn.macAddress,
                    )
                }
            }
        }
    }

    fun updateName(value: String) = _state.update { it.copy(name = value) }
    fun updateHostname(value: String) = _state.update { it.copy(hostname = value) }
    fun updatePort(value: String) = _state.update { it.copy(port = value) }
    fun updateUsername(value: String) = _state.update { it.copy(username = value) }
    fun updatePassword(value: String) = _state.update { it.copy(password = value) }
    fun updateDomain(value: String) = _state.update { it.copy(domain = value) }
    fun updateGroupId(value: Long?) = _state.update { it.copy(groupId = value) }
    fun updateResolutionMode(value: ResolutionMode) = _state.update { it.copy(resolutionMode = value) }
    fun updateCustomWidth(value: String) = _state.update { it.copy(customWidth = value) }
    fun updateCustomHeight(value: String) = _state.update { it.copy(customHeight = value) }
    fun updateColorDepth(value: Int) = _state.update { it.copy(colorDepth = value) }
    fun updateUseTls(value: Boolean) = _state.update { it.copy(useTls = value) }
    fun updateUseNla(value: Boolean) = _state.update { it.copy(useNla = value) }
    fun updateEnableWallpaper(value: Boolean) = _state.update { it.copy(enableWallpaper = value) }
    fun updateEnableFontSmoothing(value: Boolean) = _state.update { it.copy(enableFontSmoothing = value) }
    fun updateGatewayHostname(value: String) = _state.update { it.copy(gatewayHostname = value) }
    fun updateGatewayPort(value: String) = _state.update { it.copy(gatewayPort = value) }
    fun updateGatewayUsername(value: String) = _state.update { it.copy(gatewayUsername = value) }
    fun updateGatewayPassword(value: String) = _state.update { it.copy(gatewayPassword = value) }
    fun updateMacAddress(value: String) = _state.update { it.copy(macAddress = value) }

    fun save(onComplete: () -> Unit) {
        val s = _state.value
        if (s.hostname.isBlank()) {
            _state.update { it.copy(hostnameError = "Hostname is required") }
            return
        }

        viewModelScope.launch {
            val connection = RdpConnection(
                id = if (isEditing) connectionId else 0,
                name = s.name,
                hostname = s.hostname.trim(),
                port = s.port.toIntOrNull() ?: 3389,
                username = s.username,
                password = s.password,
                domain = s.domain,
                groupId = s.groupId,
                resolutionMode = s.resolutionMode,
                customWidth = s.customWidth.toIntOrNull() ?: 1920,
                customHeight = s.customHeight.toIntOrNull() ?: 1080,
                colorDepth = s.colorDepth,
                useTls = s.useTls,
                useNla = s.useNla,
                enableWallpaper = s.enableWallpaper,
                enableFontSmoothing = s.enableFontSmoothing,
                gatewayHostname = s.gatewayHostname,
                gatewayPort = s.gatewayPort.toIntOrNull() ?: 443,
                gatewayUsername = s.gatewayUsername,
                gatewayPassword = s.gatewayPassword,
                macAddress = s.macAddress.trim(),
            )
            repository.saveConnection(connection)
            onComplete()
        }
    }

    fun delete(onComplete: () -> Unit) {
        if (!isEditing) return
        viewModelScope.launch {
            repository.getConnectionById(connectionId)?.let {
                repository.deleteConnection(it)
            }
            onComplete()
        }
    }
}

data class EditorState(
    val name: String = "",
    val hostname: String = "",
    val hostnameError: String? = null,
    val port: String = "3389",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val groupId: Long? = null,
    val resolutionMode: ResolutionMode = ResolutionMode.MATCH_DEVICE,
    val customWidth: String = "1920",
    val customHeight: String = "1080",
    val colorDepth: Int = 32,
    val useTls: Boolean = true,
    val useNla: Boolean = true,
    val enableWallpaper: Boolean = false,
    val enableFontSmoothing: Boolean = true,
    val gatewayHostname: String = "",
    val gatewayPort: String = "443",
    val gatewayUsername: String = "",
    val gatewayPassword: String = "",
    val macAddress: String = "",
)
