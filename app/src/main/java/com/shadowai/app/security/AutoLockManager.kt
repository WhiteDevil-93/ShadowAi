package com.shadowai.app.security

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages app auto-lock functionality based on user inactivity.
 *
 * Tracks the last user interaction time and locks the app after a configurable timeout.
 * Automatically extends the timeout during active AI inference to prevent interruption during long operations.
 *
 * Uses Application.ActivityLifecycleCallbacks to properly detect app foreground/background states.
 *
 * FIXES APPLIED:
 * 1. Removed dedicated SupervisorJob scope that was never cancelled properly.
 * 2. Switched to ProcessLifecycleOwner.lifecycleScope which is lifecycle-aware.
 * 3. Corrected loop logic to prevent tight looping/cpu usage.
 */
@Singleton
class AutoLockManager @Inject constructor(
    @ApplicationContext context: Context
) : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
    companion object {
        private const val TAG = "AutoLockManager"
    }

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val lastUserInteraction = AtomicLong(System.currentTimeMillis())
    private val activeInference = AtomicBoolean(false)

    // Removed leaking scope. We will use ProcessLifecycleOwner.lifecycleScope

    private val activityCount = AtomicInteger(0)

    private var checkJob: Job? = null
    // Make timeout volatile for thread safety
    @Volatile
    private var currentTimeoutMinutes: Int = 5

    private val application = context.applicationContext as? Application

    init {
        // Register lifecycle callbacks to detect app foreground/background
        application?.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        Log.d(TAG, "Initialized and registered lifecycle callbacks")
    }

    override fun onStart(owner: LifecycleOwner) {
        // App is coming to foreground
        if (!_isLocked.value) {
            startMonitoring(currentTimeoutMinutes)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        // App is going to background
        stopMonitoring()
    }

    /**
     * Release lifecycle registrations.
     * Safe to call multiple times.
     */
    fun shutdown() {
        stopMonitoring()
        application?.unregisterActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        Log.d(TAG, "Shutdown complete")
    }

    /**
     * Start monitoring for inactivity.
     * Should be called when the user enters the app and auto-lock is enabled.
     */
    fun startMonitoring(timeoutMinutes: Int = 5) {
        currentTimeoutMinutes = timeoutMinutes
        // Do not reset _isLocked here, it might be intentionally locked
        lastUserInteraction.set(System.currentTimeMillis())
        Log.d(TAG, "Starting monitoring with timeout=${timeoutMinutes}m")

        stopMonitoring() // Stop any existing monitoring

        // Use ProcessLifecycleOwner scope which is tied to app lifecycle
        checkJob = ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(10_000) // Check every 10 seconds

                if (!activeInference.get() && !_isLocked.value) {
                    val lastActiveTime = lastUserInteraction.get()
                    val currentTime = System.currentTimeMillis()
                    val elapsedMinutes = (currentTime - lastActiveTime) / 60_000

                    // Check if inactivity timeout reached
                    if (elapsedMinutes >= currentTimeoutMinutes) {
                        _isLocked.value = true
                        Log.i(TAG, "Auto-lock triggered after ${elapsedMinutes}m of inactivity")
                        // Don't stop monitoring, keep checking in case state is reset externally?
                        // Actually, once locked, we don't need to check inactivity anymore until unlocked.
                        break
                    }
                }
            }
        }
    }

    /**
     * Stop monitoring for auto-lock.
     * Called when app is backgrounded or auto-lock is disabled.
     */
    fun stopMonitoring() {
        checkJob?.cancel()
        checkJob = null
        Log.d(TAG, "Stopped monitoring")
    }

    /**
     * Record a user interaction to reset the inactivity timer.
     * Call this from user actions like:
     * - Key presses
     * - Touch events
     * - Navigation
     * - Button clicks
     */
    fun recordUserInteraction() {
        if (!_isLocked.value) {
            lastUserInteraction.set(System.currentTimeMillis())
        }
    }

    /**
     * Mark that AI inference is in progress.
     * This will extend the auto-lock timeout until inference completes.
     */
    fun startInference() {
        activeInference.set(true)
        // Reset interaction time to prevent lock during inference
        lastUserInteraction.set(System.currentTimeMillis())
        Log.d(TAG, "Inference started; auto-lock paused")
    }

    /**
     * Mark that AI inference has completed.
     */
    fun endInference() {
        activeInference.set(false)
        // Reset timer so user has full timeout after inference
        lastUserInteraction.set(System.currentTimeMillis())
        Log.d(TAG, "Inference ended; auto-lock resumed")
    }

    /**
     * Unlock the app after successful authentication.
     */
    fun unlock() {
        _isLocked.value = false
        lastUserInteraction.set(System.currentTimeMillis())
        startMonitoring(currentTimeoutMinutes)
        Log.i(TAG, "Unlocked and monitoring resumed")
    }

    /**
     * Clears locked state without restarting monitoring.
     * Useful when auto-lock is disabled from settings.
     */
    fun clearLockState() {
        _isLocked.value = false
        lastUserInteraction.set(System.currentTimeMillis())
        Log.d(TAG, "Lock state cleared")
    }

    /**
     * Check if the app should be locked immediately (e.g., on app foreground).
     */
    fun shouldLockOnForeground(): Boolean {
        val lastActiveTime = lastUserInteraction.get()
        val currentTime = System.currentTimeMillis()
        val elapsedMinutes = (currentTime - lastActiveTime) / 60_000

        return elapsedMinutes >= currentTimeoutMinutes && !_isLocked.value
    }

    // ========== Application.ActivityLifecycleCallbacks ==========

    override fun onActivityStarted(activity: Activity) {
        val count = activityCount.incrementAndGet()
        // Determine if this is the first activity starting (app coming to foreground)
        if (count == 1) {
             if (!_isLocked.value && shouldLockOnForeground()) {
                // App is returning from background and timeout elapsed
                _isLocked.value = true
                Log.i(TAG, "Locked on foreground due to elapsed timeout")
            } else if (!_isLocked.value) {
                // App is foregrounding normally, monitoring starts via DefaultLifecycleObserver.onStart
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        val count = activityCount.decrementAndGet()
        // DefaultLifecycleObserver.onStop handles stopping monitoring when process stops
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
