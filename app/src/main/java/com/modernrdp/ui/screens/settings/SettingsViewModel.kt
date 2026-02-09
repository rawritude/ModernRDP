package com.modernrdp.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.modernrdp.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val themeMode: String = "system",
    val defaultResolutionMode: String = "match_device",
    val certVerification: Boolean = false,
    val confirmDisconnect: Boolean = true,
    val requireBiometric: Boolean = false,
    val defaultPort: Int = 3389,
    val clipboardSync: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
) : ViewModel() {

    val state: StateFlow<SettingsState> = combine(
        prefs.themeMode,
        prefs.defaultResolutionMode,
        prefs.certVerification,
        prefs.confirmDisconnect,
        prefs.requireBiometric,
        prefs.defaultPort,
        prefs.clipboardSync,
    ) { values ->
        SettingsState(
            themeMode = values[0] as String,
            defaultResolutionMode = values[1] as String,
            certVerification = values[2] as Boolean,
            confirmDisconnect = values[3] as Boolean,
            requireBiometric = values[4] as Boolean,
            defaultPort = values[5] as Int,
            clipboardSync = values[6] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setThemeMode(mode: String) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setDefaultResolutionMode(mode: String) = viewModelScope.launch { prefs.setDefaultResolutionMode(mode) }
    fun setCertVerification(enabled: Boolean) = viewModelScope.launch { prefs.setCertVerification(enabled) }
    fun setConfirmDisconnect(enabled: Boolean) = viewModelScope.launch { prefs.setConfirmDisconnect(enabled) }
    fun setRequireBiometric(enabled: Boolean) = viewModelScope.launch { prefs.setRequireBiometric(enabled) }
    fun setDefaultPort(port: Int) = viewModelScope.launch { prefs.setDefaultPort(port) }
    fun setClipboardSync(enabled: Boolean) = viewModelScope.launch { prefs.setClipboardSync(enabled) }
}
