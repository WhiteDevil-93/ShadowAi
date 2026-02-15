package com.shadowai.app.ui.settings

import com.shadowai.app.security.PreferencesManager
import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for SecuritySettingsViewModel.
 *
 * Tests preference persistence, StateFlow emissions, and validation logic
 * for security settings including biometric requirements, screenshot protection,
 * and auto-lock timeout configuration.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SecuritySettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @RelaxedMockK
    private lateinit var preferencesManager: PreferencesManager

    private lateinit var viewModel: SecuritySettingsViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }

    private fun createViewModelWithFlows(
        biometricDownloads: Boolean = false,
        biometricHistory: Boolean = false,
        biometricSettings: Boolean = false,
        preventScreenshots: Boolean = true,
        autoLockEnabled: Boolean = false,
        autoLockTimeout: SecuritySettingsViewModel.AutoLockTimeout = SecuritySettingsViewModel.AutoLockTimeout.FIVE_MINUTES
    ) {
        every { preferencesManager.requireBiometricForDownloads } returns flowOf(biometricDownloads)
        every { preferencesManager.requireBiometricForHistory } returns flowOf(biometricHistory)
        every { preferencesManager.requireBiometricForSettings } returns flowOf(biometricSettings)
        every { preferencesManager.preventScreenshots } returns flowOf(preventScreenshots)
        every { preferencesManager.autoLockEnabled } returns flowOf(autoLockEnabled)
        every { preferencesManager.autoLockTimeout } returns flowOf(autoLockTimeout)

        viewModel = SecuritySettingsViewModel(preferencesManager)
    }

    // ========== Initial State Tests ==========

    @Test
    fun `requireBiometricForDownloads should emit false by default`() = runTest {
        // Given
        createViewModelWithFlows(biometricDownloads = false)

        // When & Then
        viewModel.requireBiometricForDownloads.test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requireBiometricForDownloads should emit true when enabled`() = runTest {
        // Given
        createViewModelWithFlows(biometricDownloads = true)

        // When & Then
        viewModel.requireBiometricForDownloads.test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requireBiometricForHistory should emit false by default`() = runTest {
        // Given
        createViewModelWithFlows(biometricHistory = false)

        // When & Then
        viewModel.requireBiometricForHistory.test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requireBiometricForSettings should emit false by default`() = runTest {
        // Given
        createViewModelWithFlows(biometricSettings = false)

        // When & Then
        viewModel.requireBiometricForSettings.test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `preventScreenshots should emit true by default`() = runTest {
        // Given
        createViewModelWithFlows(preventScreenshots = true)

        // When & Then
        viewModel.preventScreenshots.test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `autoLockEnabled should emit false by default`() = runTest {
        // Given
        createViewModelWithFlows(autoLockEnabled = false)

        // When & Then
        viewModel.autoLockEnabled.test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `autoLockTimeout should emit FIVE_MINUTES by default`() = runTest {
        // Given
        createViewModelWithFlows(autoLockTimeout = SecuritySettingsViewModel.AutoLockTimeout.FIVE_MINUTES)

        // When & Then
        viewModel.autoLockTimeout.test {
            assertEquals(SecuritySettingsViewModel.AutoLockTimeout.FIVE_MINUTES, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== Preference Setting Tests ==========

    @Test
    fun `setRequireBiometricForDownloads should call preferencesManager`() = runTest {
        // Given
        coEvery { preferencesManager.saveRequireBiometricForDownloads(any()) } just Runs
        createViewModelWithFlows()

        // When
        viewModel.setRequireBiometricForDownloads(true)
        advanceTimeBy(100) // Give coroutine time to execute

        // Then
        coVerify { preferencesManager.saveRequireBiometricForDownloads(true) }
    }

    @Test
    fun `setRequireBiometricForDownloads should pass false when disabled`() = runTest {
        // Given
        coEvery { preferencesManager.saveRequireBiometricForDownloads(any()) } just Runs
        createViewModelWithFlows()

        // When
        viewModel.setRequireBiometricForDownloads(false)
        advanceTimeBy(100)

        // Then
        coVerify { preferencesManager.saveRequireBiometricForDownloads(false) }
    }

    @Test
    fun `setRequireBiometricForHistory should call preferencesManager`() = runTest {
        // Given
        coEvery { preferencesManager.saveRequireBiometricForHistory(any()) } just Runs
        createViewModelWithFlows()

        // When
        viewModel.setRequireBiometricForHistory(true)
        advanceTimeBy(100)

        // Then
        coVerify { preferencesManager.saveRequireBiometricForHistory(true) }
    }

    @Test
    fun `setRequireBiometricForSettings should call preferencesManager`() = runTest {
        // Given
        coEvery { preferencesManager.saveRequireBiometricForSettings(any()) } just Runs
        createViewModelWithFlows()

        // When
        viewModel.setRequireBiometricForSettings(true)
        advanceTimeBy(100)

        // Then
        coVerify { preferencesManager.saveRequireBiometricForSettings(true) }
    }

    @Test
    fun `setPreventScreenshots should call preferencesManager`() = runTest {
        // Given
        coEvery { preferencesManager.savePreventScreenshots(any()) } just Runs
        createViewModelWithFlows()

        // When
        viewModel.setPreventScreenshots(false)
        advanceTimeBy(100)

        // Then
        coVerify { preferencesManager.savePreventScreenshots(false) }
    }

    @Test
    fun `setAutoLockEnabled should call preferencesManager`() = runTest {
        // Given
        coEvery { preferencesManager.saveAutoLockEnabled(any()) } just Runs
        createViewModelWithFlows()

        // When
        viewModel.setAutoLockEnabled(true)
        advanceTimeBy(100)

        // Then
        coVerify { preferencesManager.saveAutoLockEnabled(true) }
    }

    @Test
    fun `setAutoLockTimeout should call preferencesManager with correct timeout`() = runTest {
        // Given
        coEvery { preferencesManager.saveAutoLockTimeout(any()) } just Runs
        createViewModelWithFlows()

        // When
        viewModel.setAutoLockTimeout(SecuritySettingsViewModel.AutoLockTimeout.THIRTY_MINUTES)
        advanceTimeBy(100)

        // Then
        coVerify { preferencesManager.saveAutoLockTimeout(SecuritySettingsViewModel.AutoLockTimeout.THIRTY_MINUTES) }
    }

    // ========== AutoLockTimeout Enum Tests ==========

    @Test
    fun `AutoLockTimeout ONE_MINUTE should have correct values`() {
        val timeout = SecuritySettingsViewModel.AutoLockTimeout.ONE_MINUTE
        assertEquals(1, timeout.minutes)
        assertEquals("1 minute", timeout.displayName)
    }

    @Test
    fun `AutoLockTimeout FIVE_MINUTES should have correct values`() {
        val timeout = SecuritySettingsViewModel.AutoLockTimeout.FIVE_MINUTES
        assertEquals(5, timeout.minutes)
        assertEquals("5 minutes", timeout.displayName)
    }

    @Test
    fun `AutoLockTimeout FIFTEEN_MINUTES should have correct values`() {
        val timeout = SecuritySettingsViewModel.AutoLockTimeout.FIFTEEN_MINUTES
        assertEquals(15, timeout.minutes)
        assertEquals("15 minutes", timeout.displayName)
    }

    @Test
    fun `AutoLockTimeout THIRTY_MINUTES should have correct values`() {
        val timeout = SecuritySettingsViewModel.AutoLockTimeout.THIRTY_MINUTES
        assertEquals(30, timeout.minutes)
        assertEquals("30 minutes", timeout.displayName)
    }

    @Test
    fun `AutoLockTimeout NEVER should have 0 minutes`() {
        val timeout = SecuritySettingsViewModel.AutoLockTimeout.NEVER
        assertEquals(0, timeout.minutes)
        assertEquals("Never", timeout.displayName)
    }

    @Test
    fun `AutoLockTimeout should have all 5 values in enum`() {
        val values = SecuritySettingsViewModel.AutoLockTimeout.entries
        assertEquals(5, values.size)
        assertTrue(values.contains(SecuritySettingsViewModel.AutoLockTimeout.ONE_MINUTE))
        assertTrue(values.contains(SecuritySettingsViewModel.AutoLockTimeout.FIVE_MINUTES))
        assertTrue(values.contains(SecuritySettingsViewModel.AutoLockTimeout.FIFTEEN_MINUTES))
        assertTrue(values.contains(SecuritySettingsViewModel.AutoLockTimeout.THIRTY_MINUTES))
        assertTrue(values.contains(SecuritySettingsViewModel.AutoLockTimeout.NEVER))
    }

    // ========== StateFlow Behavior Tests ==========

    @Test
    fun `stateFlows should share replay with WhileSubscribed`() = runTest {
        // Given
        createViewModelWithFlows(biometricDownloads = true)

        // When - Collect multiple times
        val values1 = mutableListOf<Boolean>()
        val values2 = mutableListOf<Boolean>()

        val job1 = launch { viewModel.requireBiometricForDownloads.take(1).toList(values1) }
        val job2 = launch { viewModel.requireBiometricForDownloads.take(1).toList(values2) }

        job1.join()
        job2.join()

        // Then - Both collectors should get the same value
        assertEquals(listOf(true), values1)
        assertEquals(listOf(true), values2)
    }

    @Test
    fun `all stateFlows should use same sharing configuration`() = runTest {
        // Given
        createViewModelWithFlows(
            biometricDownloads = true,
            biometricHistory = true,
            biometricSettings = true,
            preventScreenshots = true,
            autoLockEnabled = true,
            autoLockTimeout = SecuritySettingsViewModel.AutoLockTimeout.ONE_MINUTE
        )

        // When & Then - All state flows should emit initial values
        viewModel.requireBiometricForDownloads.test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.requireBiometricForHistory.test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.requireBiometricForSettings.test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.preventScreenshots.test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.autoLockEnabled.test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.autoLockTimeout.test {
            assertEquals(SecuritySettingsViewModel.AutoLockTimeout.ONE_MINUTE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== Coroutine Scope Tests ==========

    @Test
    fun `all save operations should use viewModelScope`() = runTest {
        // Given
        coEvery { preferencesManager.saveRequireBiometricForDownloads(any()) } just Runs
        coEvery { preferencesManager.saveRequireBiometricForHistory(any()) } just Runs
        coEvery { preferencesManager.saveRequireBiometricForSettings(any()) } just Runs
        coEvery { preferencesManager.savePreventScreenshots(any()) } just Runs
        coEvery { preferencesManager.saveAutoLockEnabled(any()) } just Runs
        coEvery { preferencesManager.saveAutoLockTimeout(any()) } just Runs
        createViewModelWithFlows()

        // When
        viewModel.setRequireBiometricForDownloads(true)
        viewModel.setRequireBiometricForHistory(true)
        viewModel.setRequireBiometricForSettings(true)
        viewModel.setPreventScreenshots(false)
        viewModel.setAutoLockEnabled(true)
        viewModel.setAutoLockTimeout(SecuritySettingsViewModel.AutoLockTimeout.FIFTEEN_MINUTES)

        advanceTimeBy(500)

        // Then - All should be called
        coVerify { preferencesManager.saveRequireBiometricForDownloads(true) }
        coVerify { preferencesManager.saveRequireBiometricForHistory(true) }
        coVerify { preferencesManager.saveRequireBiometricForSettings(true) }
        coVerify { preferencesManager.savePreventScreenshots(false) }
        coVerify { preferencesManager.saveAutoLockEnabled(true) }
        coVerify { preferencesManager.saveAutoLockTimeout(SecuritySettingsViewModel.AutoLockTimeout.FIFTEEN_MINUTES) }
    }

    // ========== Helper Class for Test Dispatchers ==========

    class MainDispatcherRule : org.junit.rules.TestWatcher() {
        private val testDispatcher = StandardTestDispatcher()

        override fun starting(description: org.junit.runner.Description?) {
            kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: org.junit.runner.Description?) {
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }
}
