package com.shadowai.modelcatalog.di

import android.content.Context
import com.google.gson.Gson
import com.shadowai.modelcatalog.ModelCatalogRepository
import com.shadowai.modelcatalog.ModelDiscovery
import com.shadowai.modelcatalog.ModelRegistry
import com.shadowai.core.ProviderId
import com.shadowai.core.ModelDescriptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the Model Catalog.
 * Encapsulates all model discovery and registry dependencies.
 *
 * Part of Phase 1: Module Boundaries.
 */
@Module
@InstallIn(SingletonComponent::class)
object ModelCatalogModule {

    @Provides
    @Singleton
    fun provideModelRegistry(): ModelRegistry {
        return ModelRegistry()
    }

    @Provides
    @Singleton
    fun provideModelDiscovery(
        @ApplicationContext context: Context,
        gson: Gson
    ): ModelDiscovery {
        return ModelDiscovery(context, gson)
    }

    @Provides
    @Singleton
    fun provideModelCatalogRepository(
        modelRegistry: ModelRegistry,
        modelDiscovery: ModelDiscovery
    ): ModelCatalogRepository {
        return object : ModelCatalogRepository {
            override suspend fun getAllModels() = modelRegistry.getAllModels()

            override suspend fun getModel(modelId: String) = modelRegistry.getModel(modelId)

            override suspend fun getModelsForProvider(providerId: ProviderId) =
                modelRegistry.getModelsForProvider(providerId)

            override suspend fun addModel(model: ModelDescriptor) {
                modelRegistry.registerModel(model)
            }

            override suspend fun addModels(models: List<ModelDescriptor>) {
                modelRegistry.registerModels(models)
            }

            override suspend fun removeModel(modelId: String) {
                modelRegistry.unregisterModel(modelId)
            }

            override suspend fun updateModel(model: ModelDescriptor) {
                modelRegistry.registerModel(model)
            }

            override suspend fun discoverModels(): List<ModelDescriptor> {
                return modelDiscovery.discoverFromAllSources()
            }

            override suspend fun refresh() {
                val discovered = modelDiscovery.discoverFromAllSources()
                modelRegistry.clear()
                modelRegistry.registerModels(discovered)
            }
        }
    }
}
