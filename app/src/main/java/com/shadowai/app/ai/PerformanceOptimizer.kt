package com.shadowai.app.ai

import android.util.Log
import com.shadowai.app.auth.UserPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Manages performance optimization settings for ShadowAi.
 *
 * This class initializes and manages:
 * - NNAPI delegation based on device capabilities
 * - Memory-mapped model loading
 * - Auto-summarization for context management
 *
 * Auto-initialization happens on first app launch to set optimal defaults.
 */
@Singleton
class PerformanceOptimizer @Inject constructor(
    private val deviceCapabilities: DeviceCapabilities,
    private val userPreferences: UserPreferences
) {
    companion object {
        private const val TAG = "PerformanceOptimizer"
    }

    private var initialized = false

    /**
     * Initialize performance optimization settings on first app launch.
     */
    suspend fun initializeIfFirstLaunch() {
        if (initialized) {
            return
        }

        // Check if user has already set preferences
        val currentNnapi = userPreferences.nnapiDelegationEnabled.first()
        val currentMmap = userPreferences.memoryMappingEnabled.first()
        val currentSummary = userPreferences.autoSummarizationEnabled.first()

        // Only auto-configure if user hasn't set explicit values
        if (currentNnapi == false && currentMmap == true && currentSummary == true) {
            // These are defaults, so user hasn't overridden them
            configureOptimalDefaults()
        }

        initialized = true
        Log.i(TAG, "Performance optimizer initialized")
    }

    /**
     * Configure optimal defaults based on device capabilities.
     */
    private suspend fun configureOptimalDefaults() {
        val deviceInfo = deviceCapabilities.getDeviceInfo()

        // Enable NNAPI if device supports it
        if (deviceInfo.canUseNnapi) {
            Log.i(TAG, "Auto-enabling NNAPI for ${deviceInfo.brand} ${deviceInfo.model}")
            userPreferences.saveNnapiDelegationEnabled(true)
        } else {
            Log.i(TAG, "NNAPI not supported or not recommended for this device")
            userPreferences.saveNnapiDelegationEnabled(false)
        }

        // Always enable memory mapping (allows larger models)
        Log.i(TAG, "Enabling memory-mapped model loading")
        userPreferences.saveMemoryMappingEnabled(true)

        // Always enable auto-summarization (manages context window)
        Log.i(TAG, "Enabling auto-summarization")
        userPreferences.saveAutoSummarizationEnabled(true)

        // Log configuration summary
        Log.i(TAG, buildString {
            append("Performance configuration:\n")
            append("  NNAPI: ${if (deviceInfo.canUseNnapi) "ENABLED" else "DISABLED"}\n")
            append("  Memory Mapping: ENABLED\n")
            append("  Auto-Summarization: ENABLED")
        })
    }

    /**
     * Get a GenerationConfig that incorporates user preferences.
     */
    suspend fun getOptimizedConfig(
        baseConfig: LlamaNative.GenerationConfig = LlamaNative.GenerationConfig()
    ): LlamaNative.GenerationConfig {
        val useNnapi = userPreferences.nnapiDelegationEnabled.first()
        val useMmap = userPreferences.memoryMappingEnabled.first()

        // Validate NNAPI is actually supported before enabling
        val safeNnapi = if (useNnapi) {
            if (deviceCapabilities.supportsNnapi()) {
                true
            } else {
                Log.w(TAG, "NNAPI requested but not supported on this device")
                false
            }
        } else {
            false
        }

        return baseConfig.copy(
            useNnapi = safeNnapi,
            useMmap = useMmap
        )
    }

    /**
     * Get human-readable performance summary.
     */
    suspend fun getPerformanceSummary(): String {
        val deviceInfo = deviceCapabilities.getDeviceInfo()
        val useNnapi = userPreferences.nnapiDelegationEnabled.first()
        val useMmap = userPreferences.memoryMappingEnabled.first()
        val autoSummary = userPreferences.autoSummarizationEnabled.first()

        return buildString {
            appendLine("=== Performance Status ===")
            appendLine(deviceCapabilities.getCapabilityDescription())
            appendLine()
            appendLine("Current Settings:")
            appendLine("  NNAPI Acceleration: ${if (useNnapi) "ON" else "OFF"}")
            appendLine("  Memory Mapping: ${if (useMmap) "ON" else "OFF"}")
            appendLine("  Auto-Summarization: ${if (autoSummary) "ON" else "OFF"}")
            appendLine()
            if (deviceInfo.canUseNnapi && useNnapi) {
                appendLine("⚡ Expected 2-3x inference speed boost")
            }
        }
    }

    /**
     * Check if performance can be improved by changing settings.
     */
    suspend fun getPerformanceSuggestion(): String? {
        val deviceInfo = deviceCapabilities.getDeviceInfo()
        val useNnapi = userPreferences.nnapiDelegationEnabled.first()

        if (deviceInfo.canUseNnapi && !useNnapi) {
            return "Enable NNAPI acceleration for 2-3x faster inference"
        }

        if (!deviceInfo.canUseNnapi && deviceInfo.nnapiAvailable) {
            return "NNAPI is available but not recommended for this device"
        }

        return null
    }
}
