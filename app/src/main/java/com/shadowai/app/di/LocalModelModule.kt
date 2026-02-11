package com.shadowai.app.di

import com.shadowai.provideradapters.LocalModelEngine
import com.shadowai.app.ai.LocalLiquidModelEngineAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds LocalLiquidModelEngineAdapter to LocalModelEngine interface.
 * This allows the :app module to provide its local model implementation
 * to the :provider-adapters module which depends on the LocalModelEngine interface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocalModelModule {
    
    @Singleton
    @Binds
    abstract fun bindLocalModelEngine(
        impl: LocalLiquidModelEngineAdapter
    ): LocalModelEngine
}
