package com.shadowai.app.ai

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.shadowai.core.LocalInferenceEngine
import com.shadowai.core.LocalGenerationConfig
import com.shadowai.core.LocalModelHandle
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.resume
import com.shadowai.core.security.PiiMaskingProcessor
import com.shadowai.app.auth.UserPreferences
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalInferenceManager @Inject constructor(
    @ApplicationContext context: Context,
    internal val piiMaskingProcessor: PiiMaskingProcessor,
    private val llamaEngine: ILlamaEngine,
    private val userPreferences: UserPreferences
) : LocalInferenceEngine, ModelTreeUriConfigurable, InferenceEngineControl {
    private val context = context.applicationContext

    companion object {
        private const val TAG = "LocalInference"
        internal const val DEFAULT_MODEL_DIR = "models"
    }

    internal val llamaNative: ILlamaEngine get() = llamaEngine

    override val isNativeAvailable: Boolean get() = llamaEngine.isLoaded()

    suspend fun ensureLibraryLoaded(): Boolean {
        return llamaEngine.loadLibraryIfNeeded()
    }

    override suspend fun warmup(): Result<Unit> {
        return try {
            val loaded = ensureLibraryLoaded()
            if (loaded) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Failed to load native library"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val loadedModels = ConcurrentHashMap<String, LlamaNative.ModelHandle>()
    private val modelMutex = Mutex()
    private val validationMutex = Mutex()
    private val activeOperations = ConcurrentHashMap<Long, CancellableContinuation<*>>()

    private suspend fun getHandleValidity(handle: LlamaNative.ModelHandle?): Boolean {
        return handle?.isValid() == true
    }

    internal fun addActiveOperation(id: Long, continuation: CancellableContinuation<*>) {
        activeOperations[id] = continuation
    }

    internal fun removeActiveOperation(id: Long) {
        activeOperations.remove(id)
    }

    internal fun removeCachedModel(path: String, handle: LlamaNative.ModelHandle) {
        loadedModels.remove(path, handle)
    }

    internal fun tryNormalizePath(path: String): String? = runCatching { normalizePath(path) }.getOrNull()

    @Volatile
    private var customModelDir: File? = null

    private val defaultModelDir: File by lazy {
        File(context.getExternalFilesDir(null), DEFAULT_MODEL_DIR).also { it.mkdirs() }
    }

    private val modelDir: File
        get() = customModelDir ?: defaultModelDir

    fun setCustomModelDirectory(path: String?) {
        customModelDir = if (path.isNullOrBlank()) {
            null
        } else {
            val candidate = File(path).canonicalFile
            val allowedRoots = listOfNotNull(
                context.filesDir,
                context.getExternalFilesDir(null),
                context.cacheDir,
                context.externalCacheDir
            ).map { it.canonicalFile }
            require(allowedRoots.any { candidate.startsWith(it) }) { "Path traversal detected: $path" }
            candidate
        }
        Log.i(TAG, "Custom model directory set to: ${customModelDir?.absolutePath ?: "none (using default)"}")
    }

    fun getModelDirectory(): File = modelDir

    private var customModelTreeUri: Uri? = null

    override fun setCustomModelTreeUri(treeUri: Uri?) {
        customModelTreeUri = treeUri
        Log.i(TAG, "Custom model tree URI set to: $treeUri")
    }

    suspend fun getAvailableModelFiles(): List<ModelFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<ModelFile>()
        val dir = getModelDirectory()
        dir.listFiles()
            ?.filter { it.extension.lowercase() == "gguf" }
            ?.map { ModelFile.FileBased(it) }
            ?.let { files.addAll(it) }
        customModelTreeUri?.let { uri ->
            scanSafTree(uri)?.let { files.addAll(it) }
        }
        files.sortedByDescending { model: ModelFile -> model.lastModified }
    }

    private fun scanSafTree(treeUri: Uri): List<ModelFile>? {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        return tree.listFiles()
            .filter { doc ->
                doc.isFile && doc.name?.endsWith(".gguf", ignoreCase = true) == true
            }
            .map { doc -> ModelFile.DocumentBased(doc) }
    }

    suspend fun loadModelFromDocument(doc: DocumentFile, config: LlamaNative.GenerationConfig = LlamaNative.GenerationConfig()): LocalModel? {
        val name = doc.name ?: return null
        val targetFile = File(getModelDirectory(), name)
        if (!targetFile.exists()) {
            copyDocumentToFile(doc, targetFile) ?: return null
        }
        return loadModel(targetFile.absolutePath, config)
    }

    private suspend fun copyDocumentToFile(doc: DocumentFile, target: File): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy document: ${doc.uri}", e)
            false
        }
    }

    private suspend fun isModelValidLocked(path: String): Boolean = validationMutex.withLock {
        val handle = loadedModels[path]
        getHandleValidity(handle)
    }

    suspend fun loadModel(
        modelPath: String,
        config: LlamaNative.GenerationConfig = LlamaNative.GenerationConfig()
    ): LocalModel? {
        val normalizedPath = normalizePath(modelPath)
        return modelMutex.withLock {
            val existingHandle = loadedModels[normalizedPath]
            if (existingHandle != null && isModelValidLocked(normalizedPath)) {
                Log.d(TAG, "Reusing cached model: $normalizedPath")
                return@withLock LocalModel(this, existingHandle, config)
            }
            val useNnapi = userPreferences.nnapiDelegationEnabled.first()
            val useMmap = userPreferences.memoryMappingEnabled.first()
            val mergedConfig = config.copy(
                useNnapi = useNnapi,
                useMmap = useMmap
            )
            Log.d(TAG, "Loading model with NNAPI=$useNnapi, MMAP=$useMmap")
            val newHandle = withContext(Dispatchers.Default) {
                llamaEngine.loadModel(normalizedPath, mergedConfig)
            }
            if (newHandle == null) {
                Log.e(TAG, "Failed to load model: $normalizedPath")
                return@withLock null
            }
            loadedModels[normalizedPath] = newHandle
            Log.i(TAG, "Loaded model: $normalizedPath (handle=${newHandle.nativeHandle})")
            LocalModel(this, newHandle, mergedConfig)
        }
    }

    suspend fun loadModelFromStorage(
        fileName: String,
        config: LlamaNative.GenerationConfig = LlamaNative.GenerationConfig()
    ): LocalModel? {
        val file = File(modelDir, fileName)
        if (!file.exists()) {
            Log.e(TAG, "Model file not found: ${file.absolutePath}")
            return null
        }
        return loadModel(file.absolutePath, config)
    }

    suspend fun unloadModel(model: LocalModel) {
        modelMutex.withLock {
            val handle = model.handle
            val path = normalizePath(handle.getModelPath())
            if (loadedModels[path] == handle) {
                handle.free()
                loadedModels.remove(path)
                Log.i(TAG, "Unloaded model: ${handle.getModelPath()}")
            }
        }
    }

    suspend fun unloadAllModels() {
        modelMutex.withLock {
            loadedModels.values.forEach { it.free() }
            loadedModels.clear()
        }
        Log.i(TAG, "Unloaded all models")
    }

    suspend fun checkIsModelLoaded(modelPath: String): Boolean {
        val normalized = normalizePath(modelPath)
        return isModelValidLocked(normalized)
    }

    fun getLoadedModelCount(): Int = loadedModels.count { it.value.isValid() }

    fun getNativeStatus(): Map<String, Any> {
        return llamaEngine.getStatus() + mapOf(
            "loadedModels" to loadedModels.size,
            "modelDirectory" to modelDir.absolutePath
        )
    }

    private fun normalizePath(path: String): String {
        val trimmed = path.trim()
        val file = if (trimmed.startsWith("/")) File(trimmed) else File(trimmed).absoluteFile
        val canonical = file.canonicalFile
        val allowedRoots = listOfNotNull(
            context.filesDir,
            context.getExternalFilesDir(null),
            context.cacheDir,
            context.externalCacheDir
        ).map { it.canonicalFile }
        require(allowedRoots.any { canonical.startsWith(it) }) { "Path traversal detected: $path" }
        return canonical.absolutePath
    }

    private suspend fun LocalGenerationConfig.toNativeConfig(): LlamaNative.GenerationConfig {
        val useNnapi = userPreferences.nnapiDelegationEnabled.first()
        val useMmap = userPreferences.memoryMappingEnabled.first()
        return LlamaNative.GenerationConfig(
            nCtx = this.nCtx,
            nThreads = this.nThreads,
            maxTokens = this.maxTokens,
            topK = this.topK,
            topP = this.topP,
            temp = this.temp,
            useNnapi = useNnapi,
            useMmap = useMmap
        )
    }

    override suspend fun loadModel(modelPath: String, config: LocalGenerationConfig): LocalModelHandle? {
        val nativeConfig = config.toNativeConfig()
        val localModel = loadModel(modelPath, nativeConfig) ?: return null
        return LocalModelHandleWrapper(localModel, config)
    }

    override suspend fun unloadModel(model: LocalModelHandle) {
        if (model is LocalModelHandleWrapper) {
            unloadModel(model.localModel)
        } else {
            Log.w(TAG, "Cannot unload model: unknown LocalModelHandle implementation")
        }
    }

    override suspend fun isModelLoaded(modelPath: String): Boolean {
        val normalized = normalizePath(modelPath)
        return isModelValidLocked(normalized)
    }

    override suspend fun getAvailableModels(): List<String> {
        return withContext(Dispatchers.IO) {
            val dir = getModelDirectory()
            dir.listFiles()
                ?.filter { it.extension.lowercase() == "gguf" }
                ?.map { it.absolutePath }
                ?.sorted()
                ?: emptyList()
        }
    }

    private inner class LocalModelHandleWrapper(
        val localModel: LocalModel,
        private var config: LocalGenerationConfig
    ) : LocalModelHandle {
        override fun getModelPath(): String = localModel.getModelPath()
        override fun isValid(): Boolean = localModel.isValid()
        override suspend fun generateAsync(prompt: String, maxTokens: Int, temperature: Double): Result<String> {
            val effectiveConfig = config.copy(maxTokens = maxTokens, temp = temperature.toFloat())
            return generateWithConfig(prompt, effectiveConfig)
        }
        override suspend fun generateWithConfig(prompt: String, config: LocalGenerationConfig): Result<String> {
            this.config = config
            val nativeConfig = config.toNativeConfig()
            localModel.setConfig(nativeConfig)
            val processedPrompt = if (config.enablePiiMasking) {
                piiMaskingProcessor.maskPii(prompt)
            } else {
                prompt
            }
            return localModel.generateAsync(processedPrompt, nativeConfig)
        }
    }
}
// ... ModelFile and LocalModel class definitions follow ...
// NOTE: ModelFile and LocalModel need to be present at the top level of the file or explicitly imported if in other files.
// I will assume they are top-level in this file for now to keep the context together.

