package com.modernrdp.rdp

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionTrackerTest {

    private lateinit var tracker: SessionTracker

    @Before
    fun setup() {
        tracker = SessionTracker()
    }

    @Test
    fun `initially has no active sessions`() {
        assertFalse(tracker.hasActiveSessions)
        assertTrue(tracker.activeSessions.value.isEmpty())
    }

    @Test
    fun `registerSession adds session`() {
        tracker.registerSession(1L, "server1.local")

        assertTrue(tracker.hasActiveSessions)
        assertEquals(1, tracker.activeSessions.value.size)
        assertEquals("server1.local", tracker.activeSessions.value[1L]?.hostname)
    }

    @Test
    fun `unregisterSession removes session`() {
        tracker.registerSession(1L, "server1.local")
        tracker.unregisterSession(1L)

        assertFalse(tracker.hasActiveSessions)
        assertTrue(tracker.activeSessions.value.isEmpty())
    }

    @Test
    fun `multiple sessions tracked independently`() {
        tracker.registerSession(1L, "server1.local")
        tracker.registerSession(2L, "server2.local")
        tracker.registerSession(3L, "server3.local")

        assertEquals(3, tracker.activeSessions.value.size)
        assertEquals("server1.local", tracker.activeSessions.value[1L]?.hostname)
        assertEquals("server2.local", tracker.activeSessions.value[2L]?.hostname)
        assertEquals("server3.local", tracker.activeSessions.value[3L]?.hostname)
    }

    @Test
    fun `unregister one session leaves others intact`() {
        tracker.registerSession(1L, "server1.local")
        tracker.registerSession(2L, "server2.local")

        tracker.unregisterSession(1L)

        assertEquals(1, tracker.activeSessions.value.size)
        assertEquals("server2.local", tracker.activeSessions.value[2L]?.hostname)
    }

    @Test
    fun `unregister nonexistent session is safe`() {
        tracker.registerSession(1L, "server1.local")

        tracker.unregisterSession(999L) // doesn't exist

        assertEquals(1, tracker.activeSessions.value.size)
    }

    @Test
    fun `re-registering session updates hostname`() {
        tracker.registerSession(1L, "old-hostname")
        tracker.registerSession(1L, "new-hostname")

        assertEquals(1, tracker.activeSessions.value.size)
        assertEquals("new-hostname", tracker.activeSessions.value[1L]?.hostname)
    }

    @Test
    fun `activeSessions flow emits updates`() = runTest {
        tracker.activeSessions.test {
            assertEquals(emptyMap<Long, SessionTracker.ActiveSession>(), awaitItem())

            tracker.registerSession(1L, "server1")
            val withOne = awaitItem()
            assertEquals(1, withOne.size)

            tracker.registerSession(2L, "server2")
            val withTwo = awaitItem()
            assertEquals(2, withTwo.size)

            tracker.unregisterSession(1L)
            val withOneRemaining = awaitItem()
            assertEquals(1, withOneRemaining.size)
            assertTrue(withOneRemaining.containsKey(2L))

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `ActiveSession data class holds correct values`() {
        val session = SessionTracker.ActiveSession(42L, "test-host")

        assertEquals(42L, session.connectionId)
        assertEquals("test-host", session.hostname)
    }
}
