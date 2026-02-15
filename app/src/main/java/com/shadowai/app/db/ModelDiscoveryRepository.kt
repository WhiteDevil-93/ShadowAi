package com.shadowai.app.db

import android.content.Context
import android.util.Log
import com.shadowai.core.ModelDescriptor
import com.shadowai.core.ProviderId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for persisting and restoring model discovery state.
 */
@Singleton
class ModelDiscoveryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ShadowDatabase
) {
    private val modelPathDao: ModelPathDao = database.modelPathDao()

    companion object {
        private const val TAG = "ModelDiscoveryRepository"
        private const val THREAD_POOL_SIZE = 4
        private const val THREAD_KEEP_ALIVE_SECONDS = 30L
    }

    private val discoveryThreadPool: ThreadPoolExecutor = Executors.newFixedThreadPool(
        THREAD_POOL_SIZE
    ) as ThreadPoolExecutor

    init {
        discoveryThreadPool.setKeepAliveTime(THREAD_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS)
        discoveryThreadPool.allowCoreThreadTimeOut(true)
    }

    suspend fun persistModel(model: ModelDescriptor, path: String) = withContext(Dispatchers.IO) {
        try {
            val entity = ModelPathEntity(
                path = path,
                modelId = model.id,
                lastDiscovered = System.currentTimeMillis(),
                valid = true
            )
            modelPathDao.insertOrUpdate(entity)
            Log.d(TAG, "Persisted model: ${model.id} at path: $path")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist model: ${model.id}", e)
        }
    }

    suspend fun persistModels(models: List<Pair<ModelDescriptor, String>>) = withContext(Dispatchers.IO) {
        models.forEach { (model, path) ->
            persistModel(model, path)
        }
        Log.d(TAG, "Persisted ${models.size} models")
    }

    suspend fun getValidModelPaths(): List<ModelPathEntity> = withContext(Dispatchers.IO) {
        modelPathDao.getValid()
    }

    suspend fun getAllModelPaths(): List<ModelPathEntity> = withContext(Dispatchers.IO) {
        modelPathDao.getAll()
    }

    suspend fun getModelPathByPath(path: String): ModelPathEntity? = withContext(Dispatchers.IO) {
        modelPathDao.getByPath(path)
    }

    suspend fun invalidateModelPath(path: String) = withContext(Dispatchers.IO) {
        try {
            modelPathDao.setInvalid(path)
            Log.d(TAG, "Invalidated model path: $path")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to invalidate model path: $path", e)
        }
    }

    suspend fun isPathValid(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            file.exists() && file.canRead()
        } catch (e: SecurityException) {
            Log.w(TAG, "Security exception checking path: $path")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking path validity: $path", e)
            false
        }
    }

    suspend fun validateAndCleanPaths(): Int = withContext(Dispatchers.IO) {
        var invalidCount = 0
        val allPaths = modelPathDao.getAll()

        allPaths.forEach { entity ->
            if (!isPathValid(entity.path)) {
                modelPathDao.setInvalid(entity.path)
                invalidCount++
            }
        }

        Log.d(TAG, "Validated paths: ${allPaths.size} total, $invalidCount marked invalid")
        invalidCount
    }

    suspend fun restoreModelPaths(): List<String> = withContext(Dispatchers.IO) {
        val validPaths = modelPathDao.getValid()
        val existingPaths = validPaths.filter { entity ->
            isPathValid(entity.path).also { isValid ->
                if (!isValid) {
                    modelPathDao.setInvalid(entity.path)
                }
            }
        }.map { it.path }

        Log.d(TAG, "Restored ${existingPaths.size} model paths from ${validPaths.size} valid entries")
        existingPaths
    }

    suspend fun clearAllPaths() = withContext(Dispatchers.IO) {
        modelPathDao.deleteAll()
        Log.d(TAG, "Cleared all model paths")
    }

    suspend fun getPersistedModelCount(): Int = withContext(Dispatchers.IO) {
        modelPathDao.getCount()
    }

    suspend fun getValidModelCount(): Int = withContext(Dispatchers.IO) {
        modelPathDao.getValidCount()
    }

    suspend fun discoverModelsAsync(searchPaths: List<String>): List<String> =
        withContext(Dispatchers.Default) {
            val discoveredPaths = mutableListOf<String>()
            val dispatcher = discoveryThreadPool.asCoroutineDispatcher()

            val jobs = searchPaths.map { path ->
                // FIX: Use async on current scope (coroutineScope is implicit in withContext)
                async(dispatcher) {
                    discoverModelsInPath(path)
                }
            }

            jobs.forEach { job ->
                try {
                    discoveredPaths.addAll(job.await())
                } catch (e: Exception) {
                    Log.e(TAG, "Error during async model discovery", e)
                }
            }

            Log.d(TAG, "Async discovery complete: ${discoveredPaths.size} models found")
            discoveredPaths
        }

    private fun discoverModelsInPath(path: String): List<String> {
        val models = mutableListOf<String>()
        try {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) {
                return models
            }

            dir.listFiles()?.forEach { file ->
                when {
                    file.name.endsWith(".gguf", ignoreCase = true) -> {
                        models.add(file.absolutePath)
                        Log.d(TAG, "Discovered GGUF model: ${file.name}")
                    }
                    file.name.endsWith(".safetensors", ignoreCase = true) -> {
                        models.add(file.absolutePath)
                        Log.d(TAG, "Discovered SafeTensors model: ${file.name}")
                    }
                    file.name == "model.json" -> {
                        file.parentFile?.absolutePath?.let { models.add(it) }
                        Log.d(TAG, "Discovered model configuration: ${file.parent}")
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Security exception accessing path: $path")
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering models in path: $path", e)
        }
        return models
    }

    fun shutdown() {
        discoveryThreadPool.shutdown()
        try {
            if (!discoveryThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                discoveryThreadPool.shutdownNow()
            }
        } catch (e: InterruptedException) {
            discoveryThreadPool.shutdownNow()
        }
        Log.d(TAG, "Discovery thread pool shut down")
    }
}
