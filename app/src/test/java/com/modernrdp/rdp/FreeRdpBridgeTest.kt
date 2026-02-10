package com.modernrdp.rdp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FreeRdpBridgeTest {

    private lateinit var bridge: FreeRdpBridge

    @Before
    fun setup() {
        bridge = FreeRdpBridge()
    }

    @Test
    fun `initial session state is DISCONNECTED`() {
        assertEquals(SessionState.DISCONNECTED, bridge.sessionState.value)
    }

    @Test
    fun `initial framebuffer is null`() {
        assertNull(bridge.framebuffer.value)
    }

    @Test
    fun `initial error message is null`() {
        assertNull(bridge.errorMessage.value)
    }

    @Test
    fun `initial remote clipboard is null`() {
        assertNull(bridge.remoteClipboard.value)
    }

    @Test
    fun `initial pending certificate is null`() {
        assertNull(bridge.pendingCertificate.value)
    }

    @Test
    fun `certVerificationEnabled defaults to false`() {
        assertFalse(bridge.certVerificationEnabled)
    }

    @Test
    fun `respondToCertificate without pending cert does not crash`() {
        // Should be safe to call even when no cert is pending
        bridge.respondToCertificate(true)
        bridge.respondToCertificate(false)
    }

    @Test
    fun `disconnect on uninitialized bridge does not crash`() {
        // Should be safe to call when nativeInstance is 0
        bridge.disconnect()
    }

    @Test
    fun `release on uninitialized bridge does not crash`() {
        bridge.release()
        assertNull(bridge.framebuffer.value)
    }

    @Test
    fun `sendMouseEvent on uninitialized bridge does not crash`() {
        bridge.sendMouseEvent(0, 0, FreeRdpBridge.MOUSE_FLAG_MOVE)
    }

    @Test
    fun `sendKeyEvent on uninitialized bridge does not crash`() {
        bridge.sendKeyEvent(42, true)
    }

    @Test
    fun `sendUnicodeKey on uninitialized bridge does not crash`() {
        bridge.sendUnicodeKey(65, true)
    }

    @Test
    fun `sendClipboardData on uninitialized bridge does not crash`() {
        bridge.sendClipboardData("test")
    }

    @Test
    fun `requestResize on uninitialized bridge does not crash`() {
        bridge.requestResize(1920, 1080)
    }

    @Test
    fun `mouse flag constants have correct values`() {
        assertEquals(0x0800, FreeRdpBridge.MOUSE_FLAG_MOVE)
        assertEquals(0x1000, FreeRdpBridge.MOUSE_FLAG_BUTTON1)
        assertEquals(0x2000, FreeRdpBridge.MOUSE_FLAG_BUTTON2)
        assertEquals(0x4000, FreeRdpBridge.MOUSE_FLAG_BUTTON3)
        assertEquals(0x8000, FreeRdpBridge.MOUSE_FLAG_DOWN)
        assertEquals(0x0200, FreeRdpBridge.MOUSE_FLAG_WHEEL)
        assertEquals(0x0100, FreeRdpBridge.MOUSE_FLAG_WHEEL_NEGATIVE)
    }

    @Test
    fun `CertificateInfo data class holds values`() {
        val cert = CertificateInfo(
            host = "server.com",
            subject = "CN=server.com",
            issuer = "Let's Encrypt",
            fingerprint = "AA:BB:CC:DD",
            hostMismatch = true,
        )

        assertEquals("server.com", cert.host)
        assertEquals("CN=server.com", cert.subject)
        assertEquals("Let's Encrypt", cert.issuer)
        assertEquals("AA:BB:CC:DD", cert.fingerprint)
        assertEquals(true, cert.hostMismatch)
    }

    @Test
    fun `SessionState enum has all expected values`() {
        val states = SessionState.entries
        assertEquals(5, states.size)
        assertEquals(SessionState.DISCONNECTED, states[0])
        assertEquals(SessionState.CONNECTING, states[1])
        assertEquals(SessionState.CONNECTED, states[2])
        assertEquals(SessionState.DISCONNECTING, states[3])
        assertEquals(SessionState.ERROR, states[4])
    }
}
