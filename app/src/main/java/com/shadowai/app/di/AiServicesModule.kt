package com.shadowai.app.di

import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.app.ai.*
import com.shadowai.app.execution.*
import com.shadowai.app.providers.AdapterBridge
import com.shadowai.app.providers.ProviderFallbackManager
import com.shadowai.core.security.PiiMaskingProcessor
import com.shadowai.pipelineplanner.PipelineExecutor
import com.shadowai.pipelineplanner.PipelinePlanner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency Injection module for AI-related services.
 * Refactored for the unified adapter architecture and pipeline planning.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiServicesModule {

    @Provides
    @Singleton
    fun provideTaskExecutionService(
        providerFallbackManager: ProviderFallbackManager,
        localBrainManager: LocalBrainManager,
        adapterBridge: AdapterBridge
    ): TaskExecutionService {
        return TaskExecutionService(
            providerFallbackManager,
            localBrainManager,
            adapterBridge
        )
    }

    @Provides
    @Singleton
    fun provideHybridAiExecutor(
        localBrainManager: LocalBrainManager,
        adminRepository: AdminRepository,
        memoryManager: MemoryManager,
        promptManager: PromptManager,
        adapterBridge: AdapterBridge,
        heuristicParser: HeuristicActionParser,
        pipelinePlanner: PipelinePlanner,
        safetySettingsManager: com.shadowai.app.ai.SafetySettingsManager,
        piiMaskingProcessor: PiiMaskingProcessor,
        gson: com.google.gson.Gson,
        pipelineExecutor: PipelineExecutor,
        adapterProviderExecutor: AdapterProviderExecutor
    ): HybridAiExecutor {
        return HybridAiExecutor(
            localBrainManager,
            adminRepository,
            memoryManager,
            promptManager,
            adapterBridge,
            heuristicParser,
            pipelinePlanner,
            safetySettingsManager,
            piiMaskingProcessor,
            gson,
            pipelineExecutor,
            adapterProviderExecutor
        )
    }

    // TaskExecutor and RoutingEngine are provided via @Binds in AppBindings
    // LocalLlmExecutor and CloudLlmExecutor are now deprecated and their provides methods removed.
}
