package com.modernrdp.data.repository

import com.modernrdp.data.crypto.CredentialEncryption
import com.modernrdp.data.local.ConnectionDao
import com.modernrdp.data.local.GroupDao
import com.modernrdp.data.model.ConnectionGroup
import com.modernrdp.data.model.RdpConnection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionRepositoryTest {

    private lateinit var connectionDao: ConnectionDao
    private lateinit var groupDao: GroupDao
    private lateinit var encryption: CredentialEncryption
    private lateinit var repository: ConnectionRepository

    @Before
    fun setup() {
        connectionDao = mockk(relaxed = true)
        groupDao = mockk(relaxed = true)
        encryption = mockk()

        // Default encryption behavior: pass-through for unencrypted, decrypt for encrypted
        every { encryption.encrypt(any()) } answers { "enc:${firstArg<String>()}" }
        every { encryption.decrypt(any()) } answers {
            val input = firstArg<String>()
            if (input.startsWith("enc:")) input.removePrefix("enc:") else input
        }
        every { encryption.isEncrypted(any()) } answers { firstArg<String>().startsWith("enc:") }

        repository = ConnectionRepository(connectionDao, groupDao, encryption)
    }

    @Test
    fun `getAllConnections decrypts passwords`() = runTest {
        val encrypted = RdpConnection(
            id = 1,
            hostname = "server.com",
            password = "enc:secret",
            gatewayPassword = "enc:gwsecret",
        )
        every { connectionDao.getAllConnections() } returns flowOf(listOf(encrypted))

        val result = repository.getAllConnections().first()

        assertEquals(1, result.size)
        assertEquals("secret", result[0].password)
        assertEquals("gwsecret", result[0].gatewayPassword)
    }

    @Test
    fun `saveConnection encrypts passwords`() = runTest {
        val slot = slot<RdpConnection>()
        coEvery { connectionDao.insertConnection(capture(slot)) } returns 1L

        val plain = RdpConnection(hostname = "server.com", password = "secret", gatewayPassword = "gwpass")
        repository.saveConnection(plain)

        assertTrue(slot.captured.password.startsWith("enc:"))
        assertTrue(slot.captured.gatewayPassword.startsWith("enc:"))
    }

    @Test
    fun `saveConnection does not double-encrypt`() = runTest {
        val slot = slot<RdpConnection>()
        coEvery { connectionDao.insertConnection(capture(slot)) } returns 1L

        val alreadyEncrypted = RdpConnection(
            hostname = "server.com",
            password = "enc:already_encrypted",
            gatewayPassword = "enc:already_encrypted",
        )
        repository.saveConnection(alreadyEncrypted)

        // Should keep the existing encrypted value, not re-encrypt
        assertEquals("enc:already_encrypted", slot.captured.password)
        assertEquals("enc:already_encrypted", slot.captured.gatewayPassword)
    }

    @Test
    fun `getConnectionById decrypts and returns connection`() = runTest {
        val encrypted = RdpConnection(
            id = 42,
            hostname = "mypc.local",
            password = "enc:p@ss",
        )
        coEvery { connectionDao.getConnectionById(42) } returns encrypted

        val result = repository.getConnectionById(42)

        assertEquals("mypc.local", result?.hostname)
        assertEquals("p@ss", result?.password)
    }

    @Test
    fun `getConnectionById returns null for non-existent id`() = runTest {
        coEvery { connectionDao.getConnectionById(999) } returns null

        val result = repository.getConnectionById(999)

        assertNull(result)
    }

    @Test
    fun `updateConnection encrypts before updating`() = runTest {
        val slot = slot<RdpConnection>()
        coEvery { connectionDao.updateConnection(capture(slot)) } returns Unit

        val plain = RdpConnection(id = 1, hostname = "server.com", password = "newpass")
        repository.updateConnection(plain)

        assertTrue(slot.captured.password.startsWith("enc:"))
        coVerify { connectionDao.updateConnection(any()) }
    }

    @Test
    fun `deleteConnection calls dao`() = runTest {
        val conn = RdpConnection(id = 1, hostname = "server.com")
        repository.deleteConnection(conn)

        coVerify { connectionDao.deleteConnection(conn) }
    }

    @Test
    fun `markConnected calls dao with timestamp`() = runTest {
        repository.markConnected(42)

        coVerify { connectionDao.updateLastConnected(42, any()) }
    }

    @Test
    fun `moveToGroup calls dao`() = runTest {
        repository.moveToGroup(1L, 2L)

        coVerify { connectionDao.moveToGroup(1L, 2L) }
    }

    @Test
    fun `searchConnections decrypts results`() = runTest {
        val encrypted = RdpConnection(
            id = 1,
            hostname = "search-result.com",
            password = "enc:hidden",
        )
        every { connectionDao.searchConnections("search") } returns flowOf(listOf(encrypted))

        val results = repository.searchConnections("search").first()

        assertEquals(1, results.size)
        assertEquals("hidden", results[0].password)
    }

    @Test
    fun `getAllGroups returns groups from dao`() = runTest {
        val groups = listOf(
            ConnectionGroup(id = 1, name = "Production"),
            ConnectionGroup(id = 2, name = "Dev"),
        )
        every { groupDao.getAllGroups() } returns flowOf(groups)

        val result = repository.getAllGroups().first()

        assertEquals(2, result.size)
        assertEquals("Production", result[0].name)
    }

    @Test
    fun `saveGroup calls dao and returns id`() = runTest {
        coEvery { groupDao.insertGroup(any()) } returns 5L

        val group = ConnectionGroup(name = "New Group")
        val id = repository.saveGroup(group)

        assertEquals(5L, id)
    }

    @Test
    fun `deleteGroup calls dao`() = runTest {
        val group = ConnectionGroup(id = 1, name = "Old Group")
        repository.deleteGroup(group)

        coVerify { groupDao.deleteGroup(group) }
    }

    @Test
    fun `saveConnection with blank password keeps blank`() = runTest {
        // Blank passwords should pass through encryption unchanged
        every { encryption.encrypt("") } returns ""
        every { encryption.isEncrypted("") } returns false

        val slot = slot<RdpConnection>()
        coEvery { connectionDao.insertConnection(capture(slot)) } returns 1L

        val conn = RdpConnection(hostname = "server.com", password = "", gatewayPassword = "")
        repository.saveConnection(conn)

        assertEquals("", slot.captured.password)
    }

    @Test
    fun `getConnectionsByGroup decrypts results`() = runTest {
        val encrypted = RdpConnection(
            id = 1,
            hostname = "grouped.com",
            password = "enc:grouppass",
            groupId = 1L,
        )
        every { connectionDao.getConnectionsByGroup(1L) } returns flowOf(listOf(encrypted))

        val results = repository.getConnectionsByGroup(1L).first()

        assertEquals("grouppass", results[0].password)
    }
}
