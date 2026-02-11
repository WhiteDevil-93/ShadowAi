package com.shadowai.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for persisting discovered model paths.
 *
 * This entity stores metadata about discovered local model files,
 * enabling persistence of model discovery state across app restarts.
 *
 * @property path The absolute file path to the model (primary key)
 * @property modelId The unique identifier for the model
 * @property lastDiscovered Timestamp when the model was last discovered
 * @property valid Whether the model path is still valid (file exists and is accessible)
 */
@Entity(tableName = "model_paths")
data class ModelPathEntity(
    @PrimaryKey val path: String,
    val modelId: String,
    val lastDiscovered: Long = System.currentTimeMillis(),
    val valid: Boolean = true
)
