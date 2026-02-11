package com.shadowai.provideradapters.di

import com.shadowai.core.providers.ProviderRepository
import com.shadowai.provideradapters.ProviderRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderAdaptersModule {

    @Binds
    @Singleton
    abstract fun bindProviderRepository(
        impl: ProviderRepositoryImpl
    ): ProviderRepository

    companion object {
        @Provides
        @Singleton
        fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
