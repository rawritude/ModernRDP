package com.modernrdp.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Sends a Wake-on-LAN magic packet to wake up a remote computer.
 *
 * The magic packet consists of 6 bytes of 0xFF followed by 16 repetitions
 * of the target's MAC address, sent as a UDP broadcast.
 */
object WakeOnLan {

    /**
     * Parse a MAC address string (colon, dash, or no separator) to bytes.
     */
    fun parseMac(mac: String): ByteArray? {
        val clean = mac.replace("[:-]".toRegex(), "").trim()
        if (clean.length != 12) return null
        return try {
            ByteArray(6) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } catch (_: NumberFormatException) {
            null
        }
    }

    /**
     * Validate a MAC address string.
     */
    fun isValidMac(mac: String): Boolean = parseMac(mac) != null

    /**
     * Send a Wake-on-LAN magic packet.
     *
     * @param macAddress MAC address (e.g., "AA:BB:CC:DD:EE:FF")
     * @param broadcastAddress Broadcast address (default: 255.255.255.255)
     * @param port WoL port (default: 9)
     * @return true if sent successfully
     */
    suspend fun send(
        macAddress: String,
        broadcastAddress: String = "255.255.255.255",
        port: Int = 9,
    ): Boolean = withContext(Dispatchers.IO) {
        val macBytes = parseMac(macAddress) ?: return@withContext false

        try {
            // Build magic packet: 6x 0xFF + 16x MAC address
            val packet = ByteArray(6 + 16 * 6)
            for (i in 0 until 6) packet[i] = 0xFF.toByte()
            for (i in 0 until 16) {
                System.arraycopy(macBytes, 0, packet, 6 + i * 6, 6)
            }

            val address = InetAddress.getByName(broadcastAddress)
            val datagram = DatagramPacket(packet, packet.size, address, port)

            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.send(datagram)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
