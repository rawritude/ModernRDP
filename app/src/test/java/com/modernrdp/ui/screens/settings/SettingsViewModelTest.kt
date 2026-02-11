package com.modernrdp.ui.screens.settings

import app.cash.turbine.test
import com.modernrdp.data.preferences.AppPreferences
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
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var prefs: AppPreferences

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        prefs = mockk(relaxed = true)

        // Default preference flows
        every { prefs.themeMode } returns flowOf("system")
        every { prefs.defaultResolutionMode } returns flowOf("match_device")
        every { prefs.certVerification } returns flowOf(false)
        every { prefs.confirmDisconnect } returns flowOf(true)
        every { prefs.requireBiometric } returns flowOf(false)
        every { prefs.defaultPort } returns flowOf(3389)
        every { prefs.clipboardSync } returns flowOf(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SettingsViewModel(prefs)

    @Test
    fun `initial state has default values`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("system", state.themeMode)
        assertEquals("match_device", state.defaultResolutionMode)
        assertFalse(state.certVerification)
        assertTrue(state.confirmDisconnect)
        assertFalse(state.requireBiometric)
        assertEquals(3389, state.defaultPort)
        assertTrue(state.clipboardSync)
    }

    @Test
    fun `setThemeMode calls prefs`() = runTest {
        coEvery { prefs.setThemeMode(any()) } returns Unit
        val vm = createViewModel()

        vm.setThemeMode("dark")
        advanceUntilIdle()

        coVerify { prefs.setThemeMode("dark") }
    }

    @Test
    fun `setDefaultResolutionMode calls prefs`() = runTest {
        coEvery { prefs.setDefaultResolutionMode(any()) } returns Unit
        val vm = createViewModel()

        vm.setDefaultResolutionMode("custom")
        advanceUntilIdle()

        coVerify { prefs.setDefaultResolutionMode("custom") }
    }

    @Test
    fun `setCertVerification calls prefs`() = runTest {
        coEvery { prefs.setCertVerification(any()) } returns Unit
        val vm = createViewModel()

        vm.setCertVerification(true)
        advanceUntilIdle()

        coVerify { prefs.setCertVerification(true) }
    }

    @Test
    fun `setConfirmDisconnect calls prefs`() = runTest {
        coEvery { prefs.setConfirmDisconnect(any()) } returns Unit
        val vm = createViewModel()

        vm.setConfirmDisconnect(false)
        advanceUntilIdle()

        coVerify { prefs.setConfirmDisconnect(false) }
    }

    @Test
    fun `setRequireBiometric calls prefs`() = runTest {
        coEvery { prefs.setRequireBiometric(any()) } returns Unit
        val vm = createViewModel()

        vm.setRequireBiometric(true)
        advanceUntilIdle()

        coVerify { prefs.setRequireBiometric(true) }
    }

    @Test
    fun `setDefaultPort calls prefs`() = runTest {
        coEvery { prefs.setDefaultPort(any()) } returns Unit
        val vm = createViewModel()

        vm.setDefaultPort(5900)
        advanceUntilIdle()

        coVerify { prefs.setDefaultPort(5900) }
    }

    @Test
    fun `setClipboardSync calls prefs`() = runTest {
        coEvery { prefs.setClipboardSync(any()) } returns Unit
        val vm = createViewModel()

        vm.setClipboardSync(false)
        advanceUntilIdle()

        coVerify { prefs.setClipboardSync(false) }
    }

    @Test
    fun `state updates when preferences change`() = runTest {
        every { prefs.themeMode } returns flowOf("dark")
        every { prefs.certVerification } returns flowOf(true)
        every { prefs.confirmDisconnect } returns flowOf(false)

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals("dark", vm.state.value.themeMode)
        assertTrue(vm.state.value.certVerification)
        assertFalse(vm.state.value.confirmDisconnect)
    }

    @Test
    fun `SettingsState data class has correct defaults`() {
        val state = SettingsState()

        assertEquals("system", state.themeMode)
        assertEquals("match_device", state.defaultResolutionMode)
        assertFalse(state.certVerification)
        assertTrue(state.confirmDisconnect)
        assertFalse(state.requireBiometric)
        assertEquals(3389, state.defaultPort)
        assertTrue(state.clipboardSync)
    }
}
