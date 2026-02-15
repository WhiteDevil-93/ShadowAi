package com.shadowai.app.ai

import android.util.Log
import java.io.Closeable
// H-3: Removed WeakReference import - now using LifecycleManagedCallback with strong references
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kotlin wrapper for llama.cpp JNI functions.
 *
 * This class provides a clean API for loading GGUF models and generating text
 * using the native llama.cpp library.
 *
 * Implements ILlamaEngine to allow dependency injection without circular dependencies.
 */
class LlamaNative @Inject constructor() : ILlamaEngine {

    companion object {
        private const val TAG = "LlamaNative"
        private const val NATIVE_ERROR_PREFIX = "Error:"

        @Volatile
        private var _isAvailable = false
        val isAvailable: Boolean get() = _isAvailable

        @Volatile
        private var libraryState = LibraryState.UNINITIALIZED

        init {
            // Note: We don't load library in static initializer anymore
            // This prevents wrong process context issues with isolated inference
            // Library loading is now handled by LocalInferenceManager
            Log.d(TAG, "LlamaNative companion initialized (library not yet loaded)")
        }

        /**
         * Attempt to load the llama_jni native library.
         * Returns true if successful, false otherwise.
         *
         * This method is now called explicitly rather than in static initializer
         * to ensure proper process context for isolated inference.
         */
        fun loadLibraryIfNeeded(): Boolean = synchronized(this) {
            when (libraryState) {
                LibraryState.LOADED -> return true
                LibraryState.FAILED -> return false
                LibraryState.UNINITIALIZED -> {
                    try {
                        System.loadLibrary("llama_jni")
                        _isAvailable = true
                        libraryState = LibraryState.LOADED
                        Log.i(TAG, "Successfully loaded llama_jni native library")
                        Log.i(TAG, "System Info: ${nativeGetSystemInfo()}")
                    } catch (e: UnsatisfiedLinkError) {
                        _isAvailable = false
                        libraryState = LibraryState.FAILED
                        Log.e(TAG, "Failed to load llama_jni native library: ${e.message}")
                        Log.i(
                            TAG,
                            "Local inference will use simulated responses. " +
                                "Ensure llama_jni.so is built and in the APK."
                        )
                    } catch (e: Exception) {
                        _isAvailable = false
                        libraryState = LibraryState.FAILED
                        Log.e(TAG, "Unexpected error loading llama_jni: ${e.message}")
                    }
                }
            }
            return _isAvailable
        }

        /**
         * Force reload the native library (useful for testing or process context switching).
         * @return true if library is now available
         *
         * Note: Android does not support unloading native libraries. This method
         * resets state tracking but the native library remains loaded. For isolated
         * inference processes, a new process is created with its own library instance.
         */
        fun reloadLibrary(): Boolean = synchronized(this) {
            libraryState = LibraryState.UNINITIALIZED
            _isAvailable = false
            return loadLibraryIfNeeded()
        }

        private enum class LibraryState {
            UNINITIALIZED,
            LOADED,
            FAILED
        }

        // =====================
        // JNI Native Methods
        // =====================
        // NOTE: Using @JvmStatic in companion object avoids Kotlin's name mangling
        // This ensures the JNI name is stable: Java_com_shadowai_app_ai_LlamaNative_nativeLoadModel
        // Without @JvmStatic, debug builds add $app_debug suffix causing UnsatisfiedLinkError

        /**
         * Get system information from native code.
         */
        @JvmStatic
        external fun nativeGetSystemInfo(): String

        /**
         * Load a GGUF model file.
         *
         * @param modelPath Absolute path to the model file
         * @param nCtx Context size (default 2048)
         * @param nThreads Number of threads
         * @param useNnapi Whether to use NNAPI delegation for NPU acceleration
         * @param useMmap Whether to use memory-mapped file loading
         * @return A LongArray containing [handle, errorMsgPtr].
         *         handle is the model handle, or 0 on failure.
         *         errorMsgPtr is a pointer to a C-string with an error message, or 0.
         */
        @JvmStatic
        external fun nativeLoadModel(
            modelPath: String,
            nCtx: Int,
            nThreads: Int,
            useNnapi: Boolean,
            useMmap: Boolean
        ): LongArray

        /**
         * Free a loaded model.
         *
         * @param handle Model handle from nativeLoadModel
         */
        @JvmStatic
        external fun nativeFreeModel(handle: Long)

        /**
         * Get a string from a native pointer.
         *
         * @param ptr Pointer to the C-string
         * @return The string, or null
         */
        @JvmStatic
        external fun nativeGetString(ptr: Long): String?

        /**
         * Free a string pointer from native code.
         *
         * @param ptr Pointer to the C-string
         */
        @JvmStatic
        external fun nativeFreeString(ptr: Long)


        /**
         * Generate text from a prompt.
         *
         * @param handle Model handle from nativeLoadModel
         * @param prompt Input prompt
         * @param maxTokens Maximum tokens to generate
         * @param topK Top-K sampling parameter
         * @param topP Top-P sampling parameter
         * @param temp Temperature for sampling
         * @return Generated text, or error message starting with "Error:"
         */
        @JvmStatic
        external fun nativeGenerate(
            handle: Long,
            prompt: String,
            maxTokens: Int,
            topK: Int,
            topP: Float,
            temp: Float
        ): String

        /**
         * Generate text with streaming callback.
         *
         * @param handle Model handle from nativeLoadModel
         * @param prompt Input prompt
         * @param maxTokens Maximum tokens to generate
         * @param topK Top-K sampling parameter
         * @param topP Top-P sampling parameter
         * @param temp Temperature for sampling
         * @param callback Callback for streaming tokens
         */
        @JvmStatic
        external fun nativeGenerateStream(
            handle: Long,
            prompt: String,
            maxTokens: Int,
            topK: Int,
            topP: Float,
            temp: Float,
            callback: GenerationCallback
        )

        /**
         * Cancel an ongoing streaming generation.
         *
         * @param handle Model handle from nativeLoadModel
         */
        @JvmStatic
        external fun nativeCancel(handle: Long)

        /**
         * Validate a model file without loading it.
         *
         * @param modelPath Absolute path to the model file
         * @return true if valid GGUF file, false otherwise
         */
        @JvmStatic
        external fun nativeValidateModel(modelPath: String): Boolean

        /**
         * Get model information as JSON string.
         *
         * @param handle Model handle from nativeLoadModel
         * @return JSON string with model info
         */
        @JvmStatic
        external fun nativeGetModelInfo(handle: Long): String

        /**
         * Get performance metrics for the loaded model.
         *
         * @param handle Model handle from nativeLoadModel
         * @return LongArray with metrics [context_size, threads, batch_size, ubatch_size]
         */
        @JvmStatic
        external fun nativeGetPerformanceMetrics(handle: Long): LongArray

        /**
         * Check if a model is loaded.
         *
         * @param handle Model handle from nativeLoadModel
         * @return true if model is loaded and ready
         */
        @JvmStatic
        external fun nativeIsModelLoaded(handle: Long): Boolean

        /**
         * Get the last error message.
         *
         * @return Error message string or "No error"
         */
        @JvmStatic
        external fun nativeGetLastError(): String
    }

