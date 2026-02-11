package com.shadowai.app.db

import android.content.Context
import android.util.Log
import com.shadowai.core.ModelDescriptor
import com.shadowai.core.ProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for persisting and restoring model discovery state.
 *
 * This class bridges the model-catalog module's ModelDiscovery with the app module's
 * Room database, enabling persistence of discovered model paths across app restarts.
 *
 * @param context Application context for database access
 * @param database The ShadowDatabase instance
 */
@Singleton
class ModelDiscoveryRepository @Inject constructor(
    private val context: Context,
    private val database: ShadowDatabase
) {
    private val modelPathDao: ModelPathDao = database.modelPathDao()

    companion object {
        private const val TAG = "ModelDiscoveryRepository"
    }

    /**
     * Persist a discovered model to the database.
     *
     * @param model The ModelDescriptor to persist
     * @param path The file path to the model
     */
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

    /**
     * Persist multiple discovered models to the database.
     *
     * @param models List of pairs containing ModelDescriptor and its path
     */
    suspend fun persistModels(models: List<Pair<ModelDescriptor, String>>) = withContext(Dispatchers.IO) {
        models.forEach { (model, path) ->
            persistModel(model, path)
        }
        Log.d(TAG, "Persisted ${models.size} models")
    }

    /**
     * Get all valid persisted model paths.
     *
     * @return List of valid ModelPathEntity entries
     */
    suspend fun getValidModelPaths(): List<ModelPathEntity> = withContext(Dispatchers.IO) {
        modelPathDao.getValid()
    }

    /**
     * Get all persisted model paths (including invalid).
     *
     * @return List of all ModelPathEntity entries
     */
    suspend fun getAllModelPaths(): List<ModelPathEntity> = withContext(Dispatchers.IO) {
        modelPathDao.getAll()
    }

    /**
     * Get a specific model path by its path.
     *
     * @param path The file path to look up
     * @return The ModelPathEntity if found, null otherwise
     */
    suspend fun getModelPathByPath(path: String): ModelPathEntity? = withContext(Dispatchers.IO) {
        modelPathDao.getByPath(path)
    }

    /**
     * Mark a model path as invalid.
     * Called when a file is deleted or becomes inaccessible.
     *
     * @param path The file path to invalidate
     */
    suspend fun invalidateModelPath(path: String) = withContext(Dispatchers.IO) {
        try {
            modelPathDao.setInvalid(path)
            Log.d(TAG, "Invalidated model path: $path")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to invalidate model path: $path", e)
        }
    }

    /**
     * Check if a model path is still valid (file exists and is readable).
     *
     * @param path The file path to check
     * @return True if the file exists and is readable
     */
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

    /**
     * Validate all persisted paths and mark invalid ones.
     *
     * @return Number of paths marked as invalid
     */
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

    /**
     * Restore models from persisted paths.
     * Only returns valid paths that still exist on the filesystem.
     *
     * @return List of valid file paths
     */
    suspend fun restoreModelPaths(): List<String> = withContext(Dispatchers.IO) {
        val validPaths = modelPathDao.getValid()
        val existingPaths = validPaths.filter { entity ->
            isPathValid(entity.path).also { isValid ->
                if (!isValid) {
                    // Mark as invalid in database since file no longer exists
                    modelPathDao.setInvalid(entity.path)
                }
            }
        }.map { it.path }
        
        Log.d(TAG, "Restored ${existingPaths.size} model paths from ${validPaths.size} valid entries")
        existingPaths
    }

    /**
     * Clear all persisted model paths.
     */
    suspend fun clearAllPaths() = withContext(Dispatchers.IO) {
        modelPathDao.deleteAll()
        Log.d(TAG, "Cleared all model paths")
    }

    /**
     * Get the count of persisted models.
     *
     * @return Total count of model path entries
     */
    suspend fun getPersistedModelCount(): Int = withContext(Dispatchers.IO) {
        modelPathDao.getCount()
    }

    /**
     * Get the count of valid persisted models.
     *
     * @return Count of valid model path entries
     */
    suspend fun getValidModelCount(): Int = withContext(Dispatchers.IO) {
        modelPathDao.getValidCount()
    }
}
