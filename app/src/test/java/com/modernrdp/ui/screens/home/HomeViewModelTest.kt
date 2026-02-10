package com.modernrdp.ui.screens.home

import app.cash.turbine.test
import com.modernrdp.data.model.ConnectionGroup
import com.modernrdp.data.model.RdpConnection
import com.modernrdp.data.repository.ConnectionRepository
import com.modernrdp.rdp.SessionTracker
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: ConnectionRepository
    private lateinit var sessionTracker: SessionTracker

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        sessionTracker = SessionTracker()

        every { repository.getAllGroups() } returns flowOf(emptyList())
        every { repository.getAllConnections() } returns flowOf(emptyList())
        every { repository.searchConnections(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = HomeViewModel(repository, sessionTracker)

    @Test
    fun `initial search query is empty`() {
        val vm = createViewModel()

        assertEquals("", vm.searchQuery.value)
    }

    @Test
    fun `setSearchQuery updates query`() {
        val vm = createViewModel()

        vm.setSearchQuery("server")

        assertEquals("server", vm.searchQuery.value)
    }

    @Test
    fun `search with blank query returns empty results`() = runTest {
        val vm = createViewModel()

        vm.searchResults.test {
            assertEquals(emptyList<RdpConnection>(), awaitItem())

            vm.setSearchQuery("")
            // Should still be empty
            expectNoEvents()

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `search with non-blank query calls repository`() = runTest {
        val results = listOf(RdpConnection(id = 1, hostname = "server1.com"))
        every { repository.searchConnections("server") } returns flowOf(results)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.setSearchQuery("server")
        advanceUntilIdle()

        assertEquals(results, vm.searchResults.value)
    }

    @Test
    fun `groupedConnections groups by group`() = runTest {
        val group = ConnectionGroup(id = 1, name = "Production")
        val conn1 = RdpConnection(id = 1, hostname = "prod1", groupId = 1)
        val conn2 = RdpConnection(id = 2, hostname = "prod2", groupId = 1)
        val conn3 = RdpConnection(id = 3, hostname = "dev1", groupId = null)

        every { repository.getAllGroups() } returns flowOf(listOf(group))
        every { repository.getAllConnections() } returns flowOf(listOf(conn1, conn2, conn3))

        val vm = createViewModel()
        advanceUntilIdle()

        val grouped = vm.groupedConnections.value
        assertEquals(2, grouped.size) // Production group + ungrouped
        assertEquals("Production", grouped[0].group?.name)
        assertEquals(2, grouped[0].connections.size)
        assertEquals(null, grouped[1].group)
        assertEquals(1, grouped[1].connections.size)
    }

    @Test
    fun `toggleGroupExpanded collapses and expands`() = runTest {
        val group = ConnectionGroup(id = 1, name = "Servers")
        val conn = RdpConnection(id = 1, hostname = "server", groupId = 1)

        every { repository.getAllGroups() } returns flowOf(listOf(group))
        every { repository.getAllConnections() } returns flowOf(listOf(conn))

        val vm = createViewModel()
        advanceUntilIdle()

        // Initially expanded
        assertTrue(vm.groupedConnections.value[0].isExpanded)

        vm.toggleGroupExpanded(1L)
        advanceUntilIdle()

        assertFalse(vm.groupedConnections.value[0].isExpanded)

        vm.toggleGroupExpanded(1L)
        advanceUntilIdle()

        assertTrue(vm.groupedConnections.value[0].isExpanded)
    }

    @Test
    fun `deleteConnection calls repository`() = runTest {
        val vm = createViewModel()
        val conn = RdpConnection(id = 1, hostname = "server")

        vm.deleteConnection(conn)
        advanceUntilIdle()

        coVerify { repository.deleteConnection(conn) }
    }

    @Test
    fun `markConnected calls repository`() = runTest {
        val vm = createViewModel()

        vm.markConnected(42L)
        advanceUntilIdle()

        coVerify { repository.markConnected(42L) }
    }

    @Test
    fun `createGroup calls repository and dismisses dialog`() = runTest {
        coEvery { repository.saveGroup(any()) } returns 1L
        val vm = createViewModel()

        vm.showCreateGroupDialog()
        assertTrue(vm.showGroupDialog.value)

        vm.createGroup("New Group")
        advanceUntilIdle()

        assertFalse(vm.showGroupDialog.value)
        coVerify { repository.saveGroup(match { it.name == "New Group" }) }
    }

    @Test
    fun `deleteGroup calls repository`() = runTest {
        val vm = createViewModel()
        val group = ConnectionGroup(id = 1, name = "Old Group")

        vm.deleteGroup(group)
        advanceUntilIdle()

        coVerify { repository.deleteGroup(group) }
    }

    @Test
    fun `moveConnectionToGroup calls repository`() = runTest {
        val vm = createViewModel()

        vm.moveConnectionToGroup(1L, 2L)
        advanceUntilIdle()

        coVerify { repository.moveToGroup(1L, 2L) }
    }

    @Test
    fun `quickConnect saves connection and invokes callback`() = runTest {
        coEvery { repository.saveConnection(any()) } returns 99L
        val vm = createViewModel()
        var createdId: Long? = null

        vm.quickConnect("quick.server.com", 3389, "user", "pass") { id ->
            createdId = id
        }
        advanceUntilIdle()

        assertEquals(99L, createdId)
        coVerify {
            repository.saveConnection(match {
                it.hostname == "quick.server.com" &&
                    it.port == 3389 &&
                    it.username == "user" &&
                    it.password == "pass"
            })
        }
    }

    @Test
    fun `activeSessions reflects SessionTracker state`() = runTest {
        val vm = createViewModel()

        assertTrue(vm.activeSessions.value.isEmpty())

        sessionTracker.registerSession(1L, "active-server")

        assertEquals(1, vm.activeSessions.value.size)
    }

    @Test
    fun `showGroupDialog and dismissGroupDialog toggle state`() {
        val vm = createViewModel()

        assertFalse(vm.showGroupDialog.value)

        vm.showCreateGroupDialog()
        assertTrue(vm.showGroupDialog.value)

        vm.dismissGroupDialog()
        assertFalse(vm.showGroupDialog.value)
    }
}