    private fun checkAvailability() {
        // Try to load the library if not already loaded
        if (!isLoaded()) {
            loadLibraryIfNeeded()
        }
        if (!isLoaded()) {
            throw IllegalStateException(
                "Native library 'llama_jni' is not available. " +
                "Check if the native library is properly built and included in the APK. " +
                "Ensure llama_jni.so is compiled for the target architecture."
            )
        }
    }

    /**
     * Check if the native library is loaded and available.
     */
    override fun isLoaded(): Boolean {
        return _isAvailable && libraryState == LibraryState.LOADED
    }

    /**
     * Attempt to load the native library.
     */
    override fun loadLibraryIfNeeded(): Boolean {
        return LlamaNative.loadLibraryIfNeeded()
    }

    /**
     * Get detailed status information for debugging.
     */
    override fun getStatus(): Map<String, Any> {
        return mapOf(
            "isAvailable" to _isAvailable,
            "libraryLoaded" to (libraryState == LibraryState.LOADED),
            "libraryState" to libraryState.name,
            "libraryPath" to (System.getProperty("java.library.path") ?: "unknown"),
            "systemInfo" to if (_isAvailable) nativeGetSystemInfo() else "N/A"
        )
    }

    /**
     * Attempt to load a GGUF model with automatic thread detection.
     *
     * @param modelPath Absolute path to the model file
     * @param config Generation configuration
     * @return ModelHandle or null if loading failed
     */
    override fun loadModel(
        modelPath: String,
        config: GenerationConfig
    ): ModelHandle? {
        checkAvailability()

        val nThreads = config.nThreads.takeIf { it > 0 }
            ?: Runtime.getRuntime().availableProcessors().let { if (it > 1) it - 1 else 1 }

        val result = nativeLoadModel(
            modelPath,
            config.nCtx,
            nThreads,
            config.useNnapi,
            config.useMmap
        )
        if (result.size < 2) {
            Log.e(TAG, "nativeLoadModel returned malformed result: size=${result.size}")
            return null
        }
        val handle = result.getOrNull(0) ?: 0L
        val errorPtr = result.getOrNull(1) ?: 0L

        if (handle == 0L) {
            val errorMessage = if (errorPtr != 0L) {
                try {
                    nativeGetString(errorPtr)
                } finally {
                    nativeFreeString(errorPtr)
                }
            } else {
                "Unknown native error"
            }
            Log.e(TAG, "Failed to load model from: $modelPath. Reason: $errorMessage")
            return null
        }

        val mmapStatus = if (config.useMmap) "memory-mapped" else "fully loaded"
        val nnapiStatus = if (config.useNnapi) "with NNAPI" else "CPU-only"
        Log.i(TAG, "Successfully loaded model from: $modelPath ($mmapStatus, $nnapiStatus)")
        return ModelHandle(handle, modelPath)
    }

