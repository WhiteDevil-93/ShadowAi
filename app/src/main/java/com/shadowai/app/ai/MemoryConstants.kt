package com.shadowai.app.ai

/**
 * Centralized memory calculation constants for consistent model sizing across the app.
 *
 * All model size estimates use a unified multiplier to ensure consistent memory
 * requirements across LocalLiquidEngine, MemoryPressureMonitor, and ModelLoadingHelper.
 */
object MemoryConstants {

    /**
     * Memory multiplier for GGUF model estimates.
     *
     * GGUF models require approximately 2x their file size in RAM due to:
     * - Model weights and parameters
     * - Intermediate activation buffers
     * - Attention mechanism allocations
     * - KV cache for generation
     */
    const val MODEL_MEMORY_MULTIPLIER = 2.0

    /**
     * Minimum file size for a valid GGUF model (1 MB).
     */
    const val MIN_MODEL_FILE_SIZE = 1024L * 1024L

    /**
     * Maximum filename length for downloaded models (prevents filesystem issues).
     */
    const val MAX_FILENAME_LENGTH = 255

    /**
     * Default model directory name within app context.
     */
    const val DEFAULT_MODEL_DIR = "models"

    /**
     * GGUF file format magic number (little-endian bytes: "GGUF").
     * Encoded as: 0x47 0x47 0x55 0x46 = "GGUF"
     */
    const val GGUF_MAGIC_HEX = "47475546"

    /**
     * M-6 FIX: VRAM estimation constants for context window and batch size factors.
     *
     * REMOVED COMPANION OBJECT - Illegal inside standalone object.
     */

    /**
     * Minimum RAM required to safely run the Liquid AI engine.
     */
    const val LIQUID_MIN_FREE_RAM_BYTES = 3L * 1024L * 1024L * 1024L

    /**
     * Default context size in tokens for VRAM estimation.
     */
    const val DEFAULT_CONTEXT_SIZE = 2048

    /**
     * Default batch size for VRAM estimation.
     */
    const val DEFAULT_BATCH_SIZE = 1

    /**
     * KV cache memory per token in bytes (approximate, based on typical LLM architectures).
     */
    const val KV_CACHE_BYTES_PER_TOKEN = 2048L

    /**
     * Additional working memory buffer for activations and computation (in bytes).
     */
    const val WORKING_MEMORY_BUFFER = 512L * 1024L * 1024L // 512MB

    /**
     * Safety margin factor (1.2 = 20% extra memory).
     */
    const val SAFETY_MARGIN_FACTOR = 1.2

    /**
     * Calculate estimated RAM requirement for a model file.
     *
     * @param fileSizeBytes Size of the model file in bytes
     * @return Estimated RAM needed in bytes
     */
    fun estimateModelRam(fileSizeBytes: Long): Long {
        return estimateModelRam(fileSizeBytes, DEFAULT_CONTEXT_SIZE, DEFAULT_BATCH_SIZE)
    }

    /**
     * Calculate estimated RAM requirement for a model with context and batch parameters.
     *
     * @param fileSizeBytes Size of the model file in bytes
     * @param contextSize Context window size in tokens (default: 2048)
     * @param batchSize Batch size for parallel generation (default: 1)
     * @return Estimated RAM needed in bytes
     */
    fun estimateModelRam(
        fileSizeBytes: Long,
        contextSize: Int = DEFAULT_CONTEXT_SIZE,
        batchSize: Int = DEFAULT_BATCH_SIZE
    ): Long {
        val baseModelMemory = (fileSizeBytes * MODEL_MEMORY_MULTIPLIER).toLong()
        val kvCacheMemory = KV_CACHE_BYTES_PER_TOKEN * contextSize
        val workingMemory = WORKING_MEMORY_BUFFER
        val baseTotal = baseModelMemory + kvCacheMemory + workingMemory
        val withBatchSize = baseTotal * batchSize
        val finalEstimate = (withBatchSize * SAFETY_MARGIN_FACTOR).toLong()

        return finalEstimate
    }

    /**
     * Estimate VRAM for a specific configuration.
     */
    fun estimateVramWithConfig(
        fileSizeBytes: Long,
        nCtx: Int,
        nBatch: Int = 1
    ): Long = estimateModelRam(fileSizeBytes, nCtx, nBatch)
}
