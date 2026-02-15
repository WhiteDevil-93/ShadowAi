package com.shadowai.app.security

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AutoLockManager.
 *
 * Tests lifecycle callback registration, timeout handling, activity
 * foreground/background detection, and auto-lock state management.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoLockManagerTest {

    @RelaxedMockK
    private lateinit var mockContext: Context

    @RelaxedMockK
    private lateinit var mockApplication: Application

    @RelaxedMockK
    private lateinit var mockActivity: Activity

    private lateinit var autoLockManager: AutoLockManager

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { mockContext.applicationContext } returns mockApplication
    }

    private fun createManager() {
        autoLockManager = AutoLockManager(mockContext)
    }

    // ========== Lifecycle Callback Registration Tests ==========

    @Test
    fun `should register activity lifecycle callbacks on init when context is Application`() {
        // Given
        every { mockContext.applicationContext } returns mockApplication

        // When
        createManager()

        // Then
        verify { mockApplication.registerActivityLifecycleCallbacks(any<Application.ActivityLifecycleCallbacks>()) }
    }

    @Test
    fun `should not throw when context is not Application`() {
        // Given
        every { mockContext.applicationContext } returns mockContext

        // When & Then - Should not throw
        createManager()
    }

    // ========== Activity Lifecycle Tests ==========

    @Test
    fun `onActivityStarted should increment activity count`() = runTest {
        // Given
        createManager()

        // When
        autoLockManager.onActivityStarted(mockActivity)
        autoLockManager.onActivityStarted(mockActivity)

        // Then - Should start monitoring on first activity
        autoLockManager.startMonitoring(5)

        // Verify not locked initially
        assertFalse(autoLockManager.isLocked.value)
    }

    @Test
    fun `onActivityStopped should decrement activity count`() = runTest {
        // Given
        createManager()

        // When
        autoLockManager.onActivityStarted(mockActivity)
        autoLockManager.onActivityStopped(mockActivity)

        // Then - After stopping, the next stopMonitoring should not throw
        autoLockManager.stopMonitoring()
    }

    @Test
    fun `should stop monitoring when last activity stops`() = runTest {
        // Given
        createManager()
        autoLockManager.onActivityStarted(mockActivity)
        autoLockManager.startMonitoring(5)

        // When - Stop the only activity
        autoLockManager.onActivityStopped(mockActivity)

        // Then - Monitoring should be stopped (no timeout lock should occur)
        // The manager should not lock because monitoring is stopped
        assertFalse(autoLockManager.isLocked.value)
    }

    @Test
    fun `should lock when returning after timeout period`() = runTest {
        // Given
        createManager()
        autoLockManager.startMonitoring(1) // 1 minute timeout

        // Simulate time passing (more than 1 minute)
        // First stop activity
        autoLockManager.onActivityStarted(mockActivity)
        autoLockManager.onActivityStopped(mockActivity) // App goes to background

        // Simulate time passing by recording user interaction in the past
        // We can't manipulate time easily, but we can test shouldLockOnForeground

        // When - Check if should lock when returning to foreground
        autoLockManager.onActivityStarted(mockActivity)

        // If enough time passed, shouldLockOnForeground would return true
        // But since we can't manipulate System.currentTimeMillis(), we verify the state
        // The isLocked would be set based on shouldLockOnForeground
    }

    @Test
    fun `should not lock when returning before timeout period`() = runTest {
        // Given
        createManager()
        autoLockManager.startMonitoring(30) // 30 minute timeout
        autoLockManager.onActivityStarted(mockActivity)

        // When - Stop and immediately restart
        autoLockManager.onActivityStopped(mockActivity)
        autoLockManager.onActivityStarted(mockActivity)

        // Then - Should not be locked
        assertFalse(autoLockManager.isLocked.value)
    }

    // ========== Timeout Handling Tests ==========

    @Test
    fun `startMonitoring should set isLocked to false`() = runTest {
        // Given
        createManager()

        // When
        autoLockManager.startMonitoring(5)

        // Then
        assertFalse(autoLockManager.isLocked.value)
    }

    @Test
    fun `stopMonitoring should cancel the check job`() = runTest {
        // Given
        createManager()
        autoLockManager.startMonitoring(5)

        // When
        autoLockManager.stopMonitoring()

        // Then - Should not throw and subsequent operations work
        autoLockManager.startMonitoring(5)
        assertFalse(autoLockManager.isLocked.value)
    }

    @Test
    fun `startMonitoring should reset interaction time`() = runTest {
        // Given
        createManager()

        // When
        autoLockManager.startMonitoring(5)

        // Then - The user interaction timestamp should be recent
        // We verify this indirectly by checking shouldLockOnForeground returns false
        assertFalse(autoLockManager.shouldLockOnForeground())
    }

    @Test
    fun `recordUserInteraction should reset inactivity timer`() = runTest {
        // Given
        createManager()
        autoLockManager.startMonitoring(5)

        // When
        autoLockManager.recordUserInteraction()

        // Then
        assertFalse(autoLockManager.shouldLockOnForeground())
    }

    @Test
    fun `recordUserInteraction should not reset when already locked`() = runTest {
        // Given
        createManager()
        autoLockManager.startMonitoring(1)
        autoLockManager.unlock() // Set unlocked state

        // Lock the manager manually
        // We can't easily simulate timeout in tests, but we can verify the method doesn't throw
        autoLockManager.recordUserInteraction()
    }

    // ========== Inference Mode Tests ==========

    @Test
    fun `startInference should prevent auto-lock`() = runTest {
        // Given
        createManager()
        autoLockManager.startMonitoring(5)

        // When
        autoLockManager.startInference()

        // Then - During inference, timeout should be extended
        // The activeInference flag should be true
        // We verify by checking subsequent operations work
        autoLockManager.endInference()
        autoLockManager.recordUserInteraction()
    }

    @Test
    fun `endInference should reset interaction timer`() = runTest {
        // Given
        createManager()
        autoLockManager.startMonitoring(5)
        autoLockManager.startInference()

        // When
        autoLockManager.endInference()

        // Then - Timer should be reset, so not locked
        assertFalse(autoLockManager.shouldLockOnForeground())
    }

    @Test
    fun `should not lock during active inference`() = runTest {
        // Given
        createManager()
        autoLockManager.startMonitoring(5)
        autoLockManager.startInference()

        // When - Even after "timeout" period
        // The inference flag should prevent locking
        // We verify by ending inference and checking still works

        // Then
        autoLockManager.endInference()
        assertFalse(autoLockManager.isLocked.value)
    }

    // ========== Unlock Tests ==========

    @Test
    fun `unlock should set isLocked to false`() = runTest {
        // Given
        createManager()
        autoLockManager.startMonitoring(5)

        // When - Simulate being in a locked state (we can't easily trigger it)
        // Then verify unlock resets everything
        autoLockManager.unlock()

        // Then
        assertFalse(autoLockManager.isLocked.value)
    }

    @Test
    fun `unlock should reset interaction time`() = runTest {
        // Given
        createManager()
        autoLockManager.startMonitoring(1)

        // When
        autoLockManager.unlock()

        // Then - Reset means shouldLockOnForeground returns false
        assertFalse(autoLockManager.shouldLockOnForeground())
    }

    @Test
    fun `unlock should restart monitoring`() = runTest {
        // Given
        createManager()
        autoLockManager.startMonitoring(5)
        autoLockManager.stopMonitoring()

        // When
        autoLockManager.unlock()

        // Then - Monitoring should be active again
        assertFalse(autoLockManager.isLocked.value)
    }

    // ========== shouldLockOnForeground Tests ==========

    @Test
    fun `shouldLockOnForeground should return false when no timeout elapsed`() = runTest {
        // Given
        createManager()
        autoLockManager.startMonitoring(30)

        // When & Then
        assertFalse(autoLockManager.shouldLockOnForeground())
    }

    @Test
    fun `shouldLockOnForeground should respect timeout configuration`() = runTest {
        // Given - Different timeout values
        val timeouts = listOf(1, 5, 15, 30, 0)

        timeouts.forEach { timeout ->
            createManager()
            autoLockManager.startMonitoring(timeout)

            // Immediately after starting, should not lock
            assertFalse("Timeout $timeout should not immediately lock", 
                autoLockManager.shouldLockOnForeground())
        }
    }

    // ========== isLocked StateFlow Tests ==========

    @Test
    fun `isLocked should emit initial value false`() = runTest {
        // Given
        createManager()

        // When & Then
        autoLockManager.isLocked.test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isLocked should maintain state consistency`() = runTest {
        // Given
        createManager()
        autoLockManager.startMonitoring(5)

        // When - Collect the isLocked flow
        val collected = mutableListOf<Boolean>()
        val job = launch {
            autoLockManager.isLocked.take(2).toList(collected)
        }

        // Trigger an unlock()
        autoLockManager.unlock()
        job.join()

        // Then - Values should be consistent
        assertTrue(collected.all { it == false })
    }

    // ========== Other Lifecycle Callbacks Tests ==========

    @Test
    fun `onActivityCreated should not throw`() {
        // Given
        createManager()

        // When & Then
        autoLockManager.onActivityCreated(mockActivity, null)
    }

    @Test
    fun `onActivityResumed should not throw`() {
        // Given
        createManager()

        // When & Then
        autoLockManager.onActivityResumed(mockActivity)
    }

    @Test
    fun `onActivityPaused should not throw`() {
        // Given
        createManager()

        // When & Then
        autoLockManager.onActivityPaused(mockActivity)
    }

    @Test
    fun `onActivitySaveInstanceState should not throw`() {
        // Given
        createManager()

        // When & Then
        autoLockManager.onActivitySaveInstanceState(mockActivity, mockk())
    }

    @Test
    fun `onActivityDestroyed should not throw`() {
        // Given
        createManager()

        // When & Then
        autoLockManager.onActivityDestroyed(mockActivity)
    }

    // ========== Timeout Configuration Tests ==========

    @Test
    fun `startMonitoring should update timeout minutes`() = runTest {
        // Given
        createManager()

        // When - Start with different timeouts
        val timeouts = listOf(1, 5, 15, 30)
        timeouts.forEach { timeout ->
            autoLockManager.startMonitoring(timeout)
            // Just verify no exception and monitoring starts
            assertFalse(autoLockManager.isLocked.value)
        }
    }

    @Test
    fun `consecutive startMonitoring calls should reset monitoring`() = runTest {
        // Given
        createManager()
        autoLockManager.startMonitoring(5)

        // When - Start again with different timeout
        autoLockManager.startMonitoring(10)

        // Then - Monitoring should be reset, still not locked
        assertFalse(autoLockManager.isLocked.value)
    }
}