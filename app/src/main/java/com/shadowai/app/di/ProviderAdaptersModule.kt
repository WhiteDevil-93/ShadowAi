package com.shadowai.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt DI module for provider adapters.
 *
 * H-23 CLEANUP: Removed all manual @Provides for classes with @Inject constructors.
 * The following classes are resolved automatically by Hilt:
 * - ProviderSecretRepository (@Singleton @Inject constructor)
 * - ProviderCrudRepository (@Singleton @Inject constructor)
 * - ProviderModelRepository (@Singleton @Inject constructor)
 * - All ProviderAdapter implementations (injected via factory)
 */
@Module
@InstallIn(SingletonComponent::class)
object ProviderAdaptersModule {
    // No manual provides needed - Hilt auto-resolves @Inject classes
}
