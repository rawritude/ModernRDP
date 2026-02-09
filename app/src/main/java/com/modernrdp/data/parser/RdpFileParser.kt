package com.modernrdp.data.parser

import com.modernrdp.data.model.RdpConnection
import com.modernrdp.data.model.ResolutionMode
import java.io.InputStream

/**
 * Parses Microsoft .rdp files into RdpConnection objects.
 *
 * .rdp format is `key:type:value` per line.
 * Types: s = string, i = integer, b = binary (base64).
 */
object RdpFileParser {

    fun parse(input: InputStream): RdpConnection {
        val properties = mutableMapOf<String, String>()
        input.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("//")) return@forEach
                val parts = trimmed.split(":", limit = 3)
                if (parts.size >= 3) {
                    properties[parts[0].lowercase()] = parts[2]
                } else if (parts.size == 2) {
                    // Some .rdp files use key:value without type
                    properties[parts[0].lowercase()] = parts[1]
                }
            }
        }
        return buildConnection(properties)
    }

    fun parse(content: String): RdpConnection = parse(content.byteInputStream())

    private fun buildConnection(props: Map<String, String>): RdpConnection {
        val hostname = props["full address"] ?: props["hostname"] ?: ""
        val hostParts = hostname.split(":", limit = 2)
        val host = hostParts[0]
        val portFromHost = hostParts.getOrNull(1)?.toIntOrNull()

        return RdpConnection(
            name = props["remoteapplicationname"] ?: host,
            hostname = host,
            port = portFromHost ?: props["server port"]?.toIntOrNull() ?: 3389,
            username = props["username"] ?: "",
            domain = props["domain"] ?: "",
            resolutionMode = when {
                props["smart sizing"]?.toIntOrNull() == 1 -> ResolutionMode.FIT_SCREEN
                props["desktopwidth"] != null -> ResolutionMode.CUSTOM
                else -> ResolutionMode.MATCH_DEVICE
            },
            customWidth = props["desktopwidth"]?.toIntOrNull() ?: 1920,
            customHeight = props["desktopheight"]?.toIntOrNull() ?: 1080,
            colorDepth = props["session bpp"]?.toIntOrNull() ?: 32,
            enableWallpaper = props["disable wallpaper"]?.toIntOrNull() != 1,
            enableFontSmoothing = props["allow font smoothing"]?.toIntOrNull() == 1,
            enableFullWindowDrag = props["disable full window drag"]?.toIntOrNull() != 1,
            useTls = true,
            useNla = props["enablecredsspsupport"]?.toIntOrNull() != 0,
            gatewayHostname = props["gatewayhostname"] ?: "",
            gatewayPort = props["gatewaybrokeringtype"]?.toIntOrNull() ?: 443,
            gatewayUsername = props["gatewayusername"] ?: "",
        )
    }
}
