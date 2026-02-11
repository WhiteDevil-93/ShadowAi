package com.shadowai.inference

import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * JNI bridge for llama.cpp.
 * Provides native methods for loading models, generating text, and streaming.
 */
class NativeBridge {

    init {
        loadNativeLibrary()
    }

    companion object {
        private const val TAG = "LlamaNative"
        private val isLibraryLoaded = AtomicBoolean(false)

        /**
         * Check if the native library is loaded.
         */
        val isAvailable: Boolean
            get() = isLibraryLoaded.get()

        private fun loadNativeLibrary() {
            if (!isLibraryLoaded.get()) {
                try {
                    System.loadLibrary("llama_jni")
                    isLibraryLoaded.set(true)
                    Log.i(TAG, "Native library 'llama_jni' loaded successfully.")
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "Failed to load native library 'llama_jni': ${e.message}")
                    isLibraryLoaded.set(false)
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error loading native library: ${e.message}")
                    isLibraryLoaded.set(false)
                }
            }
        }
    }

    /**
     * Represents a native handle to a loaded llama.cpp model.
     * This is an opaque type that wraps a C++ pointer.
     */
    data class ModelHandle(val nativeHandle: Long, private val modelPath: String) {
        private val isValid = AtomicBoolean(true)

        fun getModelPath(): String = modelPath
        fun isValid(): Boolean = isValid.get()
        fun invalidate() = isValid.set(false)
    }

    /**
     * Configuration for native text generation.
     */
    data class GenerationConfig(
        val nCtx: Int = 2048,
        val nThreads: Int = 0,
        val maxTokens: Int = 512,
        val topK: Int = 40,
        val topP: Float = 0.9f,
        val temp: Float = 0.7f,
        val repeatPenalty: Float = 1.1f,
        val batchSize: Int = 8,
        val useMmap: Boolean = true,
        val useMlock: Boolean = false,
        val gpuLayers: Int = 0
    )

    /**
     * Model information returned from native code.
     */
    data class ModelInfo(
        val vocabSize: Int,
        val contextSize: Int,
        val embeddingSize: Int,
        val layerCount: Int,
        val headCount: Int,
        val kvHeadCount: Int,
        val modelType: String
    )

    /**
     * System information from native code.
     */
    data class SystemInfo(
        val cpuCores: Int,
        val totalMemoryMB: Long,
        val availableMemoryMB: Long,
        val hasNeon: Boolean,
        val hasFp16: Boolean,
        val hasDotProd: Boolean
    )

    /**
     * Callback interface for streaming generation.
     */
    interface GenerationCallback {
        fun onToken(token: String)
        fun onCompleted()
        fun onError(message: String)
    }

    // ==================== Native Method Declarations ====================

    private external fun nativeLoadModel(
        modelPath: String,
        contextSize: Int,
        threads: Int,
        gpuLayers: Int,
        useMmap: Boolean,
        useMlock: Boolean
    ): Long

    private external fun nativeFreeModel(handle: Long): Boolean

    private external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float
    ): String?

    private external fun nativeGenerateStream(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        callback: GenerationCallback
    ): Boolean

    private external fun nativeCancel(handle: Long): Boolean

    private external fun nativeGetModelInfo(handle: Long): ModelInfo?

    private external fun nativeGetMemoryUsage(handle: Long): Long

    private external fun nativeGetSystemInfo(): SystemInfo?

    // ==================== Public API Methods ====================

    fun loadModel(modelPath: String, config: GenerationConfig): ModelHandle? {
        if (!isAvailable) {
            Log.e(TAG, "Native library not loaded.")
            return null
        }
        if (!File(modelPath).exists()) {
            Log.e(TAG, "Model file not found: $modelPath")
            return null
        }

        return try {
            val nativeHandle = nativeLoadModel(
                modelPath = modelPath,
                contextSize = config.nCtx,
                threads = config.nThreads,
                gpuLayers = config.gpuLayers,
                useMmap = config.useMmap,
                useMlock = config.useMlock
            )

            if (nativeHandle == 0L) {
                Log.e(TAG, "Failed to load model: $modelPath")
                null
            } else {
                Log.i(TAG, "Model loaded successfully: $modelPath, handle: $nativeHandle")
                ModelHandle(nativeHandle, modelPath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception loading model: ${e.message}", e)
            null
        }
    }

    fun unloadModel(handle: ModelHandle): Boolean {
        if (!handle.isValid()) {
            Log.w(TAG, "Attempted to unload invalid model handle")
            return false
        }

        return try {
            val success = nativeFreeModel(handle.nativeHandle)
            if (success) {
                handle.invalidate()
                Log.i(TAG, "Model unloaded successfully: ${handle.getModelPath()}")
            } else {
                Log.e(TAG, "Failed to unload model: ${handle.getModelPath()}")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Exception unloading model: ${e.message}", e)
            false
        }
    }

    fun generate(handle: ModelHandle, prompt: String, config: GenerationConfig): String? {
        if (!handle.isValid()) {
            Log.e(TAG, "Model handle is invalid.")
            return null
        }

        return try {
            val result = nativeGenerate(
                handle = handle.nativeHandle,
                prompt = prompt,
                maxTokens = config.maxTokens,
                temperature = config.temp,
                topP = config.topP,
                topK = config.topK,
                repeatPenalty = config.repeatPenalty
            )

            if (result == null) {
                Log.e(TAG, "Generation failed for model: ${handle.getModelPath()}")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Exception during generation: ${e.message}", e)
            null
        }
    }

    fun generateStream(
        handle: ModelHandle,
        prompt: String,
        config: GenerationConfig,
        callback: GenerationCallback
    ): Boolean {
        if (!handle.isValid()) {
            callback.onError("Model handle is invalid for streaming.")
            return false
        }

        return try {
            val success = nativeGenerateStream(
                handle = handle.nativeHandle,
                prompt = prompt,
                maxTokens = config.maxTokens,
                temperature = config.temp,
                topP = config.topP,
                topK = config.topK,
                repeatPenalty = config.repeatPenalty,
                callback = callback
            )
            success
        } catch (e: Exception) {
            Log.e(TAG, "Exception during streaming generation: ${e.message}", e)
            callback.onError("Exception: ${e.message}")
            false
        }
    }

    fun cancel(handle: ModelHandle): Boolean {
        if (!handle.isValid()) {
            Log.w(TAG, "Attempted to cancel with invalid model handle")
            return false
        }

        return try {
            nativeCancel(handle.nativeHandle)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during cancellation: ${e.message}", e)
            false
        }
    }

    fun getModelInfo(handle: ModelHandle): ModelInfo? {
        if (!handle.isValid()) {
            Log.e(TAG, "Model handle is invalid.")
            return null
        }

        return try {
            nativeGetModelInfo(handle.nativeHandle)
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting model info: ${e.message}", e)
            null
        }
    }

    fun getMemoryUsage(handle: ModelHandle): Long {
        if (!handle.isValid()) {
            Log.e(TAG, "Model handle is invalid.")
            return 0
        }

        return try {
            nativeGetMemoryUsage(handle.nativeHandle)
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting memory usage: ${e.message}", e)
            0
        }
    }

    fun getSystemInfo(): SystemInfo? {
        if (!isAvailable) {
            Log.e(TAG, "Native library not loaded.")
            return null
        }

        return try {
            nativeGetSystemInfo()
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting system info: ${e.message}", e)
            null
        }
    }

    fun getStatus(): Map<String, Any> {
        val systemInfo = getSystemInfo()
        return mapOf(
            "isNativeLibraryLoaded" to isAvailable,
            "nativeVersion" to "llama.cpp-jni-1.0",
            "cpuCores" to (systemInfo?.cpuCores ?: 0),
            "hasNeon" to (systemInfo?.hasNeon ?: false),
            "hasFp16" to (systemInfo?.hasFp16 ?: false),
            "hasDotProd" to (systemInfo?.hasDotProd ?: false)
        )
    }
}
