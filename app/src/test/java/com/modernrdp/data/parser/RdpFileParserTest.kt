package com.modernrdp.data.parser

import com.modernrdp.data.model.ResolutionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpFileParserTest {

    @Test
    fun `parse standard rdp file with all fields`() {
        val content = """
            full address:s:192.168.1.100
            server port:i:3390
            username:s:admin
            domain:s:CORP
            desktopwidth:i:1920
            desktopheight:i:1080
            session bpp:i:32
            disable wallpaper:i:0
            allow font smoothing:i:1
            gatewayhostname:s:gw.example.com
        """.trimIndent()

        val conn = RdpFileParser.parse(content)

        assertEquals("192.168.1.100", conn.hostname)
        assertEquals(3390, conn.port)
        assertEquals("admin", conn.username)
        assertEquals("CORP", conn.domain)
        assertEquals(1920, conn.customWidth)
        assertEquals(1080, conn.customHeight)
        assertEquals(32, conn.colorDepth)
        assertTrue(conn.enableWallpaper)
        assertTrue(conn.enableFontSmoothing)
        assertEquals("gw.example.com", conn.gatewayHostname)
        assertEquals(ResolutionMode.CUSTOM, conn.resolutionMode)
    }

    @Test
    fun `parse minimal rdp file with only hostname`() {
        val content = "full address:s:myserver.com"

        val conn = RdpFileParser.parse(content)

        assertEquals("myserver.com", conn.hostname)
        assertEquals(3389, conn.port)
        assertEquals("", conn.username)
        assertEquals("", conn.domain)
    }

    @Test
    fun `parse hostname with embedded port`() {
        val content = "full address:s:myserver.com:3391"

        val conn = RdpFileParser.parse(content)

        assertEquals("myserver.com", conn.hostname)
        assertEquals(3391, conn.port)
    }

    @Test
    fun `parse with smart sizing uses FIT_SCREEN mode`() {
        val content = """
            full address:s:10.0.0.1
            smart sizing:i:1
        """.trimIndent()

        val conn = RdpFileParser.parse(content)

        assertEquals(ResolutionMode.FIT_SCREEN, conn.resolutionMode)
    }

    @Test
    fun `parse with no resolution defaults to MATCH_DEVICE`() {
        val content = "full address:s:10.0.0.1"

        val conn = RdpFileParser.parse(content)

        assertEquals(ResolutionMode.MATCH_DEVICE, conn.resolutionMode)
    }

    @Test
    fun `parse ignores comments and blank lines`() {
        val content = """
            # This is a comment
            // Another comment

            full address:s:server.local
            username:s:user1
        """.trimIndent()

        val conn = RdpFileParser.parse(content)

        assertEquals("server.local", conn.hostname)
        assertEquals("user1", conn.username)
    }

    @Test
    fun `parse with NLA disabled`() {
        val content = """
            full address:s:10.0.0.1
            enablecredsspsupport:i:0
        """.trimIndent()

        val conn = RdpFileParser.parse(content)

        assertEquals(false, conn.useNla)
    }

    @Test
    fun `parse with wallpaper disabled`() {
        val content = """
            full address:s:10.0.0.1
            disable wallpaper:i:1
        """.trimIndent()

        val conn = RdpFileParser.parse(content)

        assertEquals(false, conn.enableWallpaper)
    }

    @Test
    fun `parse uses hostname as name when no app name`() {
        val content = "full address:s:mypc.local"

        val conn = RdpFileParser.parse(content)

        assertEquals("mypc.local", conn.name)
    }

    @Test
    fun `parse uses remoteapplicationname as name when present`() {
        val content = """
            full address:s:10.0.0.1
            remoteapplicationname:s:Production Server
        """.trimIndent()

        val conn = RdpFileParser.parse(content)

        assertEquals("Production Server", conn.name)
    }

    @Test
    fun `parse empty content returns defaults`() {
        val conn = RdpFileParser.parse("")

        assertEquals("", conn.hostname)
        assertEquals(3389, conn.port)
        assertEquals(ResolutionMode.MATCH_DEVICE, conn.resolutionMode)
    }

    @Test
    fun `parse from InputStream`() {
        val content = "full address:s:stream-server.com"
        val stream = content.byteInputStream()

        val conn = RdpFileParser.parse(stream)

        assertEquals("stream-server.com", conn.hostname)
    }

    @Test
    fun `parse with color depth 16bpp`() {
        val content = """
            full address:s:10.0.0.1
            session bpp:i:16
        """.trimIndent()

        val conn = RdpFileParser.parse(content)

        assertEquals(16, conn.colorDepth)
    }
}
