package com.shadowai.app.ai

/**
 * Interface for Llama generation engine.
 *
 * Abstracts the concrete LlamaNative implementation to avoid circular
 * dependencies in the DI graph.
 */
interface ILlamaEngine {
    /**
     * Check if the native library is loaded and available.
     */
    fun isLoaded(): Boolean

    /**
     * Attempt to load the native library.
     */
    fun loadLibraryIfNeeded(): Boolean

    /**
     * Get detailed status information for debugging.
     */
    fun getStatus(): Map<String, Any>

    /**
     * Attempt to load a GGUF model with automatic thread detection.
     *
     * @param modelPath Absolute path to the model file
     * @param config Generation configuration
     * @return ModelHandle or null if loading failed
     */
    fun loadModel(
        modelPath: String,
        config: LlamaNative.GenerationConfig = LlamaNative.GenerationConfig()
    ): LlamaNative.ModelHandle?

    /**
     * Generate text from a prompt using a loaded model.
     *
     * @param handle Model handle from loadModel
     * @param prompt Input prompt
     * @param config Generation configuration
     * @return Generated text
     */
    fun generate(
        handle: LlamaNative.ModelHandle,
        prompt: String,
        config: LlamaNative.GenerationConfig = LlamaNative.GenerationConfig()
    ): String

    /**
     * Generate text with streaming callback.
     *
     * @param handle Model handle from loadModel
     * @param prompt Input prompt
     * @param config Generation configuration
     * @param callback Callback for streaming tokens
     */
    fun generateStream(
        handle: LlamaNative.ModelHandle,
        prompt: String,
        config: LlamaNative.GenerationConfig = LlamaNative.GenerationConfig(),
        callback: LlamaNative.GenerationCallback
    )

    /**
     * Cancel an ongoing streaming generation for a model handle.
     *
     * @param handle Model handle from loadModel
     */
    fun cancel(handle: LlamaNative.ModelHandle)
}
