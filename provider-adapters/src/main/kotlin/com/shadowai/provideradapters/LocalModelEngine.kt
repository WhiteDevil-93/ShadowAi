package com.shadowai.provideradapters

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing discovered local model information.
 */
data class LocalModelInfo(
    val path: String,
    val format: String,
    val size: Long
)

/**
 * Engine for discovering and managing local AI models.
 * Abstracts local model operations from specific engine implementations.
 */
interface LocalModelEngine {
    /**
     * Scan for available local models.
     * @return List of discovered model metadata
     */
    fun scanForModels(): List<LocalModelInfo>

    /**
     * Set a custom directory to search for models.
     * @param path Custom directory path or null to reset
     */
    fun setCustomModelDirectory(path: String?)
}

/**
 * Stub implementation for when no local engine is available.
 */
@Singleton
class StubLocalModelEngine @Inject constructor() : LocalModelEngine {
    override fun scanForModels(): List<LocalModelInfo> = emptyList()
    override fun setCustomModelDirectory(path: String?) { }
}
