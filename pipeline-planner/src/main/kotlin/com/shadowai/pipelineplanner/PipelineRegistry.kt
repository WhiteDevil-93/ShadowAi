package com.shadowai.pipelineplanner

import com.shadowai.provideradapters.ProviderAdapterFactory
import com.shadowai.provideradapters.ProviderAdapterConfig
import com.shadowai.core.ProviderId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry that populates the PipelinePlanner with all available adapters.
 */
@Singleton
class PipelineRegistry @Inject constructor(
    private val planner: PipelinePlanner,
    private val adapterFactory: ProviderAdapterFactory
) {
    /**
     * Initializes the registry by registering all supported provider adapters.
     */
    suspend fun initialize() {
        // Register standard cloud and local adapters to build the transformation graph
        val providers = listOf(
            ProviderId.OPENAI,
            ProviderId.ANTHROPIC,
            ProviderId.GEMINI,
            ProviderId.FLUX,
            ProviderId.LOCAL_TEXT,
            ProviderId.LIQUID
        )

        providers.forEach { providerId ->
            // Use a dummy config to get the adapter for registration
            val adapter = adapterFactory.getAdapter(ProviderAdapterConfig(providerId))
            planner.registerExecutor(adapter)
        }
    }
}
