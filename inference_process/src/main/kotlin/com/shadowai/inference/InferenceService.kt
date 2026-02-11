package com.shadowai.inference

import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import com.shadowai.app.ai.IGenerationCallback as RemoteGenerationCallback
import com.shadowai.app.ai.IInferenceService as RemoteInferenceService
import com.shadowai.core.inference.InferenceIpcContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Isolated inference service running in dedicated `:inference` process.
 *
 * Implements the app-facing AIDL contract so the main process can switch local
 * inference between in-process and isolated execution.
 */
class InferenceService : Service() {

    companion object {
        private const val TAG = "InferenceService"
        private const val VERSION_CODE = 1
        private const val VERSION_NAME = "1.0.0"
    }

    private fun isDebugLogging(): Boolean = Log.isLoggable(TAG, Log.DEBUG)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val nativeBridge = NativeBridge()
    private val loadedModels = ConcurrentHashMap<String, LoadedModel>()
    private val activeGenerations = ConcurrentHashMap<String, GenerationState>()
    private val nextModelId = AtomicLong(0)

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "InferenceService created in process ${Process.myPid()}")
    }

    override fun onBind(intent: Intent): IBinder {
        Log.i(TAG, "Client bound to inference service")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "Client unbound from inference service")
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "InferenceService destroying")

        activeGenerations.values.forEach { state -> state.cancel() }
        activeGenerations.clear()

        loadedModels.values.forEach { model -> nativeBridge.unloadModel(model.handle) }
        loadedModels.clear()

        serviceScope.cancel()
    }


    /**
     * Unloads the least recently used model to free memory.
     * Called during memory pressure events.
     */
    private fun unloadLRUModel() {
        val lruModel = loadedModels.values.minByOrNull { model -> model.lastUsedAt.get() } ?: run {
            if (isDebugLogging()) Log.d(TAG, "No models loaded to unload")
            return
        }
        if (isDebugLogging()) {
            Log.d(TAG, "Selected LRU model: ${lruModel.modelId}, lastUsedAt=${lruModel.lastUsedAt.get()}")
        }
        if (loadedModels.remove(lruModel.modelId, lruModel)) {
            activeGenerations.remove(lruModel.modelId)?.cancel()
            runCatching { nativeBridge.unloadModel(lruModel.handle) }
                .onFailure { unloadError ->
                    Log.e(TAG, "Failed to unload LRU model ${lruModel.modelId}", unloadError)
                }
                .onSuccess {
                    Log.i(TAG, "Successfully unloaded LRU model: ${lruModel.modelId}")
                }
        } else {
            Log.w(TAG, "LRU model ${lruModel.modelId} was already removed")
        }
    }


    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.i(TAG, "onTrimMemory: level=$level")
        if (isDebugLogging()) {
            Log.d(TAG, "Memory stats: loadedModels=${loadedModels.size}, activeGenerations=${activeGenerations.size}")
        }
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "Critical memory pressure - unloading LRU model")
                unloadLRUModel()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                Log.i(TAG, "Low memory pressure - reducing cache sizes")
                reduceCacheSizes()
            }
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                Log.i(TAG, "Moderate memory pressure - reducing caches")
                reduceCacheSizes()
            }
        }
    }

    private val binder = object : RemoteInferenceService.Stub() {
        override fun loadModel(modelFileDescriptor: ParcelFileDescriptor, request: Bundle): Bundle {
            var descriptor: ParcelFileDescriptor? = modelFileDescriptor
            return try {
                val modelPath = resolveModelPath(descriptor)
                    ?: return failureResponse("Unable to resolve model path from descriptor")
                val modelId = request.getString(InferenceIpcContracts.Request.MODEL_ID)
                    ?.takeIf { it.isNotBlank() }
                    ?: "model_${nextModelId.getAndIncrement()}"
                val config = request.toGenerationConfig()

                val handle = nativeBridge.loadModel(modelPath, config)
                    ?: return failureResponse("Failed to load model from path: $modelPath")

                loadedModels[modelId] = LoadedModel(
                    modelId = modelId,
                    modelPath = modelPath,
                    handle = handle
                )

                Bundle().apply {
                    putBoolean(InferenceIpcContracts.Response.SUCCESS, true)
                    putString(InferenceIpcContracts.Response.MODEL_ID, modelId)
                    putLong(InferenceIpcContracts.Response.NATIVE_HANDLE, handle.nativeHandle)
                }
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Out of memory while loading model", e)
                failureResponse("Out of memory loading model")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading model", e)
                failureResponse(e.message ?: "Unknown model loading error")
            } finally {
                runCatching { descriptor?.close() }
                descriptor = null
            }
        }

        override fun unloadModel(modelId: String) {
            val model = loadedModels.remove(modelId) ?: return
            activeGenerations.remove(modelId)?.cancel()
            nativeBridge.unloadModel(model.handle)
        }

        override fun generate(request: Bundle): Bundle {
            return try {
                val modelId = request.getString(InferenceIpcContracts.Request.MODEL_ID)
                    ?.takeIf { it.isNotBlank() }
                    ?: return failureResponse("modelId is required")
                val prompt = request.getString(InferenceIpcContracts.Request.PROMPT)
                    ?.takeIf { it.isNotBlank() }
                    ?: return failureResponse("prompt is required")
                val model = loadedModels[modelId]
                    ?: return failureResponse("Model not loaded: $modelId")
                model.lastUsedAt.set(System.currentTimeMillis())
                val config = request.toGenerationConfig()

                val resultText = nativeBridge.generate(model.handle, prompt, config)
                    ?: return failureResponse("Native generation returned null")
                if (resultText.startsWith("Error:")) {
                    return failureResponse(resultText.removePrefix("Error:").trim())
                }

                val tokenCount = resultText.trim()
                    .split(Regex("\\s+"))
                    .count { token -> token.isNotBlank() }

                Bundle().apply {
                    putBoolean(InferenceIpcContracts.Response.SUCCESS, true)
                    putString(InferenceIpcContracts.Response.TEXT, resultText)
                    putInt(InferenceIpcContracts.Response.TOKENS_GENERATED, tokenCount)
                }
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Out of memory during generation", e)
                failureResponse("Out of memory during generation")
            } catch (e: Exception) {
                Log.e(TAG, "Generation failed", e)
                failureResponse(e.message ?: "Generation failed")
            }
        }

        override fun generateStream(request: Bundle, callback: RemoteGenerationCallback) {
            serviceScope.launch {
                val modelId = request.getString(InferenceIpcContracts.Request.MODEL_ID)
                    ?.takeIf { it.isNotBlank() }
                if (modelId.isNullOrBlank()) {
                    runCatching { callback.onError("modelId is required") }
                    return@launch
                }

                val prompt = request.getString(InferenceIpcContracts.Request.PROMPT)
                    ?.takeIf { it.isNotBlank() }
                if (prompt.isNullOrBlank()) {
                    runCatching { callback.onError("prompt is required") }
                    return@launch
                }

                val model = loadedModels[modelId]
                if (model == null) {
                    runCatching { callback.onError("Model not loaded: $modelId") }
                    return@launch
                }
                model.lastUsedAt.set(System.currentTimeMillis())

                val generationState = GenerationState()
                activeGenerations.remove(modelId)?.cancel()
                activeGenerations[modelId] = generationState
                val fullText = StringBuilder()
                val tokenCount = AtomicInteger(0)
                val config = request.toGenerationConfig()

                try {
                    nativeBridge.generateStream(
                        handle = model.handle,
                        prompt = prompt,
                        config = config,
                        callback = object : NativeBridge.GenerationCallback {
                            override fun onToken(token: String) {
                                if (generationState.isCancelled()) return
                                fullText.append(token)
                                tokenCount.incrementAndGet()
                                runCatching { callback.onToken(token) }
                                    .onFailure { callbackError ->
                                        Log.w(TAG, "Client callback failed on token", callbackError)
                                        generationState.cancel()
                                    }
                            }

                            override fun onCompleted() {
                                if (generationState.isCancelled()) {
                                    runCatching { callback.onError("Generation cancelled") }
                                } else {
                                    runCatching { callback.onComplete(fullText.toString()) }
                                }
                            }

                            override fun onError(message: String) {
                                runCatching { callback.onError(message) }
                            }
                        }
                    )
                } catch (streamError: Exception) {
                    Log.e(TAG, "Streaming generation failed", streamError)
                    runCatching { callback.onError(streamError.message ?: "Streaming generation failed") }
                } finally {
                    activeGenerations.remove(modelId)
                }
            }
        }

        override fun getServiceInfo(): Bundle = Bundle().apply {
            putInt(InferenceIpcContracts.ServiceInfo.VERSION_CODE, VERSION_CODE)
            putString(InferenceIpcContracts.ServiceInfo.VERSION_NAME, VERSION_NAME)
            putBoolean(InferenceIpcContracts.ServiceInfo.NATIVE_LIBRARY_LOADED, NativeBridge.isAvailable)
        }

        override fun getMemoryStats(): Bundle = Bundle().apply {
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val usedMemory = totalMemory - freeMemory

            putInt(InferenceIpcContracts.MemoryStats.TOTAL_MODELS_LOADED, loadedModels.size)
            putLong(InferenceIpcContracts.MemoryStats.TOTAL_MEMORY_USED_BYTES, usedMemory)
            putLong(InferenceIpcContracts.MemoryStats.AVAILABLE_MEMORY_BYTES, maxMemory - usedMemory)
        }

        override fun cancelGeneration(modelId: String) {
            activeGenerations.remove(modelId)?.cancel()
            loadedModels[modelId]?.let { model ->
                runCatching { nativeBridge.cancel(model.handle) }
                    .onFailure { cancelError ->
                        Log.w(TAG, "cancelGeneration failed for $modelId", cancelError)
                    }
            }
        }
    }

    private fun failureResponse(message: String): Bundle = Bundle().apply {
        putBoolean(InferenceIpcContracts.Response.SUCCESS, false)
        putString(InferenceIpcContracts.Response.ERROR_MESSAGE, message)
    }

    /**
     * Reduces cache sizes and clears buffers to free memory during low pressure.
     * Clears inference cache, reduces context buffers, and cancels active generations.
     */
    private fun reduceCacheSizes() {
        if (isDebugLogging()) {
            Log.d(TAG, "reduceCacheSizes called - activeGenerations=${activeGenerations.size}, loadedModels=${loadedModels.size}")
        }
        
        // Cancel all active generations immediately
        val activeCount = activeGenerations.size
        activeGenerations.keys.toList().forEach { modelId ->
            activeGenerations.remove(modelId)?.cancel()
            loadedModels[modelId]?.let { model ->
                runCatching { nativeBridge.cancel(model.handle) }
                    .onFailure { cancelError ->
                        Log.w(TAG, "Failed to cancel generation for $modelId", cancelError)
                    }
                    .onSuccess {
                        if (isDebugLogging()) Log.d(TAG, "Cancelled native generation for model: $modelId")
                    }
            }
            if (isDebugLogging()) Log.d(TAG, "Cancelled generation for model: $modelId")
        }
        
        // Clear any Java-level caches (empty for now - extend as needed)
        // TODO: Add cache clearing when inference cache is implemented
        
        // Suggest GC but don't force it (let the runtime decide)
        if (activeCount > 0 || loadedModels.isNotEmpty()) {
            System.gc()
            if (isDebugLogging()) Log.d(TAG, "Garbage collection suggested")
        }
        
        // Log memory stats after reduction for debug builds
        if (isDebugLogging()) {
            val runtime = Runtime.getRuntime()
            val usedMem = runtime.totalMemory() - runtime.freeMemory()
            val maxMem = runtime.maxMemory()
            Log.d(TAG, "Memory after reduction: ${usedMem/1024/1024}MB used / ${maxMem/1024/1024}MB max")
        }
        
        Log.i(TAG, "reduceCacheSizes completed - cancelled $activeCount generations, suggested GC")
    }

    private fun resolveModelPath(descriptor: ParcelFileDescriptor?): String? {
        descriptor ?: return null
        val procFdPath = "/proc/self/fd/${descriptor.fd}"
        val resolvedFile = runCatching { File(procFdPath).canonicalFile }.getOrNull() ?: return null
        return resolvedFile.absolutePath
    }

    private fun Bundle.toGenerationConfig(): NativeBridge.GenerationConfig {
        return NativeBridge.GenerationConfig(
            nCtx = getInt(InferenceIpcContracts.Request.CONTEXT_SIZE, 4096),
            nThreads = getInt(
                InferenceIpcContracts.Request.THREADS,
                Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            ),
            maxTokens = getInt(InferenceIpcContracts.Request.MAX_TOKENS, 512),
            topK = getInt(InferenceIpcContracts.Request.TOP_K, 40),
            topP = getFloat(InferenceIpcContracts.Request.TOP_P, 0.9f),
            temp = getFloat(InferenceIpcContracts.Request.TEMPERATURE, 0.7f),
            repeatPenalty = getFloat(InferenceIpcContracts.Request.REPEAT_PENALTY, 1.1f),
            batchSize = getInt(InferenceIpcContracts.Request.BATCH_SIZE, 8),
            useMmap = getBoolean(InferenceIpcContracts.Request.USE_MMAP, true),
            useMlock = getBoolean(InferenceIpcContracts.Request.USE_MLOCK, false),
            gpuLayers = getInt(InferenceIpcContracts.Request.GPU_LAYERS, 0)
        )
    }

    private data class LoadedModel(
        val modelId: String,
        val modelPath: String,
        val handle: NativeBridge.ModelHandle,
        val lastUsedAt: AtomicLong = AtomicLong(System.currentTimeMillis())
    )

    private class GenerationState {
        private val cancelled = AtomicBoolean(false)

        fun cancel() {
            cancelled.set(true)
        }

        fun isCancelled(): Boolean = cancelled.get()
    }
}
