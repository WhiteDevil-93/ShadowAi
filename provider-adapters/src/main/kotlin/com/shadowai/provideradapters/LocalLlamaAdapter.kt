package com.shadowai.provideradapters

import android.util.Log
import com.shadowai.core.LocalGenerationConfig
import com.shadowai.core.LocalInferenceEngine
import com.shadowai.core.LocalModelHandle
import com.shadowai.core.ProviderId
import com.shadowai.core.Transform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Adapter for local GGUF models using llama.cpp.
 * Handles local model execution with unified interface.
 *
 * **Architecture:** Uses constructor injection of LocalInferenceEngine, which is implemented by
 * LocalInferenceManager in the :app module. This maintains proper module boundaries while
 * enabling full local inference functionality.
 *
 * **Usage:** Simply call [execute()] - the adapter automatically handles:
 * - Model loading (if not already loaded)
 * - Inference execution
 * - Model unloading (to free memory)
 *
 * H-9 FIX: Caches loaded model handle to support chat sessions.
 * Model is unloaded only when shutdown() is called (on eviction or app close).
 */
class LocalLlamaAdapter @Inject constructor(
    override val config: ProviderAdapterConfig,
    private val inferenceEngine: LocalInferenceEngine? = null
) : ProviderAdapter {

    private companion object {
        private const val TAG = "LocalLlamaAdapter"
        private const val VRAM_MULTIPLIER = 1.2f  // VRAM ≈ model size * 1.2 for overhead
        private const val MOBILE_VRAM_WARNING_THRESHOLD_MB = 4096  // 4GB warning threshold
    }

    private var isInitialized = false

    // H-9 FIX: Cache the loaded model handle
    private var activeModel: LocalModelHandle? = null
    private val modelLock = Mutex()
    private val adapterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val providerId: ProviderId = config.providerId

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Validate model file exists and is readable
            val modelFile = File(config.baseUrl)
            if (!modelFile.exists()) {
                Log.w(TAG, "Model file not found: ${config.baseUrl}")
                return@withContext false
            }
            if (!modelFile.isFile || !modelFile.canRead()) {
                Log.w(TAG, "Model file not readable: ${config.baseUrl}")
                return@withContext false
            }

            // Verify GGUF format by checking magic bytes
            val magic = modelFile.inputStream().use { stream ->
                val header = ByteArray(4)
                stream.read(header)
                header.joinToString("") { String.format("%02X", it) }
            }
            if (magic != "47475546") {  // GGUF in hex
                Log.w(TAG, "Invalid GGUF header: $magic")
                return@withContext false
            }

            // Check VRAM requirements and warn if model may be too large
            val vramEstimate = estimateVramRequirement(modelFile.length())
            Log.d(TAG, "Initialized adapter for model: ${modelFile.name} (est. VRAM: ${vramEstimate}MB)")

            if (vramEstimate > MOBILE_VRAM_WARNING_THRESHOLD_MB) {
                Log.w(TAG, "Model may be too large for mobile devices (>4GB VRAM estimated)")
            }

            isInitialized = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            false
        }
    }

    override suspend fun validateConfig(): Boolean {
        return config.baseUrl.isNotBlank() &&
               File(config.baseUrl).exists()
    }

    override suspend fun isAvailable(): Boolean {
        // Adapter is available if initialized and config is valid
        // Model loading happens automatically in execute()
        return isInitialized
    }

    // H-8 FIX: Cleanup resources on shutdown
    override fun shutdown() {
        try {
            val model = activeModel
            if (model != null) {
                // Fire-and-forget cleanup on a dedicated adapter scope.
                adapterScope.launch {
                    try {
                        inferenceEngine?.unloadModel(model)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to unload model during background shutdown", e)
                    }
                }
                activeModel = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
    }

    override suspend fun canExecute(transform: Transform): Boolean {
        return when (transform) {
            is Transform.TextToText -> true
            is Transform.TextToImage -> false // Would need separate image model
            is Transform.ImageToText -> false
            else -> false
        }
    }

    override suspend fun execute(
        transform: Transform,
        input: Any,
        parameters: Map<String, Any>
    ): Result<Any> = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            return@withContext Result.failure(IllegalStateException("Adapter not initialized. Call initialize() first."))
        }
        try {
            // Validate inference engine is available
            if (inferenceEngine == null) {
                return@withContext Result.failure(
                    UnsupportedOperationException(
                        "Local inference backend is not wired for LocalLlamaAdapter. " +
                        "Ensure LocalInferenceEngine is provided via DI."
                    )
                )
            }

            when (transform) {
                is Transform.TextToText -> executeTextToText(input, parameters)
                else -> Result.failure(
                    UnsupportedOperationException("Transform ${transform::class.simpleName} not supported by local adapter")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Execution failed", e)
            Result.failure(e)
        }
    }

    /**
     * Execute TextToText transform with automatic model load/unload.
     * H-9 FIX: Caches loaded model handle instead of unloading immediately.
     */
    private suspend fun executeTextToText(
        input: Any,
        parameters: Map<String, Any>
    ): Result<Any> = withContext(Dispatchers.IO) {
        val prompt = input as String
        val maxTokens = parameters["maxTokens"] as? Int ?: 512
        val temperature = parameters["temperature"] as? Double ?: 0.7

        // Validate inputs
        if (prompt.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Prompt cannot be blank"))
        }
        if (maxTokens <= 0) {
            return@withContext Result.failure(IllegalArgumentException("maxTokens must be > 0"))
        }
        if (temperature !in 0.0..2.0) {
            return@withContext Result.failure(IllegalArgumentException("temperature must be in [0.0, 2.0]"))
        }

        // Validate inference engine is available
        val engine = inferenceEngine
            ?: return@withContext Result.failure(
                UnsupportedOperationException(
                    "Local inference backend is not wired for LocalLlamaAdapter. " +
                    "Ensure LocalInferenceEngine is provided via DI."
                )
            )

        if (!engine.isNativeAvailable) {
            return@withContext Result.failure(
                UnsupportedOperationException(
                    "Native inference library is not available. " +
                    "Ensure the llama_jni native library is loaded."
                )
            )
        }

        Log.d(TAG, "Executing TextToText: prompt=${prompt.take(50)}..., maxTokens=$maxTokens, temp=$temperature")

        // Use cached model or load new one
        val model = modelLock.withLock {
            var handle = activeModel
            if (handle == null || !handle.isValid()) {
                try {
                    val genConfig = LocalGenerationConfig(
                        maxTokens = maxTokens,
                        temp = temperature.toFloat()
                    )
                    handle = engine.loadModel(config.baseUrl, genConfig)
                    if (handle == null) {
                         return@withContext Result.failure(
                            IllegalStateException("Failed to load model: ${config.baseUrl}")
                        )
                    }
                    activeModel = handle
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load model: ${config.baseUrl}", e)
                    return@withContext Result.failure(e)
                }
            }
            handle
        }

        return@withContext try {
            // Execute generation through the typed interface (no reflection)
            val result = model.generateWithConfig(
                prompt = prompt,
                config = LocalGenerationConfig(
                    maxTokens = maxTokens,
                    temp = temperature.toFloat()
                )
            )

            if (result.isSuccess) {
                Result.success(result.getOrThrow())
            } else {
                Result.failure(result.exceptionOrNull() ?: RuntimeException("Unknown generation error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            Result.failure(e)
        }
        // H-9 FIX: Removed finally block that unloaded model. Model stays loaded until shutdown().
    }

    override fun getPriority(transform: Transform): Int {
        return when (transform) {
            is Transform.TextToText -> 100 // Local models preferred for text
            else -> 0
        }
    }

    /**
     * Estimates VRAM requirement for a model file with optional context window and batch size factors.
     *
     * VRAM calculation:
     * - Base model size: fileSizeBytes * VRAM_MULTIPLIER (for weights and overhead)
     * - Context window factor: nCtx * bytes_per_token * model_size_factor
     * - Batch size factor: batch_size * additional_buffer_per_batch
     *
     * @param fileSizeBytes Size of the model file in bytes
     * @param nCtx Context window size (default: 2048). Larger context requires more VRAM.
     * @param batchSize Batch size for generation (default: 1). Larger batches require more VRAM.
     * @return Estimated VRAM required in MB
     */
    fun estimateVramRequirement(
        fileSizeBytes: Long,
        nCtx: Int = 2048,
        batchSize: Int = 1
    ): Int {
        val sizeMB = fileSizeBytes / (1024 * 1024)

        // Base VRAM for model weights and overhead
        val baseVramMB = (sizeMB * VRAM_MULTIPLIER).toInt()

        // Context window VRAM: nCtx * 2 bytes per token * 2x factor for activation buffers
        // This accounts for KV cache and intermediate activations
        val contextVramMB = (nCtx * 2L * 2) / (1024 * 1024)

        // Batch size VRAM: additional buffer for each batch
        // Each batch requires activation memory proportional to context size
        val batchVramMB = (batchSize * nCtx * 2L) / (1024 * 1024)

        return baseVramMB + contextVramMB.toInt() + batchVramMB.toInt()
    }

    /**
     * Estimates VRAM requirement for a model file using the configured context size.
     *
     * @param fileSizeBytes Size of the model file in bytes
     * @return Estimated VRAM required in MB
     */
    fun estimateVramRequirement(fileSizeBytes: Long): Int {
        // Use default context size (2048) from GenerationConfig
        return estimateVramRequirement(fileSizeBytes, nCtx = 2048, batchSize = 1)
    }

    /**
     * Estimates VRAM requirement for the configured model.
     *
     * @return Estimated VRAM required in MB, or -1 if model file not accessible
     */
    fun estimateVramRequirement(): Int {
        val modelFile = File(config.baseUrl)
        return if (modelFile.exists()) {
            estimateVramRequirement(modelFile.length())
        } else {
            -1
        }
    }

    // ==================== Error Data Classes ====================

    /**
     * LocalLlama API error wrapper for consistency with other adapters.
     * Local inference errors are typically wrapped in exceptions rather than HTTP responses.
     */
    data class LocalLlamaApiError(
        val message: String?,
        val code: String? = null,
        val type: String? = null
    )

    // ==================== Custom Exceptions ====================

    sealed class LocalLlamaException(
        message: String,
        cause: Throwable? = null
    ) : Exception(message, cause) {

        class ModelLoadError(modelPath: String) : LocalLlamaException(
            "Failed to load model from: $modelPath. Ensure the file exists and is a valid GGUF format."
        )

        class InvalidModelFormat : LocalLlamaException(
            "Invalid GGUF model format. The model file header is not recognized."
        )

        class ModelTooLarge(estVramMB: Int) : LocalLlamaException(
            "Model requires approximately $estVramMB MB VRAM, which may exceed device limits."
        )

        class InferenceError(message: String?) : LocalLlamaException(
            "Inference failed: ${message ?: "Unknown error"}"
        )

        class NotInitialized : LocalLlamaException(
            "Adapter not initialized. Call initialize() first before executing."
        )

        class InvalidInput(message: String?) : LocalLlamaException(
            "Invalid input: ${message ?: "Check prompt and parameters"}"
        )

        class UnknownError(message: String?) : LocalLlamaException(
            "Unknown local inference error: ${message ?: "No details"}"
        )
    }
}
