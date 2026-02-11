# LocalLlama Manager Interface Design

## Overview

This document defines the interface contract needed for `LocalInferenceManager` in the `core-contracts` module to enable proper cross-module dependency injection and provider adapter integration.

## Background

The current implementation has `LocalInferenceManager` in the `:app` module, but provider-adapters need to access this functionality. The existing `LocalInferenceEngine` interface provides basic inference capabilities, but does not expose the full set of operations available in `LocalInferenceManager`.

## Current State Analysis

### LocalInferenceManager (app module)
Located at: `app/src/main/java/com/shadowai/app/ai/LocalInferenceManager.kt`

**Public Methods Identified:**
1. `isNativeAvailable: Boolean` - Check if native library is loaded
2. `setCustomModelDirectory(path: String?)` - Set custom model search path
3. `getModelDirectory(): File` - Get current model directory
4. `getAvailableModels(): List<File>` - List GGUF files in directory
5. `loadModel(modelPath: String, config: GenerationConfig): LocalModel?` - Load GGUF
6. `loadModelFromStorage(fileName: String, config: GenerationConfig): LocalModel?` - Load from app storage
7. `unloadModel(model: LocalModel)` - Unload specific model
8. `unloadAllModels()` - Unload all models
9. `isModelLoaded(modelPath: String): Boolean` - Check if model is loaded
10. `getLoadedModelCount(): Int` - Count of loaded models
11. `getNativeStatus(): Map<String, Any>` - Diagnostic info

### LocalModel (app module)
**Public Methods:**
1. `getModelPath(): String`
2. `isValid(): Boolean`
3. `getConfig(): LlamaNative.GenerationConfig`
4. `setConfig(newConfig: LlamaNative.GenerationConfig)`
5. `generate(prompt: String, config: LlamaNative.GenerationConfig?): String`
6. `generateAsync(prompt: String, config: LlamaNative.GenerationConfig?): Result<String>`
7. `generateStream(...)` - Streaming generation with callbacks
8. `generateStreamAsync(prompt, config): Result<String>` - Suspend streaming
9. `close()` - Release resources

### Existing core-contracts Interfaces

**LocalInferenceEngine:**
- `isNativeAvailable: Boolean`
- `loadModel(modelPath, config): LocalModelHandle?`
- `unloadModel(model: LocalModelHandle)`
- `isModelLoaded(modelPath: String): Boolean`
- `getAvailableModels(): List<String>`

**ProviderExecutor:**
- `providerId: ProviderId`
- `isAvailable(): Boolean`
- `canExecute(transform: Transform): Boolean`
- `execute(transform, input, parameters): Result<Any>`
- `getPriority(transform): Int`

## Design Decisions

### 1. Interface Hierarchy

The interface should be a **separate, extended interface** that extends `LocalInferenceEngine`, not `ProviderExecutor`. This is because:

- `ProviderExecutor` is a generic execution interface for all providers
- Local inference has unique concerns (model files, memory management, native status)
- `LocalInferenceEngine` should remain focused on basic inference
- `LocalInferenceManager` needs management operations that don't fit `ProviderExecutor`

### 2. Naming

**Option A: `LocalInferenceManager`** - Consistent with app module name
**Option B: `LocalInferenceContract`** - Emphasizes this is a contract, not implementation
**Option C: `LocalLlamaManager`** - Specific to llama.cpp implementation

**Decision:** Use `LocalInferenceManager` as the interface name. The implementation in the app module will become `LocalInferenceManagerImpl`, following the pattern used throughout ShadowAi.

### 3. Return Types

Follow core-contracts patterns:
- **Sealed results:** Use `kotlin.Result<>` for async operations
- **Suspend functions:** For IO-heavy operations (load/unload/is checks)
- **Plain functions:** For lightweight accessors (getters, counts)
- **Callback-based:** For streaming APIs to avoid coroutine complexity in JNI

### 4. Model Handle vs Model Reference

The existing `LocalModelHandle` is too limited - it only exposes `generateAsync` and `generateWithConfig`. The app module's `LocalModel` has:
- Configuration management (getConfig, setConfig)
- Both blocking and async generation
- Streaming APIs (callback and async)
- Resource management (close)

We need to extend `LocalModelHandle` with these capabilities without breaking existing implementations.

### 5. Android Dependencies

The interface must NOT expose Android-specific types (Context, File, Handler) to maintain cross-module portability:
- Replace `File` with `String` (paths)
- Replace `Handler` callbacks with custom callback interfaces
- Remove Context dependencies from interface

## Proposed Interface Design

### Extended Configuration

