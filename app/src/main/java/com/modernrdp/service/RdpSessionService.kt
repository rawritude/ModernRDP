package com.modernrdp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.modernrdp.MainActivity
import com.modernrdp.R
import com.modernrdp.rdp.FreeRdpBridge
import com.modernrdp.rdp.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps RDP sessions alive when the app is in the background.
 *
 * This prevents Android from killing the process and dropping the connection
 * when the user switches to another app or the screen turns off.
 *
 * Manages multiple concurrent sessions keyed by connection ID.
 */
class RdpSessionService : Service() {

    companion object {
        private const val CHANNEL_ID = "rdp_session"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.modernrdp.STOP_SESSION"
        const val EXTRA_CONNECTION_ID = "connection_id"
    }

    inner class SessionBinder : Binder() {
        val service: RdpSessionService get() = this@RdpSessionService
    }

    private val binder = SessionBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Active sessions keyed by connection ID
    private val _activeSessions = MutableStateFlow<Map<Long, SessionInfo>>(emptyMap())
    val activeSessions: StateFlow<Map<Long, SessionInfo>> = _activeSessions.asStateFlow()

    data class SessionInfo(
        val connectionId: Long,
        val hostname: String,
        val bridge: FreeRdpBridge,
        val state: SessionState,
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                val connId = intent.getLongExtra(EXTRA_CONNECTION_ID, -1L)
                if (connId != -1L) {
                    removeSession(connId)
                } else {
                    stopAllSessions()
                }
            }
        }
        return START_STICKY
    }

    /**
     * Register a session so it persists in the background.
     */
    fun registerSession(connectionId: Long, hostname: String, bridge: FreeRdpBridge) {
        val sessions = _activeSessions.value.toMutableMap()
        sessions[connectionId] = SessionInfo(
            connectionId = connectionId,
            hostname = hostname,
            bridge = bridge,
            state = bridge.sessionState.value,
        )
        _activeSessions.value = sessions

        // Start foreground with notification
        startForeground(NOTIFICATION_ID, buildNotification())

        // Observe session state changes
        serviceScope.launch {
            bridge.sessionState.collect { state ->
                updateSessionState(connectionId, state)
                if (state == SessionState.DISCONNECTED || state == SessionState.ERROR) {
                    removeSession(connectionId)
                }
            }
        }
    }

    /**
     * Unregister a session (e.g. user disconnected).
     */
    fun removeSession(connectionId: Long) {
        val sessions = _activeSessions.value.toMutableMap()
        sessions.remove(connectionId)
        _activeSessions.value = sessions

        if (sessions.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            // Update notification with remaining sessions
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    /**
     * Get an active session's bridge by connection ID.
     */
    fun getSession(connectionId: Long): FreeRdpBridge? {
        return _activeSessions.value[connectionId]?.bridge
    }

    private fun updateSessionState(connectionId: Long, state: SessionState) {
        val sessions = _activeSessions.value.toMutableMap()
        sessions[connectionId]?.let { info ->
            sessions[connectionId] = info.copy(state = state)
            _activeSessions.value = sessions
        }

        // Update notification text
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun stopAllSessions() {
        _activeSessions.value.values.forEach { session ->
            session.bridge.disconnect()
        }
        _activeSessions.value = emptyMap()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "RDP Sessions",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Active remote desktop connections"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val sessions = _activeSessions.value
        val contentText = when (sessions.size) {
            0 -> "No active sessions"
            1 -> "Connected to ${sessions.values.first().hostname}"
            else -> "${sessions.size} active sessions"
        }

        val returnIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingReturn = PendingIntent.getActivity(
            this, 0, returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, RdpSessionService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ModernRDP")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(pendingReturn)
            .addAction(0, "Disconnect All", pendingStop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