sealed class ModelFile {
    abstract val name: String
    abstract val lastModified: Long
    data class FileBased(val file: File) : ModelFile() {
        override val name: String get() = file.name
        override val lastModified: Long get() = file.lastModified()
    }
    data class DocumentBased(val doc: DocumentFile) : ModelFile() {
        override val name: String get() = doc.name ?: "unknown"
        override val lastModified: Long get() = doc.lastModified()
    }
}

class LocalModel(
    private val manager: LocalInferenceManager,
    val handle: LlamaNative.ModelHandle,
    private var config: LlamaNative.GenerationConfig
) : AutoCloseable {
    companion object {
        private const val TAG = "LocalModel"
        private val generationLock = ReentrantLock()
    }
    private inline fun <T> withGenerationLock(action: () -> T): T = generationLock.withLock(action)
    private suspend fun <T> withSuspendingGenerationLock(action: suspend () -> T): T =
        withContext(Dispatchers.Default) {
            generationLock.lock()
            try {
                action()
            } finally {
                generationLock.unlock()
            }
        }
    fun getModelPath(): String = handle.getModelPath()
    fun isValid(): Boolean = handle.isValid()
    fun getConfig(): LlamaNative.GenerationConfig = config
    fun setConfig(newConfig: LlamaNative.GenerationConfig) {
        config = newConfig
    }
    fun generate(
        prompt: String,
        config: LlamaNative.GenerationConfig? = null
    ): String {
        return withGenerationLock {
            if (!handle.isValid()) {
                return@withGenerationLock "Error: Model is no longer valid. Please reload the model."
            }
            val effectiveConfig = config ?: this.config
            manager.llamaNative.generate(handle, prompt, effectiveConfig)
        }
    }
    suspend fun generateAsync(
        prompt: String,
        config: LlamaNative.GenerationConfig? = null
    ): Result<String> = withSuspendingGenerationLock {
        try {
            val effectiveConfig = config ?: this@LocalModel.config
            Result.success(manager.llamaNative.generate(handle, prompt, effectiveConfig))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    fun generateStream(
        prompt: String,
        config: LlamaNative.GenerationConfig? = null,
        onToken: (String) -> Unit = {},
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        withGenerationLock {
            if (!handle.isValid()) {
                onError("Model is no longer valid. Please reload the model.")
                return@withGenerationLock
            }
            val effectiveConfig = config ?: this.config
            val mainHandler = Handler(Looper.getMainLooper())
            val callback = object : LlamaNative.GenerationCallback {
                override fun onToken(token: String) {
                    mainHandler.post { onToken(token) }
                }
                override fun onCompleted() {
                    mainHandler.post { onComplete() }
                }
                override fun onError(message: String) {
                    mainHandler.post { onError(message) }
                }
            }
            manager.llamaNative.generateStream(handle, prompt, effectiveConfig, callback)
        }
    }
    suspend fun generateStreamAsync(
        prompt: String,
        config: LlamaNative.GenerationConfig? = null
    ): Result<String> = withSuspendingGenerationLock {
        suspendCancellableCoroutine { continuation ->
            val tokens = StringBuilder()
            val effectiveConfig = config ?: this@LocalModel.config
            val opId = handle.nativeHandle
            val completed = AtomicBoolean(false)
            val callback = object : LlamaNative.GenerationCallback {
                override fun onToken(token: String) {
                    if (!completed.get()) {
                        tokens.append(token)
                    }
                }
                override fun onCompleted() {
                    manager.removeActiveOperation(opId)
                    if (completed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(Result.success(tokens.toString()))
                    }
                }
                override fun onError(message: String) {
                    manager.removeActiveOperation(opId)
                    if (completed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(Result.failure(Exception(message)))
                    }
                }
            }
            manager.addActiveOperation(opId, continuation)
            manager.llamaNative.generateStream(handle, prompt, effectiveConfig, callback)
            continuation.invokeOnCancellation {
                manager.llamaNative.cancel(handle)
                manager.removeActiveOperation(opId)
                completed.compareAndSet(false, true)
            }
        }
    }
    override fun close() {
        val path = manager.tryNormalizePath(handle.getModelPath())
        handle.free()
        if (path != null) {
            manager.removeCachedModel(path, handle)
        }
    }
}
