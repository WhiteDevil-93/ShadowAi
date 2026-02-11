package com.shadowai.app.ai

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors system memory pressure and provides warnings before loading large models.
 *
 * This class helps prevent OutOfMemoryErrors by checking available memory
 * before attempting to load large AI models into RAM.
 *
 * Memory Thresholds:
 * - CRITICAL: < 50MB available (model loading blocked)
 * - LOW: < 200MB available (warnings issued)
 * - NORMAL: >= 200MB available (safe to load models)
 */
@Singleton
class MemoryPressureMonitor @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "MemoryPressure"

        // Memory thresholds in bytes
        private const val CRITICAL_THRESHOLD_BYTES = 50 * 1024 * 1024L  // 50MB
        private const val LOW_THRESHOLD_BYTES = 200 * 1024 * 1024L      // 200MB
        private const val RECOMMENDED_FREE_BYTES = 500 * 1024 * 1024L   // 500MB

        // Model size estimates (conservative)
        private const val SMALL_MODEL_SIZE = 100 * 1024 * 1024L   // 100MB
        private const val MEDIUM_MODEL_SIZE = 500 * 1024 * 1024L  // 500MB
        private const val LARGE_MODEL_SIZE = 2048 * 1024 * 1024L  // 2GB
    }

    private val activityManager: ActivityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    /**
     * Memory pressure levels
     */
    enum class MemoryPressure {
        NORMAL,     // Plenty of memory available
        LOW,        // Memory is getting low, proceed with caution
        CRITICAL    // Very low memory, block model loading
    }

    /**
     * Result of memory check with recommendations
     */
    data class MemoryCheckResult(
        val pressure: MemoryPressure,
        val availableMemoryMB: Long,
        val totalMemoryMB: Long,
        val usagePercent: Int,
        val canLoadModel: Boolean,
        val recommendation: String
    )

    /**
     * Check current memory pressure level.
     *
     * @return Current memory pressure state
     */
    suspend fun checkMemoryPressure(): MemoryCheckResult = withContext(Dispatchers.Default) {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val availableMemory = memoryInfo.availMem
        val totalMemory = memoryInfo.totalMem
        val usagePercent = ((totalMemory - availableMemory) * 100 / totalMemory).toInt()

        val pressure = when {
            availableMemory < CRITICAL_THRESHOLD_BYTES -> MemoryPressure.CRITICAL
            availableMemory < LOW_THRESHOLD_BYTES -> MemoryPressure.LOW
            else -> MemoryPressure.NORMAL
        }

        val canLoadModel = availableMemory >= CRITICAL_THRESHOLD_BYTES

        val recommendation = when (pressure) {
            MemoryPressure.CRITICAL ->
                "Memory critically low (${formatBytes(availableMemory)}). Cannot load model safely. " +
                "Close other apps or restart device."

            MemoryPressure.LOW ->
                "Memory low (${formatBytes(availableMemory)}). Model loading may succeed but system " +
                "could become unstable. Consider closing background apps."

            MemoryPressure.NORMAL ->
                "Memory sufficient (${formatBytes(availableMemory)} available). Safe to load models."
        }

        Log.d(TAG, "Memory check: $pressure, Available: ${formatBytes(availableMemory)}, " +
                "Total: ${formatBytes(totalMemory)}, Usage: $usagePercent%")

        MemoryCheckResult(
            pressure = pressure,
            availableMemoryMB = availableMemory / (1024 * 1024),
            totalMemoryMB = totalMemory / (1024 * 1024),
            usagePercent = usagePercent,
            canLoadModel = canLoadModel,
            recommendation = recommendation
        )
    }

    /**
     * Check if sufficient memory is available to load a model of given size.
     *
     * @param modelSizeBytes Size of the model file in bytes
     * @return MemoryCheckResult with specific recommendations for this model
     */
    suspend fun canLoadModel(modelSizeBytes: Long): MemoryCheckResult = withContext(Dispatchers.Default) {
        val baseCheck = checkMemoryPressure()
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        // Models typically require 1.5-2x their file size in RAM due to:
        // - File reading buffers
        // - Model decompression
        // - Runtime tensors and activations
        val estimatedRAMNeeded = MemoryConstants.estimateModelRam(modelSizeBytes)

        val availableMemory = memoryInfo.availMem
        val hasSufficientMemory = availableMemory >= estimatedRAMNeeded + CRITICAL_THRESHOLD_BYTES

        val canLoad = hasSufficientMemory && baseCheck.canLoadModel

        val recommendation = when {
            !hasSufficientMemory ->
                "Model requires ~${formatBytes(estimatedRAMNeeded)} RAM, but only " +
                "${formatBytes(availableMemory)} available. Loading may fail with OutOfMemoryError. " +
                "Try a smaller model or free up memory."

            baseCheck.pressure == MemoryPressure.CRITICAL ->
                baseCheck.recommendation

            baseCheck.pressure == MemoryPressure.LOW ->
                "Model should fit in available memory (${formatBytes(availableMemory)}), but " +
                "system memory is low. Loading may succeed but could cause instability."

            else ->
                "Sufficient memory available (${formatBytes(availableMemory)}) for model " +
                "(~${formatBytes(estimatedRAMNeeded)} needed). Safe to proceed."
        }

        Log.d(TAG, "Model size: ${formatBytes(modelSizeBytes)}, Estimated RAM: ${formatBytes(estimatedRAMNeeded)}, " +
                "Available: ${formatBytes(availableMemory)}, Can load: $canLoad")

        baseCheck.copy(
            canLoadModel = canLoad,
            recommendation = recommendation
        )
    }

    /**
     * Get recommended maximum model size for current memory state.
     *
     * @return Maximum safe model size in bytes, or null if memory is too low
     */
    suspend fun getRecommendedMaxModelSize(): Long? = withContext(Dispatchers.Default) {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val availableMemory = memoryInfo.availMem

        when {
            availableMemory < CRITICAL_THRESHOLD_BYTES -> null
            availableMemory < LOW_THRESHOLD_BYTES -> SMALL_MODEL_SIZE
            availableMemory < RECOMMENDED_FREE_BYTES -> MEDIUM_MODEL_SIZE
            else -> LARGE_MODEL_SIZE
        }
    }

    /**
     * Log current memory state for debugging.
     */
    suspend fun logMemoryState(tag: String = TAG) = withContext(Dispatchers.Default) {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val runtime = Runtime.getRuntime()
        val maxHeap = runtime.maxMemory()
        val totalHeap = runtime.totalMemory()
        val freeHeap = runtime.freeMemory()
        val usedHeap = totalHeap - freeHeap

        Log.d(tag, """
            Memory State:
            System Available: ${formatBytes(memoryInfo.availMem)}
            System Total: ${formatBytes(memoryInfo.totalMem)}
            System Low Memory: ${memoryInfo.lowMemory}
            System Threshold: ${formatBytes(memoryInfo.threshold)}

            App Heap Max: ${formatBytes(maxHeap)}
            App Heap Total: ${formatBytes(totalHeap)}
            App Heap Used: ${formatBytes(usedHeap)}
            App Heap Free: ${formatBytes(freeHeap)}
        """.trimIndent())
    }

    /**
     * Trim application memory in response to system memory pressure.
     * This is the preferred way to reduce memory usage compared to explicit GC.
     */
    fun trimMemory() {
        val runtime = Runtime.getRuntime()
        val before = runtime.freeMemory()

        // Clear soft references and caches
        runtime.runFinalization()

        Log.d(TAG, "Memory trimmed, heap free: ${formatBytes(runtime.freeMemory())} (was ${formatBytes(before)})")
    }

    /**
     * Format bytes to human-readable string using US locale for consistency in logs.
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes.toDouble() / (1L shl 30))
            bytes >= 1L shl 20 -> String.format(Locale.US, "%.2f MB", bytes.toDouble() / (1L shl 20))
            bytes >= 1L shl 10 -> String.format(Locale.US, "%.2f KB", bytes.toDouble() / (1L shl 10))
            else -> "$bytes bytes"
        }
    }
}
