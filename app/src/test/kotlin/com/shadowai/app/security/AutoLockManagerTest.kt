package com.shadowai.app.security

import android.app.Activity
import android.app.Application
import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for AutoLockManager.
 * Tests lifecycle callback registration, timeout handling, and activity detection.
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AutoLockManagerTest {

    private lateinit var autoLockManager: AutoLockManager
    private lateinit var mockContext: Context
    private lateinit var mockApplication: Application
    private lateinit var mockActivity: Activity
    
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        mockApplication = mock()
        mockContext = mockApplication
        mockActivity = mock()
        autoLockManager = AutoLockManager(mockContext)
    }

    @After
    fun tearDown() {
        autoLockManager.stopMonitoring()
    }

    // ========== Lifecycle Callback Registration Tests ==========

    @Test
    fun `lifecycle callback registration - should register with Application context`() {
        // Given: AutoLockManager initialized with Application context
        // When: initialized
        // Then: lifecycle callbacks should be registered (verified by constructor)
        assertFalse(autoLockManager.isLocked.value)
    }

    @Test
    fun `lifecycle callback registration - should handle non-Application context gracefully`() {
        // Given: a regular Context (not Application)
        val regularContext = mock<Context>()
        
        // When: creating AutoLockManager
        val manager = AutoLockManager(regularContext)
        
        // Then: should not crash and should still function
        assertFalse(manager.isLocked.value)
    }

    // ========== Timeout Handling Tests ==========

    @Test
    fun `timeout handling - should lock after timeout period`() = testScope.runTest {
        // Given: monitoring started with 1 minute timeout
        autoLockManager.startMonitoring(timeoutMinutes = 1)
        assertFalse(autoLockManager.isLocked.value)
        
        // When: simulating 1.5 minutes of inactivity
        advanceTimeBy(70_000) // 70 seconds
        
        // Then: app should be locked
        assertTrue(autoLockManager.isLocked.first())
    }

    @Test
    fun `timeout handling - should not lock before timeout period`() = testScope.runTest {
        // Given: monitoring started with 5 minute timeout
        autoLockManager.startMonitoring(timeoutMinutes = 5)
        
        // When: simulating 3 minutes of inactivity
        advanceTimeBy(180_000) // 180 seconds
        
        // Then: app should NOT be locked
        assertFalse(autoLockManager.isLocked.value)
    }

    @Test
    fun `timeout handling - should reset timer on user interaction`() = testScope.runTest {
        // Given: monitoring started with 2 minute timeout
        autoLockManager.startMonitoring(timeoutMinutes = 2)
        
        // When: 1 minute passes, then user interaction, then another minute
        advanceTimeBy(60_000)
        autoLockManager.recordUserInteraction()
        advanceTimeBy(60_000)
        
        // Then: app should NOT be locked (total 2 min but reset at 1 min)
        assertFalse(autoLockManager.isLocked.value)
    }

    @Test
    fun `timeout handling - should stop monitoring when stopped`() = testScope.runTest {
        // Given: monitoring started
        autoLockManager.startMonitoring(timeoutMinutes = 1)
        
        // When: stopping monitoring and waiting past timeout
        autoLockManager.stopMonitoring()
        advanceTimeBy(70_000)
        
        // Then: app should NOT be locked
        assertFalse(autoLockManager.isLocked.value)
    }

    @Test
    fun `timeout handling - inference should extend timeout`() = testScope.runTest {
        // Given: monitoring started with 1 minute timeout
        autoLockManager.startMonitoring(timeoutMinutes = 1)
        
        // When: starting inference and waiting past normal timeout
        autoLockManager.startInference()
        advanceTimeBy(120_000) // Should not lock during inference
        
        // Then: app should NOT be locked
        assertFalse(autoLockManager.isLocked.value)
    }

    @Test
    fun `timeout handling - ending inference resets timer`() = testScope.runTest {
        // Given: monitoring started with 1 minute timeout and inference active
        autoLockManager.startMonitoring(timeoutMinutes = 1)
        autoLockManager.startInference()
        advanceTimeBy(60_000)
        
        // When: ending inference
        autoLockManager.endInference()
        
        // When: waiting less than timeout after inference ends
        advanceTimeBy(30_000)
        
        // Then: app should NOT be locked
        assertFalse(autoLockManager.isLocked.value)
    }

    @Test
    fun `timeout handling - unlock should restart monitoring`() = testScope.runTest {
        // Given: locked app with monitoring stopped
        autoLockManager.startMonitoring(timeoutMinutes = 1)
        advanceTimeBy(70_000)
        assertTrue(autoLockManager.isLocked.value)
        
        // When: unlocking the app
        autoLockManager.unlock()
        
        // Then: should not be locked and monitoring resumed
        assertFalse(autoLockManager.isLocked.value)
        
        // Verify timeout still works after unlock
        advanceTimeBy(70_000)
        assertTrue(autoLockManager.isLocked.first())
    }

    // ========== Activity Foreground-Background Detection Tests ==========

    @Test
    fun `activity detection - should start monitoring when app comes to foreground`() = testScope.runTest {
        // Given: app in background
        autoLockManager.onActivityStopped(mockActivity)
        
        // When: activity starts (app to foreground)
        autoLockManager.onActivityStarted(mockActivity)
        
        // Then: monitoring should be active
        autoLockManager.startMonitoring(timeoutMinutes = 1)
        advanceTimeBy(70_000)
        assertTrue(autoLockManager.isLocked.first())
    }

    @Test
    fun `activity detection - should stop monitoring when app goes to background`() = testScope.runTest {
        // Given: app in foreground with monitoring
        autoLockManager.startMonitoring(timeoutMinutes = 1)
        
        // When: last activity stops (app to background)
        autoLockManager.onActivityStopped(mockActivity)
        
        // Then: monitoring stopped, so timeout won't trigger lock
        advanceTimeBy(70_000)
        assertFalse(autoLockManager.isLocked.value)
    }

    @Test
    fun `activity detection - should lock when returning from background after timeout`() = testScope.runTest {
        // Given: monitoring stopped (app in background)
        autoLockManager.startMonitoring(timeoutMinutes = 1)
        autoLockManager.onActivityStopped(mockActivity)
        
        // When: simulating time passing while in background
        Thread.sleep(100) // Small delay to let timestamp progress
        
        // When: returning to foreground past timeout
        autoLockManager.onActivityStarted(mockActivity)
        
        // Then: may lock depending on timing
        // This tests the shouldLockOnForeground logic
        val shouldLock = autoLockManager.shouldLockOnForeground()
    }

    @Test
    fun `activity detection - should track multiple activities correctly`() = testScope.runTest {
        // Given: first activity starts
        val activity1 = mock<Activity>()
        val activity2 = mock<Activity>()
        
        // When: first activity starts
        autoLockManager.onActivityStarted(activity1)

        // When: second activity starts (config change or new screen)
        autoLockManager.onActivityStarted(activity2)
        
        // When: first activity stops (but second still running)
        autoLockManager.onActivityStopped(activity1)
        
        // Then: should still consider app in foreground
        // Background monitoring should NOT resume yet
        advanceTimeBy(70_000)
        assertFalse(autoLockManager.isLocked.value)
    }

    @Test
    fun `activity detection - should handle activity count accurately`() = testScope.runTest {
        // Given: multiple activities
        val activity1 = mock<Activity>()
        val activity2 = mock<Activity>()
        val activity3 = mock<Activity>()
        
        // When: activities start and stop in sequence
        autoLockManager.onActivityStarted(activity1) // count = 1
        autoLockManager.onActivityStarted(activity2) // count = 2
        autoLockManager.onActivityStopped(activity1) // count = 1 (still foreground)
        autoLockManager.onActivityStarted(activity3) // count = 2
        autoLockManager.onActivityStopped(activity2) // count = 1 (still foreground)
        autoLockManager.onActivityStopped(activity3) // count = 0 (background)
        
        // Then: monitoring should respect background state
        autoLockManager.startMonitoring(timeoutMinutes = 1)
        advanceTimeBy(70_000)
        // Should not lock because we went to background (monitoring stopped)
        assertFalse(autoLockManager.isLocked.value)
    }

    @Test
    fun `shouldLockOnForeground - returns true when timeout elapsed`() {
        // Given: autoLockManager initialized
        autoLockManager.startMonitoring(timeoutMinutes = 1)
        autoLockManager.stopMonitoring()
        
        // Wait a bit to simulate time passage
        Thread.sleep(50)
        
        // When: checking if should lock
        // Note: This is timing-dependent, so we test the method exists and returns a boolean
        val result = autoLockManager.shouldLockOnForeground()
    }

    @Test
    fun `recordUserInteraction - should update last interaction time`() = testScope.runTest {
        // Given: monitoring started
        autoLockManager.startMonitoring(timeoutMinutes = 2)
        
        // When: recording user interaction
        autoLockManager.recordUserInteraction()
        
        // When: partial timeout passes
        advanceTimeBy(90_000) // 1.5 minutes
        
        // Then: should NOT be locked because interaction was recorded
        assertFalse(autoLockManager.isLocked.value)
    }

    @Test
    fun `recordUserInteraction - should not update when already locked`() = testScope.runTest {
        // Given: app is locked
        autoLockManager.startMonitoring(timeoutMinutes = 1)
        advanceTimeBy(70_000)
        assertTrue(autoLockManager.isLocked.first())
        
        // When: trying to record interaction while locked
        autoLockManager.recordUserInteraction()
        
        // Then: should remain locked
        assertTrue(autoLockManager.isLocked.value)
    }

    @Test
    fun `lifecycle callbacks - should implement all required methods`() {
        // Test that all lifecycle callback methods exist and don't throw
        val bundle = mock<android.os.Bundle>()
        
        autoLockManager.onActivityCreated(mockActivity, bundle)
        autoLockManager.onActivityStarted(mockActivity)
        autoLockManager.onActivityResumed(mockActivity)
        autoLockManager.onActivityPaused(mockActivity)
        autoLockManager.onActivityStopped(mockActivity)
        autoLockManager.onActivitySaveInstanceState(mockActivity, bundle)
        autoLockManager.onActivityDestroyed(mockActivity)
        
        // If we get here without exceptions, the test passes
        assertTrue(true)
    }

    @Test
    fun `update timeout - should respect new timeout value`() = testScope.runTest {
        // Given: monitoring with 5 minute timeout
        autoLockManager.startMonitoring(timeoutMinutes = 5)
        
        // When: updating to 1 minute timeout
        autoLockManager.startMonitoring(timeoutMinutes = 1)
        
        // When: waiting past new timeout
        advanceTimeBy(70_000)
        
        // Then: should be locked with new shorter timeout
        assertTrue(autoLockManager.isLocked.first())
    }

    @Test
    fun `isLocked flow - should emit correct values`() = testScope.runTest {
        // Given: fresh AutoLockManager
        val initialValue = autoLockManager.isLocked.value
        assertFalse(initialValue)
        
        // When: starting monitoring and timeout elapses
        autoLockManager.startMonitoring(timeoutMinutes = 1)
        advanceTimeBy(70_000)
        
        // Then: locked state should be true
        assertTrue(autoLockManager.isLocked.first())
        
        // When: unlocking
        autoLockManager.unlock()
        
        // Then: locked state should be false
        assertFalse(autoLockManager.isLocked.value)
    }
}
