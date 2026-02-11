package com.shadowai.app.di

import android.content.Context
import com.shadowai.provideradapters.ProviderSecretRepository
import com.shadowai.core.security.TeeKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProviderAdaptersModule {

    @Provides
    @Singleton
    fun provideProviderSecretRepository(
        @ApplicationContext context: Context,
        teeKeyManager: TeeKeyManager
    ): ProviderSecretRepository {
        return ProviderSecretRepository(context, teeKeyManager)
    }

    // App-side ProviderRepository facade remains for legacy call sites during the migration
    // to core-contracts ProviderRepository.
}