```kotlin
package com.shadowai.core

/**
 * Extended generation configuration for local models.
 * Includes all parameters supported by llama.cpp.
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
    val temperature: Float = 0.7f,
    /** Presence penalty (default: 0.0) */
    val presencePenalty: Float = 0.0f,
    /** Frequency penalty (default: 0.0) */
    val frequencyPenalty: Float = 0.0f,
    /** Repeat penalty (default: 1.1) */
    val repeatPenalty: Float = 1.1f,
    /** Batch size for prompt processing (default: 512) */
    val batchSize: Int = 512,
    /** Whether to use memory mapping (default: true) */
    val useMmap: Boolean = true,
    /** Whether to lock model in memory (default: false) */
    val useMlock: Boolean = false
) {
    companion object {
        val FAST = LocalGenerationConfig(
            nCtx = 1024, maxTokens = 128, topK = 20, topP = 0.8f, temperature = 0.7f
        )
        val QUALITY = LocalGenerationConfig(
            nCtx = 4096, maxTokens = 1024, topK = 60, topP = 0.95f, temperature = 0.8f
        )
        val CREATIVE = LocalGenerationConfig(
            nCtx = 2048, maxTokens = 512, topK = 0, topP = 0.9f, temperature = 1.2f
        )
    }
}
```

### Extended Model Handle

```kotlin
package com.shadowai.core

/**
 * Extended handle to a loaded local model.
 * Provides full generation capabilities including streaming.
 */
interface LocalModelHandle : AutoCloseable {
    /** Get the model path that was used to load this model. */
    fun getModelPath(): String

    /** Check if this handle is still valid (model is loaded and ready). */
    fun isValid(): Boolean

    /** Get current generation configuration. */
    fun getConfig(): LocalGenerationConfig

    /** Update generation configuration for subsequent operations. */
    fun setConfig(config: LocalGenerationConfig)

    /**
     * Generate text from a prompt (blocking).
     * Use for simple, synchronous operations only.
     * For production code, prefer generateAsync.
     *
     * @param prompt The input prompt
     * @param config Optional override configuration
     * @return Generated text, or error message starting with "Error:"
     */
    fun generate(prompt: String, config: LocalGenerationConfig? = null): String

    /**
     * Generate text from a prompt (async).
     *
     * @param prompt The input prompt
     * @param config Optional override configuration
     * @return Result containing generated text or exception
     */
    suspend fun generateAsync(
        prompt: String,
        config: LocalGenerationConfig? = null
    ): Result<String>

    /**
     * Generate text with streaming output.
     * Callbacks are invoked on a background thread - handle UI updates appropriately.
     *
     * @param prompt The input prompt
     * @param config Optional override configuration
     * @param onToken Called for each generated token
     * @param onComplete Called when generation completes successfully
     * @param onError Called if generation fails
     */
    fun generateStream(
        prompt: String,
        config: LocalGenerationConfig? = null,
        onToken: (String) -> Unit = {},
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    )

    /**
     * Generate text with streaming output (async/coroutine version).
     * Supports cancellation via coroutine cancellation.
     *
     * @param prompt The input prompt
     * @param config Optional override configuration
     * @return Result containing complete generated text or exception
     */
    suspend fun generateStreamAsync(
        prompt: String,
        config: LocalGenerationConfig? = null
    ): Result<String>

    /** Release resources associated with this model handle. */
    override fun close()
}
```

### Streaming Callback Interface

```kotlin
package com.shadowai.core

/**
 * Callback interface for streaming generation.
 * Used when integration with coroutines is not desired or needed.
 */
interface GenerationCallback {
    /** Called when a new token is generated. */
    fun onToken(token: String)

    /** Called when generation completes successfully. */
    fun onCompleted()

    /** Called when generation fails. */
    fun onError(message: String)
}
```

### Manager Interface

