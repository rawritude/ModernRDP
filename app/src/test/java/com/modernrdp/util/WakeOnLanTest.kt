package com.modernrdp.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeOnLanTest {

    @Test
    fun `parseMac with colon-separated address`() {
        val result = WakeOnLan.parseMac("AA:BB:CC:DD:EE:FF")

        assertNotNull(result)
        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte()),
            result,
        )
    }

    @Test
    fun `parseMac with dash-separated address`() {
        val result = WakeOnLan.parseMac("AA-BB-CC-DD-EE-FF")

        assertNotNull(result)
        assertEquals(6, result!!.size)
    }

    @Test
    fun `parseMac with no separator`() {
        val result = WakeOnLan.parseMac("AABBCCDDEEFF")

        assertNotNull(result)
        assertEquals(6, result!!.size)
    }

    @Test
    fun `parseMac with lowercase`() {
        val result = WakeOnLan.parseMac("aa:bb:cc:dd:ee:ff")

        assertNotNull(result)
        assertEquals(6, result!!.size)
    }

    @Test
    fun `parseMac with mixed case`() {
        val result = WakeOnLan.parseMac("Aa:Bb:Cc:Dd:Ee:Ff")

        assertNotNull(result)
    }

    @Test
    fun `parseMac with extra whitespace`() {
        val result = WakeOnLan.parseMac("  AA:BB:CC:DD:EE:FF  ")

        assertNotNull(result)
    }

    @Test
    fun `parseMac with too short address returns null`() {
        val result = WakeOnLan.parseMac("AA:BB:CC")

        assertNull(result)
    }

    @Test
    fun `parseMac with too long address returns null`() {
        val result = WakeOnLan.parseMac("AA:BB:CC:DD:EE:FF:00")

        assertNull(result)
    }

    @Test
    fun `parseMac with empty string returns null`() {
        val result = WakeOnLan.parseMac("")

        assertNull(result)
    }

    @Test
    fun `parseMac with invalid hex characters returns null`() {
        val result = WakeOnLan.parseMac("GG:HH:II:JJ:KK:LL")

        assertNull(result)
    }

    @Test
    fun `isValidMac returns true for valid address`() {
        assertTrue(WakeOnLan.isValidMac("AA:BB:CC:DD:EE:FF"))
        assertTrue(WakeOnLan.isValidMac("00-11-22-33-44-55"))
        assertTrue(WakeOnLan.isValidMac("AABBCCDDEEFF"))
    }

    @Test
    fun `isValidMac returns false for invalid address`() {
        assertFalse(WakeOnLan.isValidMac(""))
        assertFalse(WakeOnLan.isValidMac("not-a-mac"))
        assertFalse(WakeOnLan.isValidMac("AA:BB:CC"))
        assertFalse(WakeOnLan.isValidMac("GG:HH:II:JJ:KK:LL"))
    }

    @Test
    fun `parseMac bytes are correct for known values`() {
        val result = WakeOnLan.parseMac("01:23:45:67:89:AB")

        assertNotNull(result)
        assertEquals(0x01.toByte(), result!![0])
        assertEquals(0x23.toByte(), result[1])
        assertEquals(0x45.toByte(), result[2])
        assertEquals(0x67.toByte(), result[3])
        assertEquals(0x89.toByte(), result[4])
        assertEquals(0xAB.toByte(), result[5])
    }
}
