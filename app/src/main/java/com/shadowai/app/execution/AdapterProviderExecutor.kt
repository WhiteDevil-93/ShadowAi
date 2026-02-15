package com.shadowai.app.execution

import com.shadowai.app.providers.AdapterBridge
import com.shadowai.app.providers.ProviderSelector
import com.shadowai.app.security.ApiKeyRedaction
import com.shadowai.app.tasks.TaskType
import com.shadowai.core.Artifact
import com.shadowai.core.Modality
import com.shadowai.core.ProviderExecutor
import com.shadowai.core.Transform
import com.shadowai.core.providers.ActiveProviderConfig
import com.shadowai.pipelineplanner.PipelinePlanner
import android.os.StatFs
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdapterProviderExecutor @Inject constructor(
    private val bridge: AdapterBridge,
    private val providerSelector: ProviderSelector
) : PipelinePlanner.ProviderExecutorSource {

    override suspend fun getBestExecutor(transform: Transform): ProviderExecutor? {
        val taskType = when (transform.targetModality) {
            Modality.Image -> TaskType.IMAGE_GEN
            Modality.Audio -> TaskType.AUDIO_GEN
            Modality.Video -> TaskType.VIDEO_GEN
            else -> TaskType.WRITING
        }

        // Try cloud first (assuming network availability), then local
        val cloudConfig = providerSelector.nextCloud(taskType)
        val localConfig = providerSelector.nextLocal(taskType)

        return when {
            cloudConfig != null && localConfig != null ->
                FallbackExecutor(bridge, cloudConfig, localConfig)
            cloudConfig != null ->
                SingleExecutor(bridge, cloudConfig)
            localConfig != null ->
                SingleExecutor(bridge, localConfig)
            else -> null
        }
    }

    private class SingleExecutor(
        private val bridge: AdapterBridge,
        private val config: ActiveProviderConfig
    ) : ProviderExecutor {
        override val providerId = config.providerId
        override suspend fun isAvailable() = true
        override suspend fun canExecute(t: Transform) = true
        override suspend fun execute(
            t: Transform,
            i: Artifact,
            p: Map<String, Any>
        ): Result<Artifact> {
            // bridge.getAdapter is suspend and fetches the fresh API key
            val adapter = bridge.getAdapter(config)
            return adapter.execute(t, i, p)
        }
    }

    /**
     * FIX H-18: LocalInferenceAvailable validation
     * - Check model integrity
     * - Check disk space for KV cache
     * - Check current memory pressure
     * FIX H-19: Provider capability validation (canExecute)
     * FIX H-20: Fallback parameter adjustment (different params for fallback)
     */
    private class FallbackExecutor(
        private val bridge: AdapterBridge,
        private val primary: ActiveProviderConfig,
        private val secondary: ActiveProviderConfig
    ) : ProviderExecutor {
        override val providerId = primary.providerId
        override suspend fun isAvailable(): Boolean {
            // FIX H-18: Validate local inference availability before allowing fallback
            return if (secondary.isLocal) {
                validateLocalInferenceAvailable()
            } else {
                true
            }
        }

        /**
         * FIX H-19: Provider capability validation
         * Check if the primary provider can execute this transform,
         * and if primary fails, check if secondary can execute it.
         */
        override suspend fun canExecute(t: Transform): Boolean {
            val primaryAdapter = bridge.getAdapter(primary)
            val primaryCanExecute = primaryAdapter.canExecute(t)
            if (primaryCanExecute) return true

            // Check if secondary can execute as fallback
            val secondaryAdapter = bridge.getAdapter(secondary)
            return secondaryAdapter.canExecute(t)
        }

        override suspend fun execute(
            t: Transform,
            i: Artifact,
            p: Map<String, Any>
        ): Result<Artifact> {
            val primaryAdapter = bridge.getAdapter(primary)

            // FIX H-19: Validate capability before execution
            if (!primaryAdapter.canExecute(t)) {
                // M-17: Redact API keys from error messages
                val apiKeyRedaction = ApiKeyRedaction()
                val redactedError = apiKeyRedaction.redactThrowable(
                    IllegalStateException("Primary provider ${primary.providerId} cannot execute transform ${t::class.simpleName}")
                )
                return Result.failure(redactedError)
            }

            val primaryResult = primaryAdapter.execute(t, i, p)

            if (primaryResult.isSuccess) return primaryResult

            // FIX H-18: Validate local inference availability before fallback
            if (secondary.isLocal && !validateLocalInferenceAvailable()) {
                return Result.failure(
                    IllegalStateException("Fallback to local provider unavailable: ${getLocalInferenceUnavailableReason()}")
                )
            }

            // FIX H-20: Fallback parameter adjustment - reduce token limits and temperature for fallback
            // This provides faster, more deterministic responses from backup providers
            val fallbackParams = adjustParametersForFallback(p)

            // FIX H-19: Validate secondary capability before fallback execution
            val secondaryAdapter = bridge.getAdapter(secondary)
            if (!secondaryAdapter.canExecute(t)) {
                return Result.failure(
                    IllegalStateException("Secondary provider ${secondary.providerId} cannot execute transform ${t::class.simpleName}")
                )
            }

            return secondaryAdapter.execute(t, i, fallbackParams)
        }

        /**
         * FIX H-20: Adjust parameters for fallback execution.
         * Uses more conservative settings for fallback providers.
         */
        private fun adjustParametersForFallback(original: Map<String, Any>): Map<String, Any> {
            return original.toMutableMap().apply {
                // Reduce max tokens for faster fallback responses (default 1024 -> 512)
                val originalMaxTokens = this["maxTokens"] as? Int ?: 1024
                this["maxTokens"] = (originalMaxTokens / 2).coerceAtLeast(256)

                // Lower temperature for more deterministic responses (default 0.7 -> 0.3)
                val originalTemp = this["temperature"] as? Double ?: 0.7
                this["temperature"] = (originalTemp * 0.5).coerceIn(0.0, 0.5)

                // Reduce top_p for more focused sampling (default 1.0 -> 0.5)
                if (this.containsKey("topP")) {
                    val originalTopP = this["topP"] as? Double ?: 1.0
                    this["topP"] = (originalTopP * 0.5).coerceIn(0.1, 0.5)
                }

                // Add fallback indicator for monitoring
                this["_isFallback"] = true
                this["_fallbackFrom"] = primary.providerId.name
            }
        }

        /**
         * FIX H-18: Check if local inference is available for fallback.
         * Validates:
         * 1. Model integrity (placeholder - would check checksums in production)
         * 2. Disk space for KV cache (minimum 500MB)
         * 3. Memory pressure (< 80% used)
         */
        private fun validateLocalInferenceAvailable(): Boolean {
            return hasEnoughDiskSpace() && !isUnderMemoryPressure()
        }

        private fun getLocalInferenceUnavailableReason(): String {
            val reasons = mutableListOf<String>()
            if (!hasEnoughDiskSpace()) {
                reasons.add("Insufficient disk space for KV cache (need 500MB+)")
            }
            if (isUnderMemoryPressure()) {
                reasons.add("System under memory pressure (>80% RAM used)")
            }
            // Model integrity check would be implemented here
            // if (hasModelIntegrityIssues()) reasons.add("Model integrity check failed")
            return reasons.joinToString(", ")
        }

        /**
         * Check if there is enough disk space for KV cache.
         * Minimum 500MB free space recommended.
         */
        private fun hasEnoughDiskSpace(minFreeSpaceBytes: Long = 500 * 1024 * 1024): Boolean {
            return try {
                val dataDir = File(System.getProperty("java.io.tmpdir", "/tmp"))
                val statFs = StatFs(dataDir.path)
                val availableBytes = statFs.availableBytes
                availableBytes >= minFreeSpaceBytes
            } catch (e: Exception) {
                // If we can't check, assume insufficient to be safe
                false
            }
        }

        /**
         * Check if system is under memory pressure.
         * Returns true if memory usage is above 80%.
         */
        private fun isUnderMemoryPressure(): Boolean {
            return try {
                val runtime = Runtime.getRuntime()
                val maxMemory = runtime.maxMemory()
                val totalMemory = runtime.totalMemory()
                val freeMemory = runtime.freeMemory()
                val usedMemory = totalMemory - freeMemory
                val memoryUsagePercent = usedMemory.toDouble() / maxMemory.toDouble()
                memoryUsagePercent > 0.80
            } catch (e: Exception) {
                // If we can't check, assume under pressure to be safe
                true
            }
        }
    }
}
