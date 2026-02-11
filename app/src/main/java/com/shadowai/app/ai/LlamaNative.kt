package com.shadowai.app.ai

import android.util.Log
import java.io.Closeable
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong

/**
 * Kotlin wrapper for llama.cpp JNI functions.
 *
 * This class provides a clean API for loading GGUF models and generating text
 * using the native llama.cpp library.
 */
class LlamaNative {

    companion object {
        private const val TAG = "LlamaNative"
        private const val NATIVE_ERROR_PREFIX = "Error:"

        @Volatile
        private var _isAvailable = false
        val isAvailable: Boolean get() = _isAvailable

        @Volatile
        private var libraryState = LibraryState.UNINITIALIZED

        init {
            loadLibrary()
        }

        /**
         * Attempt to load the llama_jni native library.
         * Returns true if successful, false otherwise.
         */
        private fun loadLibrary(): Boolean = synchronized(this) {
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
         * Force reload the native library (useful for testing).
         * @return true if library is now available
         */
        fun reloadLibrary(): Boolean = synchronized(this) {
            libraryState = LibraryState.UNINITIALIZED
            _isAvailable = false
            return loadLibrary()
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
         * @return A LongArray containing [handle, errorMsgPtr].
         *         handle is the model handle, or 0 on failure.
         *         errorMsgPtr is a pointer to a C-string with an error message, or 0.
         */
        @JvmStatic
        external fun nativeLoadModel(
            modelPath: String,
            nCtx: Int,
            nThreads: Int
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
    }

    private fun checkAvailability() {
        if (!_isAvailable) {
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
    fun isLoaded(): Boolean {
        return _isAvailable && libraryState == LibraryState.LOADED
    }

    /**
     * Get detailed status information for debugging.
     */
    fun getStatus(): Map<String, Any> {
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
    fun loadModel(
        modelPath: String,
        config: GenerationConfig = GenerationConfig()
    ): ModelHandle? {
        checkAvailability()

        val nThreads = config.nThreads.takeIf { it > 0 }
            ?: Runtime.getRuntime().availableProcessors().let { if (it > 1) it - 1 else 1 }

        val result = nativeLoadModel(modelPath, config.nCtx, nThreads)
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

        Log.i(TAG, "Successfully loaded model from: $modelPath")
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
    fun generate(handle: ModelHandle, prompt: String, config: GenerationConfig = GenerationConfig()): String {
        checkAvailability()
        if (!handle.isValid()) {
            throw LlamaGenerationException("Invalid/freed handle")
        }

        // Medium #11: Unbounded Native String mitigation
        // Non-streaming generation with very high token counts can cause native OOM or JVM JNI string allocation failures.
        val maxTokens = if (config.maxTokens > 512) {
            Log.w(TAG, "Large maxTokens (${config.maxTokens}) requested for non-streaming generation. Capping to 512 to avoid OOM. Use generateStream for longer outputs.")
            512
        } else {
            config.maxTokens
        }

        val generated = nativeGenerate(
            handle.nativeHandle,
            prompt,
            maxTokens,
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
     * @param handle Model handle from loadModel
     * @param prompt Input prompt
     * @param config Generation configuration
     * @param callback Callback for streaming tokens
     */
    fun generateStream(
        handle: ModelHandle,
        prompt: String,
        config: GenerationConfig = GenerationConfig(),
        callback: GenerationCallback
    ) {
        checkAvailability()
        if (!handle.isValid()) {
            callback.onError("Invalid/freed handle")
            return
        }
        val weakCallback = WeakGenerationCallback(callback)
        nativeGenerateStream(
            handle.nativeHandle,
            prompt,
            config.maxTokens,
            config.topK,
            config.topP,
            config.temp,
            weakCallback
        )
    }

    // =====================
    // Helper Methods
    // =====================

    /**
     * Cancel an ongoing streaming generation for a model handle.
     *
     * @param handle Model handle from loadModel
     */
    fun cancel(handle: ModelHandle) {
        checkAvailability()
        if (handle.isValid()) {
            nativeCancel(handle.nativeHandle)
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
        val temp: Float = 0.8f
    ) {
        companion object {
            /** Fast generation preset */
            val FAST = GenerationConfig(
                nCtx = 1024,
                maxTokens = 64,
                topK = 20,
                topP = 0.8f,
                temp = 0.7f
            )

            /** Quality generation preset */
            val QUALITY = GenerationConfig(
                nCtx = 4096,
                maxTokens = 256,
                topK = 60,
                topP = 0.95f,
                temp = 0.9f
            )

            /** Creative generation preset */
            val CREATIVE = GenerationConfig(
                nCtx = 2048,
                maxTokens = 200,
                topK = 0, // disabled for more randomness
                topP = 0.9f,
                temp = 1.2f
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

    private class WeakGenerationCallback(callback: GenerationCallback) : GenerationCallback {
        private val callbackRef = WeakReference(callback)

        override fun onToken(token: String) {
            callbackRef.get()?.onToken(token)
        }

        override fun onCompleted() {
            callbackRef.get()?.onCompleted()
        }

        override fun onError(message: String) {
            callbackRef.get()?.onError(message)
        }
    }
}
