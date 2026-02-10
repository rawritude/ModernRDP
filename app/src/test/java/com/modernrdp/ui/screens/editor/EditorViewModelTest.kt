package com.modernrdp.ui.screens.editor

import androidx.lifecycle.SavedStateHandle
import com.modernrdp.data.model.RdpConnection
import com.modernrdp.data.model.ResolutionMode
import com.modernrdp.data.repository.ConnectionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: ConnectionRepository
    private lateinit var savedStateHandle: SavedStateHandle

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        every { repository.getAllGroups() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(connectionId: Long = -1L): EditorViewModel {
        savedStateHandle = SavedStateHandle(mapOf("connectionId" to connectionId))
        return EditorViewModel(repository, savedStateHandle)
    }

    @Test
    fun `new connection mode has default state`() {
        val vm = createViewModel()

        assertFalse(vm.isEditing)
        assertEquals("", vm.state.value.hostname)
        assertEquals("3389", vm.state.value.port)
        assertEquals("", vm.state.value.username)
        assertEquals("", vm.state.value.password)
        assertTrue(vm.state.value.useTls)
        assertTrue(vm.state.value.useNla)
    }

    @Test
    fun `edit mode loads connection data`() = runTest {
        val existing = RdpConnection(
            id = 42,
            name = "Test Server",
            hostname = "10.0.0.1",
            port = 3390,
            username = "admin",
            password = "secret",
            domain = "CORP",
            resolutionMode = ResolutionMode.CUSTOM,
            customWidth = 2560,
            customHeight = 1440,
        )
        coEvery { repository.getConnectionById(42) } returns existing

        val vm = createViewModel(connectionId = 42)
        advanceUntilIdle()

        assertTrue(vm.isEditing)
        assertEquals("Test Server", vm.state.value.name)
        assertEquals("10.0.0.1", vm.state.value.hostname)
        assertEquals("3390", vm.state.value.port)
        assertEquals("admin", vm.state.value.username)
        assertEquals("secret", vm.state.value.password)
        assertEquals("CORP", vm.state.value.domain)
        assertEquals(ResolutionMode.CUSTOM, vm.state.value.resolutionMode)
        assertEquals("2560", vm.state.value.customWidth)
        assertEquals("1440", vm.state.value.customHeight)
    }

    @Test
    fun `updateName updates state`() {
        val vm = createViewModel()

        vm.updateName("My Server")

        assertEquals("My Server", vm.state.value.name)
    }

    @Test
    fun `updateHostname updates state and clears error`() {
        val vm = createViewModel()

        vm.updateHostname("192.168.1.1")

        assertEquals("192.168.1.1", vm.state.value.hostname)
        assertNull(vm.state.value.hostnameError)
    }

    @Test
    fun `updatePort updates state`() {
        val vm = createViewModel()

        vm.updatePort("5900")

        assertEquals("5900", vm.state.value.port)
    }

    @Test
    fun `updateResolutionMode updates state`() {
        val vm = createViewModel()

        vm.updateResolutionMode(ResolutionMode.CUSTOM)

        assertEquals(ResolutionMode.CUSTOM, vm.state.value.resolutionMode)
    }

    @Test
    fun `updateColorDepth updates state`() {
        val vm = createViewModel()

        vm.updateColorDepth(16)

        assertEquals(16, vm.state.value.colorDepth)
    }

    @Test
    fun `updateMacAddress updates state`() {
        val vm = createViewModel()

        vm.updateMacAddress("AA:BB:CC:DD:EE:FF")

        assertEquals("AA:BB:CC:DD:EE:FF", vm.state.value.macAddress)
    }

    @Test
    fun `save with blank hostname sets error`() = runTest {
        val vm = createViewModel()
        var saved = false

        vm.save { saved = true }
        advanceUntilIdle()

        assertFalse(saved)
        assertEquals("Hostname is required", vm.state.value.hostnameError)
    }

    @Test
    fun `save with valid hostname calls repository`() = runTest {
        coEvery { repository.saveConnection(any()) } returns 1L
        val vm = createViewModel()
        var saved = false

        vm.updateHostname("192.168.1.1")
        vm.updateUsername("admin")
        vm.save { saved = true }
        advanceUntilIdle()

        assertTrue(saved)
        coVerify { repository.saveConnection(match { it.hostname == "192.168.1.1" && it.username == "admin" }) }
    }

    @Test
    fun `save trims hostname`() = runTest {
        coEvery { repository.saveConnection(any()) } returns 1L
        val vm = createViewModel()

        vm.updateHostname("  192.168.1.1  ")
        vm.save {}
        advanceUntilIdle()

        coVerify { repository.saveConnection(match { it.hostname == "192.168.1.1" }) }
    }

    @Test
    fun `save with invalid port defaults to 3389`() = runTest {
        coEvery { repository.saveConnection(any()) } returns 1L
        val vm = createViewModel()

        vm.updateHostname("server.com")
        vm.updatePort("not_a_number")
        vm.save {}
        advanceUntilIdle()

        coVerify { repository.saveConnection(match { it.port == 3389 }) }
    }

    @Test
    fun `delete calls repository and invokes callback`() = runTest {
        val existing = RdpConnection(id = 42, hostname = "server.com")
        coEvery { repository.getConnectionById(42) } returns existing

        val vm = createViewModel(connectionId = 42)
        advanceUntilIdle()

        var deleted = false
        vm.delete { deleted = true }
        advanceUntilIdle()

        assertTrue(deleted)
        coVerify { repository.deleteConnection(existing) }
    }

    @Test
    fun `delete on new connection does nothing`() = runTest {
        val vm = createViewModel()
        var deleted = false

        vm.delete { deleted = true }
        advanceUntilIdle()

        assertFalse(deleted)
    }

    @Test
    fun `save includes macAddress trimmed`() = runTest {
        coEvery { repository.saveConnection(any()) } returns 1L
        val vm = createViewModel()

        vm.updateHostname("server.com")
        vm.updateMacAddress("  AA:BB:CC:DD:EE:FF  ")
        vm.save {}
        advanceUntilIdle()

        coVerify { repository.saveConnection(match { it.macAddress == "AA:BB:CC:DD:EE:FF" }) }
    }

    @Test
    fun `save includes gateway fields`() = runTest {
        coEvery { repository.saveConnection(any()) } returns 1L
        val vm = createViewModel()

        vm.updateHostname("server.com")
        vm.updateGatewayHostname("gw.example.com")
        vm.updateGatewayPort("8443")
        vm.updateGatewayUsername("gwuser")
        vm.updateGatewayPassword("gwpass")
        vm.save {}
        advanceUntilIdle()

        coVerify {
            repository.saveConnection(match {
                it.gatewayHostname == "gw.example.com" &&
                    it.gatewayPort == 8443 &&
                    it.gatewayUsername == "gwuser" &&
                    it.gatewayPassword == "gwpass"
            })
        }
    }
}
