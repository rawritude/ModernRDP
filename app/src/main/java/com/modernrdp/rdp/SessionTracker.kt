package com.modernrdp.rdp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks active RDP sessions across the app. Allows the HomeScreen
 * to show active session indicators and enable session switching.
 */
@Singleton
class SessionTracker @Inject constructor() {

    data class ActiveSession(
        val connectionId: Long,
        val hostname: String,
    )

    private val _activeSessions = MutableStateFlow<Map<Long, ActiveSession>>(emptyMap())
    val activeSessions: StateFlow<Map<Long, ActiveSession>> = _activeSessions.asStateFlow()

    val hasActiveSessions: Boolean
        get() = _activeSessions.value.isNotEmpty()

    fun registerSession(connectionId: Long, hostname: String) {
        _activeSessions.value = _activeSessions.value.toMutableMap().apply {
            put(connectionId, ActiveSession(connectionId, hostname))
        }
    }

    fun unregisterSession(connectionId: Long) {
        _activeSessions.value = _activeSessions.value.toMutableMap().apply {
            remove(connectionId)
        }
    }
}