    /**
     * Generate text from a prompt using a loaded model.
     *
     * @param handle Model handle from loadModel
     * @param prompt Input prompt
     * @param config Generation configuration
     * @return Generated text, or error message starting with "Error:"
     */
    override fun generate(handle: ModelHandle, prompt: String, config: GenerationConfig): String {
        checkAvailability()
        if (!handle.isValid()) {
            throw LlamaGenerationException("Invalid/freed handle")
        }

        // H-4 FIX: Fail fast instead of silently truncating output length.
        // Callers can switch to generateStream for long outputs.
        if (config.maxTokens > 512) {
            throw TokenLimitException(
                message = "maxTokens=${config.maxTokens} exceeds non-streaming limit (512). Use generateStream for larger outputs.",
                requestedTokens = config.maxTokens,
                maxContext = 512
            )
        }

        val generated = nativeGenerate(
            handle.nativeHandle,
            prompt,
            config.maxTokens,
            config.topK,
            config.topP,
            config.temp
        )
        if (generated.startsWith(NATIVE_ERROR_PREFIX)) {
            throw LlamaGenerationException(generated.removePrefix(NATIVE_ERROR_PREFIX).trim())
        }
        return generated
    }

    /**
     * Generate text with streaming callback.
     *
     * H-3 FIX: Uses strong reference with lifecycle management instead of WeakReference.
     * The callback is held strongly but automatically cleaned up when:
     * 1. Generation completes (onCompleted/onError called)
     * 2. Explicit cancellation via cancel()
     * 3. Model handle is freed
     *
     * @param handle Model handle from loadModel
     * @param prompt Input prompt
     * @param config Generation configuration
     * @param callback Callback for streaming tokens - held with strong reference
     */
    override fun generateStream(
        handle: ModelHandle,
        prompt: String,
        config: GenerationConfig,
        callback: GenerationCallback
    ) {
        checkAvailability()
        if (!handle.isValid()) {
            callback.onError("Invalid/freed handle")
            return
        }

        // H-3: Use LifecycleManagedCallback instead of WeakReference
        val managedCallback = LifecycleManagedCallback(handle, callback) { activeCallbacks }
        callbackMutex.lock()
        try {
            activeCallbacks[handle.nativeHandle] = managedCallback
        } finally {
            callbackMutex.unlock()
        }

        nativeGenerateStream(
            handle.nativeHandle,
            prompt,
            config.maxTokens,
            config.topK,
            config.topP,
            config.temp,
            managedCallback
        )
    }

    @Volatile
    private var activeCallbacks = java.util.concurrent.ConcurrentHashMap<Long, LifecycleManagedCallback>()
    private val callbackMutex = java.util.concurrent.locks.ReentrantLock()

    // =====================
    // Helper Methods
    // =====================

    /**
     * Cancel an ongoing streaming generation for a model handle.
     *
     * H-3: Also removes the callback from the active callbacks map to prevent
     * memory leaks and ensure proper lifecycle cleanup.
     *
     * @param handle Model handle from loadModel
     */
    override fun cancel(handle: ModelHandle) {
        checkAvailability()
        if (handle.isValid()) {
            nativeCancel(handle.nativeHandle)

            // H-3: Clean up the callback
            callbackMutex.lock()
            try {
                activeCallbacks.remove(handle.nativeHandle)
            } finally {
                callbackMutex.unlock()
            }
        }
    }

