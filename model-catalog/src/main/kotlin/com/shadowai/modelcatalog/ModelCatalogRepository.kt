package com.shadowai.modelcatalog

import com.shadowai.core.ModelDescriptor
import com.shadowai.core.ProviderId

/**
 * Repository interface for model catalog operations.
 * Provides abstraction for model data access.
 */
interface ModelCatalogRepository {
    /**
     * Retrieves all models from the catalog.
     */
    suspend fun getAllModels(): List<ModelDescriptor>

    /**
     * Retrieves a model by its ID.
     */
    suspend fun getModel(modelId: String): ModelDescriptor?

    /**
     * Retrieves models for a specific provider.
     */
    suspend fun getModelsForProvider(providerId: ProviderId): List<ModelDescriptor>

    /**
     * Adds a model to the catalog.
     */
    suspend fun addModel(model: ModelDescriptor)

    /**
     * Adds multiple models to the catalog.
     */
    suspend fun addModels(models: List<ModelDescriptor>)

    /**
     * Removes a model from the catalog.
     */
    suspend fun removeModel(modelId: String)

    /**
     * Updates an existing model.
     */
    suspend fun updateModel(model: ModelDescriptor)

    /**
     * Discovers models from all configured sources.
     */
    suspend fun discoverModels(): List<ModelDescriptor>

    /**
     * Refreshes the model catalog from sources.
     */
    suspend fun refresh()
}
