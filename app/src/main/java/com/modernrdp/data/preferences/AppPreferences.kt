package com.modernrdp.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class AppPreferences @Inject constructor(
    private val context: Context,
) {
    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_RESOLUTION_MODE = stringPreferencesKey("default_resolution_mode")
        val CERT_VERIFICATION = booleanPreferencesKey("cert_verification")
        val CONFIRM_DISCONNECT = booleanPreferencesKey("confirm_disconnect")
        val REQUIRE_BIOMETRIC = booleanPreferencesKey("require_biometric")
        val DEFAULT_PORT = intPreferencesKey("default_port")
        val CLIPBOARD_SYNC = booleanPreferencesKey("clipboard_sync")
    }

    val themeMode: Flow<String> = context.dataStore.data.map { it[THEME_MODE] ?: "system" }
    val defaultResolutionMode: Flow<String> = context.dataStore.data.map { it[DEFAULT_RESOLUTION_MODE] ?: "match_device" }
    val certVerification: Flow<Boolean> = context.dataStore.data.map { it[CERT_VERIFICATION] ?: false }
    val confirmDisconnect: Flow<Boolean> = context.dataStore.data.map { it[CONFIRM_DISCONNECT] ?: true }
    val requireBiometric: Flow<Boolean> = context.dataStore.data.map { it[REQUIRE_BIOMETRIC] ?: false }
    val defaultPort: Flow<Int> = context.dataStore.data.map { it[DEFAULT_PORT] ?: 3389 }
    val clipboardSync: Flow<Boolean> = context.dataStore.data.map { it[CLIPBOARD_SYNC] ?: true }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setDefaultResolutionMode(mode: String) {
        context.dataStore.edit { it[DEFAULT_RESOLUTION_MODE] = mode }
    }

    suspend fun setCertVerification(enabled: Boolean) {
        context.dataStore.edit { it[CERT_VERIFICATION] = enabled }
    }

    suspend fun setConfirmDisconnect(enabled: Boolean) {
        context.dataStore.edit { it[CONFIRM_DISCONNECT] = enabled }
    }

    suspend fun setRequireBiometric(enabled: Boolean) {
        context.dataStore.edit { it[REQUIRE_BIOMETRIC] = enabled }
    }

    suspend fun setDefaultPort(port: Int) {
        context.dataStore.edit { it[DEFAULT_PORT] = port }
    }

    suspend fun setClipboardSync(enabled: Boolean) {
        context.dataStore.edit { it[CLIPBOARD_SYNC] = enabled }
    }
}
