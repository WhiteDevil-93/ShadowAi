package com.shadowai.modelcatalog

import android.content.Context
import com.google.gson.Gson
import com.shadowai.core.ModelDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Extension to ModelDiscovery for persistence hooks.
 *
 * Note: model-catalog cannot depend on app Room entities/DAOs.
 * App-level persistence should happen in the app module.
 */
class ModelDiscoveryPersistence @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val modelDiscovery = ModelDiscovery(context, gson)

    /**
     * Discover models and persist to database.
     */
    suspend fun discoverAndPersistModels(): List<ModelDescriptor> = withContext(Dispatchers.IO) {
        modelDiscovery.discoverFromAllSources()
    }

    /**
     * Placeholder until app-layer persistence is wired to model-catalog contracts.
     */
    suspend fun getLRUModelId(): String? = withContext(Dispatchers.IO) { null }

    /**
     * Placeholder until app-layer persistence is wired to model-catalog contracts.
     */
    suspend fun markModelAsUsed(modelId: String) = withContext(Dispatchers.IO) {
        // No-op in model-catalog module.
    }
}