```kotlin
package com.shadowai.core

/**
 * Extended interface for local LLM inference management.
 * 
 * This interface extends the basic [LocalInferenceEngine] with management operations
 * for model lifecycle, discovery, and diagnostics. It provides a complete contract
 * for the app's LocalInferenceManager without exposing Android-specific types.
 *
 * Implementations (in the :app module) handle:
 * - Model loading from file paths
 * - Memory management and lifecycle
 * - Native library initialization
 * - Thread safety and synchronization
 *
 * Usage:
 * ```
 * class LocalLlamaAdapter @Inject constructor(
 *     private val manager: LocalInferenceManager,
 *     ...
 * ) : ProviderAdapter {
 *     override suspend fun execute(prompt: String): String {
 *         val model = manager.loadModel("/path/to/model.gguf")
 *             ?: throw ModelLoadException()
 *         
 *         return model.use {
 *             it.generateAsync(prompt).getOrThrow()
 *         }
 *     }
 * }
 * ```
 */
interface LocalInferenceManager : LocalInferenceEngine {

    // =================================================================
    // Model Discovery and Configuration
    // =================================================================

    /**
     * Set a custom directory for model discovery.
     * Path must be within application's allowed storage boundaries.
     *
     * @param path Absolute path to directory, or null to reset to default
     * @throws IllegalArgumentException if path is outside allowed boundaries
     */
    fun setCustomModelDirectory(path: String?)

    /**
     * Get the currently configured model directory path.
     * 
     * @return Absolute path to the model directory
     */
    fun getModelDirectory(): String

    /**
     * Get list of available model files in the configured directory.
     * Only .gguf files are returned, sorted by last modified (newest first).
     *
     * @return List of absolute paths to available GGUF model files
     */
    override suspend fun getAvailableModels(): List<String>

    // =================================================================
    // Model Loading and Lifecycle
    // =================================================================

    /**
     * Load a GGUF model from the specified path.
     * Model must be located within allowed storage boundaries.
     * If the model is already loaded, returns a new handle to the cached instance.
     *
     * @param modelPath Absolute path to the GGUF model file
     * @param config Generation configuration
     * @return Model handle, or null if loading failed
     */
    override suspend fun loadModel(
        modelPath: String,
        config: LocalGenerationConfig
    ): LocalModelHandle?

    /**
     * Load a model from the configured model directory by filename.
     *
     * @param fileName Name of the GGUF file (NOT full path)
     * @param config Generation configuration
     * @return Model handle, or null if file not found or loading failed
     */
    suspend fun loadModelFromStorage(
        fileName: String,
        config: LocalGenerationConfig = LocalGenerationConfig()
    ): LocalModelHandle?

    /**
     * Unload a specific model and release its resources.
     * After unloading, the handle becomes invalid.
     *
     * @param model The model handle to unload
     */
    override suspend fun unloadModel(model: LocalModelHandle)

    /**
     * Unload all loaded models and release all resources.
     * Use when shutting down or for memory pressure response.
     */
    suspend fun unloadAllModels()

    // =================================================================
    // Model State Queries
    // =================================================================

    /**
     * Check if a specific model is currently loaded.
     *
     * @param modelPath The path that was used to load the model
     * @return true if model is loaded and valid
     */
    override suspend fun isModelLoaded(modelPath: String): Boolean

    /**
     * Get the number of currently loaded models.
     * Includes only valid (not freed) model handles.
     *
     * @return Count of loaded models
     */
    fun getLoadedModelCount(): Int

    // =================================================================
    // Diagnostics and Status
    // =================================================================

    /**
     * Get diagnostic information about the native library and loaded models.
     * Useful for debugging and health checks.
     *
     * @return Map containing diagnostic information including:
     *         - nativeAvailable: Boolean
     *         - version: String (llama.cpp version)
         *         - loadedModels: Int
     *         - modelDirectory: String
     */
    fun getNativeStatus(): Map<String, Any>
}
```

### Result Types (Sealed Classes)

Following core-contracts patterns, we should define sealed result types for better error handling:

```kotlin
package com.shadowai.core

/**
 * Sealed hierarchy for inference results.
 * Provides structured error information.
 */
sealed class InferenceResult<out T> {
    data class Success<T>(val data: T) : InferenceResult<T>()
    sealed class Error : InferenceResult<Nothing>() {
        data class ModelLoadFailed(val path: String, val reason: String) : Error()
        data class ModelNotFound(val path: String) : Error()
        data class GenerationFailed(val reason: String) : Error()
        data class InvalidHandle(val reason: String) : Error()
        data class NativeError(val code: Int, val message: String) : Error()
        data class StorageError(val reason: String) : Error()
        data class Cancelled(val reason: String = "Generation was cancelled") : Error()
        data class Unknown(val exception: Throwable) : Error()
    }

    fun toResult(): Result<T> = when (this) {
        is Success -> Result.success(data)
        is Error -> Result.failure(InferenceException(this))
    }
}

/**
 * Exception wrapper for inference errors.
 */
class InferenceException(val error: InferenceResult.Error) : Exception(
    when (error) {
        is InferenceResult.Error.ModelLoadFailed -> "Failed to load model: ${error.path} - ${error.reason}"
        is InferenceResult.Error.ModelNotFound -> "Model not found: ${error.path}"
        is InferenceResult.Error.GenerationFailed -> "Generation failed: ${error.reason}"
        is InferenceResult.Error.InvalidHandle -> "Invalid model handle: ${error.reason}"
        is InferenceResult.Error.NativeError -> "Native error ${error.code}: ${error.message}"
        is InferenceResult.Error.StorageError -> "Storage error: ${error.reason}"
        is InferenceResult.Error.Cancelled -> error.reason
        is InferenceResult.Error.Unknown -> "Unknown error: ${error.exception.message}"
    }
)
```

