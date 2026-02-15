package com.shadowai.app.di

import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.app.ai.*
import com.shadowai.app.execution.*
import com.shadowai.app.providers.AdapterBridge
import com.shadowai.app.providers.ProviderFallbackManager
import com.shadowai.core.security.PiiMaskingProcessor
import com.shadowai.pipelineplanner.PipelineExecutor
import com.shadowai.pipelineplanner.PipelinePlanner
import dagger.Binds
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
abstract class AiServicesModule {
    // HybridAiExecutor has @Inject constructor — Hilt auto-provides it.
    // No @Binds needed unless binding to a supertype interface.

    companion object {
        // TaskExecutionService is deleted.
    }
}