    /**
     * H-3: Lifecycle-managed callback that holds strong reference to the actual callback
     * but automatically cleans up when generation ends or is cancelled.
     */
    private inner class LifecycleManagedCallback(
        private val handle: ModelHandle,
        private val actualCallback: GenerationCallback,
        private val activeCallbackMap: () -> MutableMap<Long, LifecycleManagedCallback>
    ) : GenerationCallback {
        @Volatile
        private var isCompleted = false

        override fun onToken(token: String) {
            if (!isCompleted) {
                actualCallback.onToken(token)
            }
        }

        override fun onCompleted() {
            if (!isCompleted) {
                isCompleted = true
                actualCallback.onCompleted()
                cleanup()
            }
        }

        override fun onError(message: String) {
            if (!isCompleted) {
                isCompleted = true
                actualCallback.onError(message)
                cleanup()
            }
        }

        private fun cleanup() {
            callbackMutex.lock()
            try {
                activeCallbackMap().remove(handle.nativeHandle)
            } finally {
                callbackMutex.unlock()
            }
        }
    }

    // =====================
    // Helper Classes
    // =====================

    /**
     * Handle to a loaded model. Use [free] to release resources when done.
     */
    class ModelHandle internal constructor(
        handle: Long,
        private val modelPath: String
    ) : Closeable {
        private val nativeHandleRef = AtomicLong(handle)
        internal val nativeHandle: Long
            get() = nativeHandleRef.get()

        /**
         * Free the model resources. Safe to call multiple times (idempotent).
         */
        fun free() {
            val current = nativeHandleRef.getAndSet(0L)
            if (current != 0L) {
                try {
                    nativeFreeModel(current)
                    Log.d(TAG, "Freed model: $modelPath")
                } catch (e: Exception) {
                    Log.e(TAG, "Error freeing model: ${e.message}")
                }
            }
        }

        override fun close() {
            free()
        }

        /**
         * Check if this handle is valid.
         */
        fun isValid(): Boolean = nativeHandleRef.get() != 0L

        /**
         * Get the model path.
         */
        fun getModelPath(): String = modelPath
    }

    /**
     * Configuration for text generation.
     */
    data class GenerationConfig(
        /** Context window size (default: 2048) */
        val nCtx: Int = 2048,
        /** Number of CPU threads (0 = auto-detect) */
        val nThreads: Int = 0,
        /** Maximum tokens to generate (default: 128) */
        val maxTokens: Int = 128,
        /** Top-K sampling parameter (default: 40, 0 = disabled) */
        val topK: Int = 40,
        /** Top-P sampling parameter (default: 0.9, 0.0-1.0) */
        val topP: Float = 0.9f,
        /** Temperature for sampling (default: 0.8, 0.0-2.0) */
        val temp: Float = 0.8f,
        /** Use NNAPI delegation for NPU acceleration (default: false) */
        val useNnapi: Boolean = false,
        /** Use memory-mapped file loading (default: true) */
        val useMmap: Boolean = true
    ) {
        companion object {
            /** Fast generation preset */
            val FAST = GenerationConfig(
                nCtx = 1024,
                maxTokens = 64,
                topK = 20,
                topP = 0.8f,
                temp = 0.7f,
                useNnapi = false,
                useMmap = true
            )

            /** Quality generation preset */
            val QUALITY = GenerationConfig(
                nCtx = 4096,
                maxTokens = 256,
                topK = 60,
                topP = 0.95f,
                temp = 0.9f,
                useNnapi = true,
                useMmap = true
            )

            /** Creative generation preset */
            val CREATIVE = GenerationConfig(
                nCtx = 2048,
                maxTokens = 200,
                topK = 0, // disabled for more randomness
                topP = 0.9f,
                temp = 1.2f,
                useNnapi = false,
                useMmap = false
            )
        }
    }

    /**
     * Callback interface for streaming generation.
     */
    interface GenerationCallback {
        fun onToken(token: String)
        fun onCompleted()
        fun onError(message: String)
    }

    class LlamaGenerationException(message: String) : RuntimeException(message)
    // H-3: Removed WeakGenerationCallback - now using LifecycleManagedCallback with strong references
}

/**
 * H-4: Token limit exception for generation context overflow.
 * Thrown when input would be silently truncated by the native layer.
 */
class TokenLimitException(
    message: String,
    val requestedTokens: Int,
    val maxContext: Int
) : IllegalArgumentException(message)