## Migration Strategy

### Step 1: Add interfaces to core-contracts
Add the interfaces above to `core-contracts/src/main/kotlin/com/shadowai/core/`

### Step 2: Update existing LocalInferenceEngine
- Keep `LocalInferenceEngine` for backward compatibility
- Mark it as deprecated, pointing to `LocalInferenceManager`
- Or keep both: `LocalInferenceEngine` as minimal, `LocalInferenceManager` as extended

### Step 3: Rename app module implementation
```kotlin
// app/src/main/java/com/shadowai/app/ai/LocalInferenceManagerImpl.kt
class LocalInferenceManagerImpl(context: Context) : LocalInferenceManager {
    // Implementation using existing code
}
```

### Step 4: Update Dagger bindings
```kotlin
// In AppBindings.kt or AiServicesModule.kt
@Binds
abstract fun bindLocalInferenceManager(
    impl: LocalInferenceManagerImpl
): LocalInferenceManager
```

### Step 5: Update provider-adapters
```kotlin
// In LocalLlamaAdapter.kt
class LocalLlamaAdapter @Inject constructor(
    private val manager: LocalInferenceManager,
    private val modelCatalog: ModelCatalog
) : ProviderAdapter {
    // Use manager.loadModel, manager.getAvailableModels(), etc.
}
```

## Adapter Implementation Example

Example of how provider-adapters would use this interface:

```kotlin
package com.shadowai.provideradapters.local

import com.shadowai.core.*
import javax.inject.Inject

class LocalLlamaProviderAdapter @Inject constructor(
    private val manager: LocalInferenceManager,
    private val modelRegistry: ModelRegistry
) : ProviderAdapter {

    override val providerId: ProviderId = ProviderId.LOCAL_LLAMA

    override suspend fun isAvailable(): Boolean {
        return manager.isNativeAvailable
    }

    override suspend fun canExecute(transform: Transform): Boolean {
        return when (transform) {
            is Transform.TextToText -> true
            else -> false // Local models currently only support text
        }
    }

    override suspend fun execute(
        transform: Transform,
        input: Any,
        parameters: Map<String, Any>
    ): Result<Any> {
        // Get model path from parameters or use default
        val modelPath = parameters["model_path"] as? String
            ?: return Result.failure(IllegalArgumentException("model_path required"))

        // Load model with appropriate config
        val config = parameters["config"] as? LocalGenerationConfig 
            ?: LocalGenerationConfig.QUALITY

        val model = manager.loadModel(modelPath, config)
            ?: return Result.failure(IllegalStateException("Failed to load model: $modelPath"))

        return model.use { handle ->
            when (transform) {
                is Transform.TextToText -> {
                    val prompt = input as String
                    handle.generateAsync(prompt)
                }
                else -> Result.failure(UnsupportedOperationException("Transform not supported: $transform"))
            }
        }
    }

    override fun getPriority(transform: Transform): Int {
        return when (transform) {
            is Transform.TextToText -> 100 // High priority for local text
            else -> 0
        }
    }

    /**
     * Local-specific: List available models on device.
     */
    suspend fun listLocalModels(): List<ModelDescriptor> {
        return manager.getAvailableModels().map { path ->
            ModelDescriptor(
                id = path.substringAfterLast("/"),
                name = path.substringAfterLast("/").removeSuffix(".gguf"),
                providerId = providerId,
                capabilities = setOf(Capability.TEXT, Capability.STREAMING),
                supportedTransforms = setOf(Transform.TextToText())
            )
        }
    }
}
```

## Summary

This interface design:

1. **Extends `LocalInferenceEngine`** without breaking existing code
2. **Follows core-contracts patterns** (sealed results, coroutines, suspend functions)
3. **Removes Android dependencies** from the interface (no Context, File, Handler)
4. **Exposes all management operations** needed by provider-adapters
5. **Maintains type safety** with proper model handles
6. **Enables dependency injection** across module boundaries
7. **Supports streaming** via both callbacks and coroutines

The implementation in the `:app` module would be renamed to `LocalInferenceManagerImpl` and simply bind to this interface, enabling provider-adapters to use local inference without coupling to app internals.
