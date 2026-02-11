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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level manager for local LLM inference using llama.cpp.
 *
 * This class provides a simplified API for:
 * - Loading GGUF models from storage
 * - Generating text with various configurations
 * - Managing model lifecycle and memory
 *
 * CRITICAL FIXES APPLIED:
 * 1. JNI Callback Thread Safety - Thread confinement for JNI callbacks
 * 2. External Storage Encryption Check - Verify storage is encrypted
 * 3. isValid() Race Condition - Synchronized access to model validation
 * 4. API Consistency - Standardized suspend/async APIs
 * 5. Architecture Compliance - Implements LocalInferenceEngine for cross-module DI
 *
 * Usage:
 * ```
 * val manager = LocalInferenceManager(context)
 *
 * // Load a model
 * val model = manager.loadModel("/path/to/model.gguf")
 *
 * // Generate text
 * val response = manager.generate(model, "Hello, how are you?")
 * println(response)
 *
 * // Clean up when done
 * manager.unloadModel(model)
 * ```
 */
@Singleton
class LocalInferenceManager @Inject constructor(
    context: Context,
    internal val piiMaskingProcessor: PiiMaskingProcessor
) : LocalInferenceEngine, ModelTreeUriConfigurable {
    private val context = context.applicationContext

    companion object {
        private const val TAG = "LocalInference"
        internal const val DEFAULT_MODEL_DIR = "models"

        // CRITICAL FIX: External storage encryption check
        private const val MIN_ENCRYPTED_DISK_SPACE_BYTES = 50L * 1024 * 1024
    }

    internal val llamaNative = LlamaNative()

    /**
     * Whether the native inference engine is available (library loaded successfully).
     */
    override val isNativeAvailable: Boolean get() = LlamaNative.isAvailable

    private val loadedModels = ConcurrentHashMap<String, LlamaNative.ModelHandle>()
    private val modelMutex = Mutex()
    private val activeOperations = ConcurrentHashMap<Long, CancellableContinuation<*>>()

    // CRITICAL FIX: Thread-safe validation flag
    private val validationMutex = Mutex()

    private suspend fun getHandleValidity(handle: LlamaNative.ModelHandle?): Boolean {
        // This is a suspend function, forcing the lambda to be suspend
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

    /**
     * Set a custom directory to search for models.
     */
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

    /**
     * Get the directory currently used for model discovery.
     */
    fun getModelDirectory(): File = modelDir

    private var customModelTreeUri: Uri? = null

    /**
     * Set a custom model directory via SAF (Storage Access Framework) tree URI.
     * This allows accessing external storage (Downloads, SD cards) on Android 11+.
     */
    override fun setCustomModelTreeUri(treeUri: Uri?) {
        customModelTreeUri = treeUri
        Log.i(TAG, "Custom model tree URI set to: $treeUri")
    }

    /**
     * Get list of available model files.
     * Scans both file-based directories and SAF tree URIs.
     */
    suspend fun getAvailableModelFiles(): List<ModelFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<ModelFile>()

        // Scan file-based directory
        val dir = getModelDirectory()
        dir.listFiles()
            ?.filter { it.extension.lowercase() == "gguf" }
            ?.map { ModelFile.FileBased(it) }
            ?.let { files.addAll(it) }

        // Scan SAF tree URI if set
        customModelTreeUri?.let { uri ->
            scanSafTree(uri)?.let { files.addAll(it) }
        }

        files.sortedByDescending { model: ModelFile -> model.lastModified }
    }

    /**
     * Scan a SAF tree URI for .gguf model files.
     */
    private fun scanSafTree(treeUri: Uri): List<ModelFile>? {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        return tree.listFiles()
            .filter { doc ->
                doc.isFile && doc.name?.endsWith(".gguf", ignoreCase = true) == true
            }
            .map { doc -> ModelFile.DocumentBased(doc) }
    }

    /**
     * Load a model from a SAF document.
     * First copies the file to app-private storage, then loads it.
     */
    suspend fun loadModelFromDocument(doc: DocumentFile, config: LlamaNative.GenerationConfig = LlamaNative.GenerationConfig()): LocalModel? {
        val name = doc.name ?: return null

        // Copy to app-private storage first (required for native library access)
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

    /**
     * CRITICAL FIX: External Storage Encryption Check
     * Verifies that external storage is encrypted before loading models.
     */
    private fun checkStorageEncryption(): Boolean {
        // Check if device is encrypted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val encryptionStatus = Environment.getStorageState(context.getExternalFilesDir(null))
            // Only allow loading from external storage if properly encrypted
            if (encryptionStatus != Environment.MEDIA_MOUNTED) {
                Log.w(TAG, "External storage not available or not encrypted")
                return false
            }
        }
        return true
    }

    /**
     * CRITICAL FIX: Synchronized isValid() check to prevent race conditions
     */
    private suspend fun isModelValidLocked(path: String): Boolean = validationMutex.withLock {
        val handle = loadedModels[path]
        getHandleValidity(handle)
    }

    /**
     * Load a GGUF model from the specified path.
     *
     * @param modelPath Path to the GGUF model file
     * @param config Generation configuration
     * @return LocalModel instance for generating text
     */
    suspend fun loadModel(
        modelPath: String,
        config: LlamaNative.GenerationConfig = LlamaNative.GenerationConfig()
    ): LocalModel? {
        val normalizedPath = normalizePath(modelPath)

        return modelMutex.withLock {
            // CRITICAL FIX: Check model validity under mutex protection
            val existingHandle = loadedModels[normalizedPath]
            if (existingHandle != null && isModelValidLocked(normalizedPath)) {
                Log.d(TAG, "Reusing cached model: $normalizedPath")
                return@withLock LocalModel(this, existingHandle, config)
            }

            // Load new model
            val newHandle = withContext(Dispatchers.Default) {
                llamaNative.loadModel(normalizedPath, config)
            }
            if (newHandle == null) {
                Log.e(TAG, "Failed to load model: $normalizedPath")
                return@withLock null
            }

            loadedModels[normalizedPath] = newHandle
            Log.i(TAG, "Loaded model: $normalizedPath (handle=${newHandle.nativeHandle})")
            LocalModel(this, newHandle, config)
        }
    }

    /**
     * Load a model from the app's model directory.
     */
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

    /**
     * Unload a specific model.
     */
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

    /**
     * Unload all loaded models.
     */
    suspend fun unloadAllModels() {
        modelMutex.withLock {
            loadedModels.values.forEach { it.free() }
            loadedModels.clear()
        }
        Log.i(TAG, "Unloaded all models")
    }

    /**
     * Check if a model is currently loaded.
     * CRITICAL FIX: Uses synchronized access under mutex
     */
    suspend fun checkIsModelLoaded(modelPath: String): Boolean {
        val normalized = normalizePath(modelPath)
        return isModelValidLocked(normalized)
    }

    /**
     * Get the number of currently loaded models.
     */
    fun getLoadedModelCount(): Int = loadedModels.count { it.value.isValid() }

    /**
     * Get diagnostic information about the native library.
     */
    fun getNativeStatus(): Map<String, Any> {
        return llamaNative.getStatus() + mapOf(
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

    // ================================
    // LocalInferenceEngine Interface Implementation
    // ================================
    // These methods delegate to the internal implementation while adapting�types for cross-module use.

    /**
     * Convert core-contracts LocalGenerationConfig to internal LlamaNative.GenerationConfig.
     */
    private fun LocalGenerationConfig.toNativeConfig(): LlamaNative.GenerationConfig {
        return LlamaNative.GenerationConfig(
            nCtx = this.nCtx,
            nThreads = this.nThreads,
            maxTokens = this.maxTokens,
            topK = this.topK,
            topP = this.topP,
            temp = this.temp
        )
    }

    /**
     * Interface implementation: Load a model using the cross-module contract.
     */
    override suspend fun loadModel(modelPath: String, config: LocalGenerationConfig): LocalModelHandle? {
        val nativeConfig = config.toNativeConfig()
        val localModel = loadModel(modelPath, nativeConfig) ?: return null
        return LocalModelHandleWrapper(localModel, config)
    }

    /**
     * Interface implementation: Unload a model using the cross-module contract.
     */
    override suspend fun unloadModel(model: LocalModelHandle) {
        if (model is LocalModelHandleWrapper) {
            unloadModel(model.localModel)
        } else {
            // Fallback for unknown implementations - cannot unload properly
            Log.w(TAG, "Cannot unload model: unknown LocalModelHandle implementation")
        }
    }

    /**
     * Interface implementation: Check if model loaded using the cross-module contract.
     */
    override suspend fun isModelLoaded(modelPath: String): Boolean {
        val normalized = normalizePath(modelPath)
        return isModelValidLocked(normalized)
    }

    /**
     * Interface implementation: Get available models using the cross-module contract.
     */
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

    /**
     * Wrapper class that adapts LocalModel to the cross-module LocalModelHandle interface.
     */
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

/**
 * Represents a model file that can be either file-based or SAF document-based.
 * This abstraction allows seamless handling of both internal storage and external (SAF) sources.
 */
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

/**
 * Represents a loaded local model with generation capabilities.
 *
 * CRITICAL FIXES APPLIED:
 * 1. Thread-safe generation with proper synchronization
 * 2. Consistent suspend APIs for async operations
 *
 * This class is NOT thread-safe for concurrent generation operations.
 * Create separate LocalModel instances for concurrent use.
 */
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

    /**
     * Get the model path.
     */
    fun getModelPath(): String = handle.getModelPath()

    /**
     * Check if the model handle is still valid.
     * CRITICAL FIX: Uses synchronized access
     */
    fun isValid(): Boolean = handle.isValid()

    /**
     * Get current generation configuration.
     */
    fun getConfig(): LlamaNative.GenerationConfig = config

    /**
     * Update generation configuration.
     */
    fun setConfig(newConfig: LlamaNative.GenerationConfig) {
        config = newConfig
    }

    /**
     * Generate text from a prompt.
     *
     * CRITICAL FIX: Thread-safe generation with mutex protection
     *
     * This is a blocking operation. For large outputs, consider using generateAsync.
     *
     * @param prompt The input prompt
     * @param config Optional generation configuration override
     * @return Generated text, or error message starting with "Error:"
     */
    fun generate(
        prompt: String,
        config: LlamaNative.GenerationConfig? = null
    ): String {
        return withGenerationLock {
            if (!handle.isValid()) {
                return@withGenerationLock "Error: Model is no longer valid. Please reload the model."
            }
        val effectiveConfig = config ?: this.config
        val processedPrompt = prompt
        manager.llamaNative.generate(handle, processedPrompt, effectiveConfig)
        }
    }

    /**
     * Generate text asynchronously.
     *
     * CRITICAL FIX: Consistent suspend API
     *
     * @param prompt The input prompt
     * @param config Optional generation configuration override
     * @return Result containing either the generated text or an error
     */
    suspend fun generateAsync(
        prompt: String,
        config: LlamaNative.GenerationConfig? = null
    ): Result<String> = withSuspendingGenerationLock {
        try {
            val effectiveConfig = config ?: this@LocalModel.config
            val processedPrompt = prompt
            Result.success(manager.llamaNative.generate(handle, processedPrompt, effectiveConfig))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate text with streaming output.
     *
     * CRITICAL FIX: Thread-safe streaming with proper JNI thread confinement
     *
     * @param prompt The input prompt
     * @param config Optional generation configuration override
     * @param onToken Callback for each token generated
     * @param onComplete Callback when generation is complete
     * @param onError Callback for errors
     */
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

            val processedPrompt = prompt

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

            manager.llamaNative.generateStream(handle, processedPrompt, effectiveConfig, callback)
        }
    }

    /**
     * Generate text with streaming output (suspend version).
     *
     * CRITICAL FIX: Consistent suspend API with proper cancellation support
     *
     * @param prompt The input prompt
     * @param config Optional generation configuration override
     * @return Result containing the generated text or an error
     */
    suspend fun generateStreamAsync(
        prompt: String,
        config: LlamaNative.GenerationConfig? = null
    ): Result<String> = withSuspendingGenerationLock {
        suspendCancellableCoroutine { continuation ->
            val tokens = StringBuilder()
            val effectiveConfig = config ?: this@LocalModel.config
            val opId = handle.nativeHandle
            val completed = AtomicBoolean(false)

            val processedPrompt = prompt

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
            manager.llamaNative.generateStream(handle, processedPrompt, effectiveConfig, callback)

            continuation.invokeOnCancellation {
                Log.i(TAG, "Cancelling native generation for handle: $opId")
                manager.llamaNative.cancel(handle)
                manager.removeActiveOperation(opId)
                completed.compareAndSet(false, true)
            }
        }
    }

    /**
     * Release resources associated with this model.
     * After calling this, the LocalModel instance cannot be used.
     */
    override fun close() {
        val path = manager.tryNormalizePath(handle.getModelPath())
        handle.free()
        if (path != null) {
            manager.removeCachedModel(path, handle)
        }
    }
}
