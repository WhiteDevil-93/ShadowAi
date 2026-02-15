/**
 * Architecture Decision Records (ADR) References:
 * - ADR-007: Local Inference Engine Choice - llama.cpp via JNI with process isolation
 * - ADR-001: Provider Adapter Architecture - Local inference as a provider type
 *
 * @see docs/architecture/adr/ADR-007-Local-Inference-Engine-Choice.md
 * @see docs/architecture/adr/ADR-001-Provider-Adapter-Architecture.md
 */
package com.shadowai.app.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.shadowai.core.LocalGenerationConfig
import com.shadowai.core.LocalInferenceEngine
import com.shadowai.core.LocalModelHandle
import com.shadowai.core.inference.InferenceIpcContracts
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client-side bridge to the isolated inference process.
 *
 * Owns AIDL service binding, binder death handling, and automatic rebind
 * logic to recover from inference process crashes/OOM without crashing
 * the main app process.
 */
@Singleton
class IsolatedInferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) : LocalInferenceEngine, InferenceEngineControl, ModelTreeUriConfigurable {

    companion object {
        private const val TAG = "IsolatedInferenceManager"
        private const val SERVICE_CLASS = "com.shadowai.inference.InferenceService"
        private const val MODEL_DIR_NAME = "models"
        private const val MAX_REBIND_ATTEMPTS = 5
        private const val REBIND_BASE_DELAY_MS = 1_000L
    }

    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        OPERATIONAL,
        FAILED
    }

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bindMutex = Mutex()
    private val rebinding = AtomicBoolean(false)

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val modelIdsByPath = ConcurrentHashMap<String, String>()
    private val modelPathsById = ConcurrentHashMap<String, String>()

    // Phase 6.3: Replace @Volatile with AtomicReference/AtomicBoolean
    private val service = AtomicReference<IInferenceService?>(null)
    private val serviceBinder = AtomicReference<IBinder?>(null)
    private val isBound = AtomicBoolean(false)
    private val rebindAttempts = AtomicInteger(0)
    private val nativeAvailable = AtomicBoolean(false)
    private val customModelTreeUri = AtomicReference<Uri?>(null)

    private var connectDeferred: CompletableDeferred<Unit>? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val remote = IInferenceService.Stub.asInterface(binder)
            if (remote == null || binder == null) {
                Log.e(TAG, "Service connected with null binder/interface")
                _connectionStatus.value = ConnectionStatus.FAILED
                connectDeferred?.completeExceptionally(IllegalStateException("Null inference binder"))
                connectDeferred = null
                clearServiceReference(clearModels = true)
                return
            }

            service.set(remote)
            serviceBinder.set(binder)
            isBound.set(true)
            rebindAttempts.set(0)
            _connectionStatus.value = ConnectionStatus.CONNECTED

            runCatching { binder.linkToDeath(deathRecipient, 0) }
                .onFailure { deathError ->
                    Log.e(TAG, "Failed to link death recipient", deathError)
                }

            connectDeferred?.complete(Unit)
            connectDeferred = null

            managerScope.launch {
                refreshServiceInfo()
                _connectionStatus.value = ConnectionStatus.OPERATIONAL
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Inference service disconnected")
            handleServiceFailure("Service disconnected")
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.w(TAG, "Inference service binding died")
            handleServiceFailure("Binding died")
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.e(TAG, "Inference service returned null binding")
            handleServiceFailure("Null binding", scheduleRebind = false)
        }
    }

    private val deathRecipient = IBinder.DeathRecipient {
        Log.e(TAG, "Inference process died (binder death)")
        handleServiceFailure("Binder death")
    }

    override val isNativeAvailable: Boolean
        get() = nativeAvailable.get()

    /**
     * Bind to the remote inference process and wait for a connection result.
     */
    suspend fun bindService(): Result<Unit> = bindMutex.withLock {
        if (service.get() != null && isBound.get()) {
            return@withLock Result.success(Unit)
        }

        return@withLock withContext(Dispatchers.Main) {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            connectDeferred = CompletableDeferred()

            val bindIntent = Intent().setClassName(context, SERVICE_CLASS)
            val bound = context.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                _connectionStatus.value = ConnectionStatus.FAILED
                connectDeferred = null
                clearServiceReference(clearModels = true)
                return@withContext Result.failure(IllegalStateException("Failed to bind to $SERVICE_CLASS"))
            }

            isBound.set(true)
            runCatching {
                connectDeferred?.await()
                Result.success(Unit)
            }.getOrElse { bindError ->
                _connectionStatus.value = ConnectionStatus.FAILED
                clearServiceReference(clearModels = true)
                Result.failure(bindError)
            }
        }
    }

    /**
     * Unbind explicitly from the remote service.
     */
    fun unbindService() {
        if (!isBound.get()) return

        runCatching { context.unbindService(serviceConnection) }
            .onFailure { unbindError ->
                Log.w(TAG, "Unbind failed", unbindError)
            }

        clearServiceReference(clearModels = false)
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    /**
     * Sets a SAF tree URI for model discovery/import.
     *
     * Isolated inference cannot directly mmap a content URI, so models from SAF are copied
     * to app-private model storage before being returned by [getAvailableModels].
     */
    override fun setCustomModelTreeUri(treeUri: Uri?) {
        customModelTreeUri.set(treeUri)
        Log.i(TAG, "Custom model tree URI set to: $treeUri")
    }

    override suspend fun warmup(): Result<Unit> = bindService()

    override suspend fun loadModel(
        modelPath: String,
        config: LocalGenerationConfig
    ): LocalModelHandle? = withContext(Dispatchers.IO) {
        val remote = requireService() ?: return@withContext null
        val normalizedPath = normalizePath(modelPath) ?: return@withContext null
        val modelFile = File(normalizedPath)
        if (!modelFile.exists() || !modelFile.isFile || !modelFile.canRead()) {
            Log.e(TAG, "Model file not readable: $normalizedPath")
            return@withContext null
        }

        val request = Bundle().apply {
            putInt(InferenceIpcContracts.Request.CONTEXT_SIZE, config.nCtx)
            putInt(InferenceIpcContracts.Request.THREADS, config.nThreads)
            putInt(InferenceIpcContracts.Request.MAX_TOKENS, config.maxTokens)
            putInt(InferenceIpcContracts.Request.TOP_K, config.topK)
            putFloat(InferenceIpcContracts.Request.TOP_P, config.topP)
            putFloat(InferenceIpcContracts.Request.TEMPERATURE, config.temp)
            putBoolean(InferenceIpcContracts.Request.ENABLE_PII_MASKING, config.enablePiiMasking)
        }

        val descriptor = runCatching {
            ParcelFileDescriptor.open(modelFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrElse { openError ->
            Log.e(TAG, "Failed to open model descriptor for $normalizedPath", openError)
            return@withContext null
        }

        try {
            val response = remote.loadModel(descriptor, request)
            val success = response.getBoolean(InferenceIpcContracts.Response.SUCCESS, false)
            if (!success) {
                val message = response.getString(InferenceIpcContracts.Response.ERROR_MESSAGE)
                    ?: "Unknown loadModel failure"
                Log.e(TAG, "Remote loadModel failed: $message")
                return@withContext null
            }

            val modelId = response.getString(InferenceIpcContracts.Response.MODEL_ID)
            if (modelId.isNullOrBlank()) {
                Log.e(TAG, "Remote loadModel succeeded but returned no modelId")
                return@withContext null
            }

            modelIdsByPath[normalizedPath] = modelId
            modelPathsById[modelId] = normalizedPath
            RemoteModelHandle(manager = this@IsolatedInferenceManager, modelId = modelId, modelPath = normalizedPath)
        } catch (remoteError: Exception) {
            Log.e(TAG, "Remote error in loadModel", remoteError)
            null
        } finally {
            runCatching { descriptor.close() }
        }
    }

    override suspend fun unloadModel(model: LocalModelHandle) = withContext(Dispatchers.IO) {
        if (model !is RemoteModelHandle) {
            Log.w(TAG, "Unsupported LocalModelHandle type for isolated unload")
            return@withContext
        }

        val remote = requireService()
        runCatching { remote?.unloadModel(model.modelId) }
            .onFailure { unloadError ->
                Log.w(TAG, "Remote unloadModel failed for ${model.modelId}", unloadError)
            }

        removeModelMapping(model.modelId)
    }

    override suspend fun isModelLoaded(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        val normalizedPath = normalizePath(modelPath) ?: return@withContext false
        modelIdsByPath.containsKey(normalizedPath)
    }

    override suspend fun getAvailableModels(): List<String> = withContext(Dispatchers.IO) {
        val dir = modelDirectory()
        val localModels = dir.listFiles()
            ?.asSequence()
            ?.filter { file -> file.isFile && file.extension.equals("gguf", ignoreCase = true) }
            ?.map { file -> file.absolutePath }
            ?.toList()
            ?: emptyList()

        val importedModels = customModelTreeUri.get()?.let { treeUri ->
            importModelsFromSafTree(modelDir = dir, treeUri = treeUri)
        } ?: emptyList()

        (localModels + importedModels).distinct().sorted()
    }

    internal suspend fun generate(modelId: String, prompt: String, config: LocalGenerationConfig): Result<String> {
        val remote = requireService()
            ?: return Result.failure(IllegalStateException("Inference service is not connected"))

        val request = Bundle().apply {
            putString(InferenceIpcContracts.Request.MODEL_ID, modelId)
            putString(InferenceIpcContracts.Request.PROMPT, prompt)
            putInt(InferenceIpcContracts.Request.MAX_TOKENS, config.maxTokens)
            putInt(InferenceIpcContracts.Request.TOP_K, config.topK)
            putFloat(InferenceIpcContracts.Request.TOP_P, config.topP)
            putFloat(InferenceIpcContracts.Request.TEMPERATURE, config.temp)
            putBoolean(InferenceIpcContracts.Request.ENABLE_PII_MASKING, config.enablePiiMasking)
        }

        return runCatching {
            val response = remote.generate(request)
            val success = response.getBoolean(InferenceIpcContracts.Response.SUCCESS, false)
            if (!success) {
                val message = response.getString(InferenceIpcContracts.Response.ERROR_MESSAGE)
                    ?: "Remote generation failed"
                throw IllegalStateException(message)
            }
            response.getString(InferenceIpcContracts.Response.TEXT)
                ?: throw IllegalStateException("Remote generation returned null text")
        }
    }

    internal fun isModelIdLoaded(modelId: String): Boolean = modelPathsById.containsKey(modelId)

    private suspend fun requireService(): IInferenceService? {
        service.get()?.let { return it }

        val bindResult = bindService()
        if (bindResult.isFailure) {
            Log.e(TAG, "Unable to bind inference service", bindResult.exceptionOrNull())
            return null
        }
        return service.get()
    }

    private suspend fun refreshServiceInfo() {
        val remote = service.get() ?: return
        runCatching {
            val info = remote.getServiceInfo()
            val isNative = info.getBoolean(
                InferenceIpcContracts.ServiceInfo.NATIVE_LIBRARY_LOADED,
                false
            )
            nativeAvailable.set(isNative)
        }.onFailure { infoError ->
            nativeAvailable.set(false)
            Log.w(TAG, "Failed to fetch service info", infoError)
        }
    }

    private fun normalizePath(path: String): String? {
        return runCatching { File(path).canonicalPath }
            .onFailure { pathError ->
                Log.e(TAG, "Invalid model path: $path", pathError)
            }
            .getOrNull()
    }

    private fun modelDirectory(): File {
        return File(context.getExternalFilesDir(null), MODEL_DIR_NAME).also { it.mkdirs() }
    }

    private fun importModelsFromSafTree(modelDir: File, treeUri: Uri): List<String> {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        if (!tree.exists() || !tree.isDirectory) return emptyList()

        return tree.listFiles()
            .asSequence()
            .filter { doc -> doc.isFile && doc.name?.endsWith(".gguf", ignoreCase = true) == true }
            .mapNotNull { doc ->
                val name = doc.name ?: return@mapNotNull null
                val destination = File(modelDir, name)
                val needsCopy = !destination.exists() || destination.length() != doc.length()
                if (needsCopy) {
                    val copied = copyDocumentToFile(doc, destination)
                    if (!copied) return@mapNotNull null
                }
                destination.absolutePath
            }
            .toList()
    }

    private fun copyDocumentToFile(doc: DocumentFile, destination: File): Boolean {
        return try {
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return false
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to import model from SAF URI: ${doc.uri}", e)
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied importing model from SAF URI: ${doc.uri}", e)
            false
        }
    }

    private fun removeModelMapping(modelId: String) {
        val path = modelPathsById.remove(modelId)
        if (path != null) {
            modelIdsByPath.remove(path)
        }
    }

    private fun clearServiceReference(clearModels: Boolean) {
        serviceBinder.get()?.let { binder ->
            runCatching { binder.unlinkToDeath(deathRecipient, 0) }
        }
        serviceBinder.set(null)
        service.set(null)
        isBound.set(false)
        nativeAvailable.set(false)

        if (clearModels) {
            modelIdsByPath.clear()
            modelPathsById.clear()
        }
    }

    private fun handleServiceFailure(reason: String, scheduleRebind: Boolean = true) {
        Log.w(TAG, reason)
        clearServiceReference(clearModels = true)
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        connectDeferred?.completeExceptionally(IllegalStateException(reason))
        connectDeferred = null

        if (scheduleRebind) {
            scheduleRebind()
        } else {
            _connectionStatus.value = ConnectionStatus.FAILED
        }
    }

    private fun scheduleRebind() {
        if (!rebinding.compareAndSet(false, true)) return

        managerScope.launch {
            try {
                while (rebindAttempts.get() < MAX_REBIND_ATTEMPTS && service.get() == null) {
                    rebindAttempts.incrementAndGet()
                    _connectionStatus.value = ConnectionStatus.CONNECTING
                    delay(REBIND_BASE_DELAY_MS * rebindAttempts.get())

                    val result = bindService()
                    if (result.isSuccess) {
                        Log.i(TAG, "Inference service rebound after attempt ${rebindAttempts.get()}")
                        return@launch
                    }
                }
                _connectionStatus.value = ConnectionStatus.FAILED
            } finally {
                rebinding.set(false)
            }
        }
    }

    private class RemoteModelHandle(
        private val manager: IsolatedInferenceManager,
        val modelId: String,
        private val modelPath: String
    ) : LocalModelHandle {

        override fun getModelPath(): String = modelPath

        override fun isValid(): Boolean = manager.isModelIdLoaded(modelId)

        override suspend fun generateAsync(prompt: String, maxTokens: Int, temp: Double): Result<String> {
            return generateWithConfig(prompt, LocalGenerationConfig(maxTokens = maxTokens, temp = temp.toFloat()))
        }

        override suspend fun generateWithConfig(prompt: String, config: LocalGenerationConfig): Result<String> {
            return manager.generate(modelId = modelId, prompt = prompt, config = config)
        }
    }
}
