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
     * Minimum RAM required to safely run the Liquid AI engine.
     */
    const val LIQUID_MIN_FREE_RAM_BYTES = 3L * 1024L * 1024L * 1024L

    /**
     * Calculate estimated RAM requirement for a model file.
     *
     * @param fileSizeBytes Size of the model file in bytes
     * @return Estimated RAM needed in bytes
     */
    fun estimateModelRam(fileSizeBytes: Long): Long {
        return (fileSizeBytes * MODEL_MEMORY_MULTIPLIER).toLong()
    }
}
