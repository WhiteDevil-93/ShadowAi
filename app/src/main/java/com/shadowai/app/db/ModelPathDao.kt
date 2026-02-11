package com.shadowai.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy

/**
 * Room Data Access Object for model path persistence.
 *
 * Provides CRUD operations for discovered model paths,
 * including methods for batch operations and validity tracking.
 */
@Dao
interface ModelPathDao {

    /**
     * Insert or update a model path entity.
     * Uses REPLACE strategy to update existing entries.
     *
     * @param entity The ModelPathEntity to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: ModelPathEntity)

    /**
     * Get all persisted model paths.
     *
     * @return List of all ModelPathEntity entries
     */
    @Query("SELECT * FROM model_paths")
    suspend fun getAll(): List<ModelPathEntity>

    /**
     * Get a specific model path by its path.
     *
     * @param path The file path to look up
     * @return The ModelPathEntity if found, null otherwise
     */
    @Query("SELECT * FROM model_paths WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): ModelPathEntity?

    /**
     * Get all valid model paths.
     * Filters out paths that have been marked as invalid.
     *
     * @return List of valid ModelPathEntity entries
     */
    @Query("SELECT * FROM model_paths WHERE valid = 1")
    suspend fun getValid(): List<ModelPathEntity>

    /**
     * Mark a model path as invalid.
     * Used when a file is deleted or becomes inaccessible.
     *
     * @param path The file path to invalidate
     */
    @Query("UPDATE model_paths SET valid = 0 WHERE path = :path")
    suspend fun setInvalid(path: String)

    /**
     * Delete a model path entry.
     *
     * @param path The file path to delete
     */
    @Query("DELETE FROM model_paths WHERE path = :path")
    suspend fun deleteByPath(path: String)

    /**
     * Delete all model paths.
     */
    @Query("DELETE FROM model_paths")
    suspend fun deleteAll()

    /**
     * Get the count of all model paths.
     *
     * @return Total count of model path entries
     */
    @Query("SELECT COUNT(*) FROM model_paths")
    suspend fun getCount(): Int

    /**
     * Get the count of valid model paths.
     *
     * @return Count of valid model path entries
     */
    @Query("SELECT COUNT(*) FROM model_paths WHERE valid = 1")
    suspend fun getValidCount(): Int
}
