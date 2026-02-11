package com.shadowai.app.execution

import com.shadowai.app.providers.ActiveProviderConfig
import com.shadowai.app.providers.AdapterBridge
import com.shadowai.app.providers.ProviderSelector
import com.shadowai.app.tasks.TaskType
import com.shadowai.core.Artifact
import com.shadowai.core.Modality
import com.shadowai.core.ProviderExecutor
import com.shadowai.core.Transform
import com.shadowai.pipelineplanner.PipelinePlanner
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdapterProviderExecutor @Inject constructor(
    private val bridge: AdapterBridge,
    private val providerSelector: ProviderSelector
) : PipelinePlanner.ProviderExecutorSource {

    override suspend fun getBestExecutor(transform: Transform): ProviderExecutor? {
        val taskType = when (transform.targetModality) {
            Modality.Image -> TaskType.IMAGE_GEN
            Modality.Audio -> TaskType.AUDIO_GEN
            Modality.Video -> TaskType.VIDEO_GEN
            else -> TaskType.WRITING
        }

        // Try cloud first (assuming network availability), then local
        val cloudConfig = providerSelector.nextCloud(taskType)
        val localConfig = providerSelector.nextLocal(taskType)

        return when {
            cloudConfig != null && localConfig != null -> 
                FallbackExecutor(bridge, cloudConfig, localConfig)
            cloudConfig != null -> 
                SingleExecutor(bridge, cloudConfig)
            localConfig != null -> 
                SingleExecutor(bridge, localConfig)
            else -> null
        }
    }

    private class SingleExecutor(
        private val bridge: AdapterBridge,
        private val config: ActiveProviderConfig
    ) : ProviderExecutor {
        override val providerId = config.providerId
        override suspend fun isAvailable() = true
        override suspend fun canExecute(t: Transform) = true
        override suspend fun execute(
            t: Transform, 
            i: Artifact, 
            p: Map<String, Any>
        ): Result<Artifact> {
            val adapter = bridge.getAdapter(config)
            return adapter.execute(t, i, p)
        }
    }

    private class FallbackExecutor(
        private val bridge: AdapterBridge,
        private val primary: ActiveProviderConfig,
        private val secondary: ActiveProviderConfig
    ) : ProviderExecutor {
        override val providerId = primary.providerId 
        override suspend fun isAvailable() = true
        override suspend fun canExecute(t: Transform) = true
        override suspend fun execute(
            t: Transform, 
            i: Artifact, 
            p: Map<String, Any>
        ): Result<Artifact> {
            val primaryAdapter = bridge.getAdapter(primary)
            val primaryResult = primaryAdapter.execute(t, i, p)
            
            if (primaryResult.isSuccess) return primaryResult
            
            // Fallback Logic
            val secondaryAdapter = bridge.getAdapter(secondary)
            return secondaryAdapter.execute(t, i, p)
        }
    }
}
