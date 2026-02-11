package com.shadowai.app.di

import com.shadowai.app.ai.MemoryManager
import com.shadowai.app.ai.PromptManager
import com.shadowai.core.security.PiiMaskingProcessor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PromptModule {
    @Provides
    @Singleton
    fun providePromptManager(
        memoryManager: MemoryManager,
        piiMaskingProcessor: PiiMaskingProcessor
    ): PromptManager {
        return PromptManager(memoryManager, piiMaskingProcessor)
    }
}
