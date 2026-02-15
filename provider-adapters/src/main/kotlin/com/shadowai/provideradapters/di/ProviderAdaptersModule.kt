package com.shadowai.provideradapters.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Hilt DI module for provider-adapters module.
 *
 * H-23 CLEANUP: Removed manual @Provides for classes with @Inject constructors.
 * The following classes are resolved automatically by Hilt:
 * - ProviderCrudRepository (@Singleton @Inject constructor)
 * - ProviderSecretRepository (@Singleton @Inject constructor)
 * - ProviderModelRepository (@Singleton @Inject constructor)
 * - ProviderAdapterFactory (@Singleton @Inject constructor)
 * - ProviderConfigurationService (@Singleton @Inject constructor)
 * - ProviderModelCatalog (utility object, no DI needed)
 */
@Module
@InstallIn(SingletonComponent::class)
object ProviderAdaptersModule {

    /**
     * Provides application-scoped CoroutineScope for async operations.
     * This is required by repositories that need an application scope.
     */
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
