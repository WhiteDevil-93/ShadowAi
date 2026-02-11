package com.shadowai.core

/**
 * Interface for local LLM inference engines.
 *
 * This interface provides a minimal contract for loading models and performing text generation
 * using local GGUF models. Implementations (like LocalInferenceManager in the :app module)
 * provide the actual inference capability.
 *
 * This interface exists to:
 * 1. Enable proper dependency injection across module boundaries
 * 2. Allow provider-adapters to use local inference without direct dependency on app internals
 * 3. Maintain clean architecture by keeping :provider-adapters decoupled from :app
 *
 * Usage:
 * ```
 * class LocalLlamaAdapter @Inject constructor(
 *     private val inferenceEngine: LocalInferenceEngine,
 *     ...
 * ) : ProviderAdapter { ... }
 * ```
 */
interface LocalInferenceEngine {

    /**
     * Check if the native inference engine is available.
     */
    val isNativeAvailable: Boolean

    /**
     * Load a local GGUF model from the specified path.
     *
     * @param modelPath Absolute path to the GGUF model file
     * @param config Generation configuration parameters
     * @return A handle to the loaded model, or null if loading failed
     */
    suspend fun loadModel(modelPath: String, config: LocalGenerationConfig = LocalGenerationConfig()): LocalModelHandle?

    /**
     * Unload a previously loaded model.
     *
     * @param model The model handle returned by [loadModel]
     */
    suspend fun unloadModel(model: LocalModelHandle)

    /**
     * Check if a model is currently loaded.
     *
     * @param modelPath The path to the model file
     */
    suspend fun isModelLoaded(modelPath: String): Boolean

    /**
     * Get available models in the configured model directory.
     *
     * @return List of absolute paths to available GGUF model files
     */
    suspend fun getAvailableModels(): List<String>
}

/**
 * Handle to a loaded local model.
 *
 * This is a thin wrapper that allows the adapter to reference a loaded model
 * without knowing the concrete implementation type from the :app module.
 */
interface LocalModelHandle {
    /**
     * Get the model path that was used to load this model.
     */
    fun getModelPath(): String

    /**
     * Check if this handle is still valid (model is loaded).
     */
    fun isValid(): Boolean

    /**
     * Generate text from a prompt using this model.
     *
     * @param prompt The input prompt
     * @param maxTokens Maximum tokens to generate
     * @param temp Sampling temperature (0.0 to 2.0)
     * @return Result containing the generated text or an exception
     */
    suspend fun generateAsync(prompt: String, maxTokens: Int = 512, temp: Double = 0.7): Result<String>

    /**
     * Generate text with a custom configuration.
     *
     * @param prompt The input prompt
     * @param config Generation configuration
     * @return Result containing the generated text or an exception
     */
    suspend fun generateWithConfig(prompt: String, config: LocalGenerationConfig): Result<String>
}

/**
 * Configuration for local text generation.
 *
 * This is a simplified configuration class that can be used across module boundaries.
 * Implementations may map this to their internal configuration structures.
 */
data class LocalGenerationConfig(
    /** Context window size (default: 2048) */
    val nCtx: Int = 2048,
    /** Number of CPU threads (0 = auto-detect) */
    val nThreads: Int = 0,
    /** Maximum tokens to generate (default: 512) */
    val maxTokens: Int = 512,
    /** Top-K sampling parameter (default: 40, 0 = disabled) */
    val topK: Int = 40,
    /** Top-P sampling parameter (default: 0.9, 0.0-1.0) */
    val topP: Float = 0.9f,
    /** Temperature for sampling (default: 0.7, 0.0-2.0) */
    val temp: Float = 0.7f,
    /** Enable PII masking for local inference (default: false) */
    val enablePiiMasking: Boolean = false
) {
    companion object {
        /** Fast generation preset - lower quality, faster response */
        val FAST = LocalGenerationConfig(
            nCtx = 1024,
            maxTokens = 128,
            topK = 20,
            topP = 0.8f,
            temp = 0.7f
        )

        /** Quality generation preset - higher quality, slower response */
        val QUALITY = LocalGenerationConfig(
            nCtx = 4096,
            maxTokens = 1024,
            topK = 60,
            topP = 0.95f,
            temp = 0.8f
        )

        /** Creative generation preset - more randomness */
        val CREATIVE = LocalGenerationConfig(
            nCtx = 2048,
            maxTokens = 512,
            topK = 0,
            topP = 0.9f,
            temp = 1.2f
        )
    }
}
