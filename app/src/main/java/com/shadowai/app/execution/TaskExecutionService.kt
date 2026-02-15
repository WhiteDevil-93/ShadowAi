package com.shadowai.app.execution

import com.shadowai.app.models.ModelId
import com.shadowai.core.providers.ActiveProviderConfig
import com.shadowai.core.providers.ApiStyle
import com.shadowai.app.providers.ProviderFallbackManager
import com.shadowai.app.providers.AdapterBridge
import com.shadowai.app.routing.ExecutionSource
import com.shadowai.app.routing.RoutingDecision
import com.shadowai.app.tasks.Task
import com.shadowai.app.tasks.TaskType
import com.shadowai.core.Artifact
import com.shadowai.core.Transform
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service responsible for AI model selection and task execution coordination.
 * Refactored to use the unified Adapter architecture (Phase 2)
 * and Normalized Artifact System (Phase 3).
 * 
 * L-4: Removed deprecated LocalBrainManager dependency - now uses ProviderSelector
 * through ProviderFallbackManager for all provider configuration.
 */
@Singleton
class TaskExecutionService @Inject constructor(
    private val providerFallbackManager: ProviderFallbackManager,
    private val adapterBridge: AdapterBridge
) {
    companion object {
        private const val TAG = "TaskExecutionService"
        private const val MODEL_LOCAL_CHAT = "llama-2-7b-chat"
        private const val MODEL_LOCAL_WRITING = "llama-2-13b-chat"
        private const val MODEL_LOCAL_IMAGE = "stable-diffusion-1.5"
        private const val MODEL_LOCAL_VIDEO = "stable-video-1-0"
        private const val MODEL_LOCAL_AUDIO = "bark-small"

        private const val MODEL_CLOUD_DEFAULT = "gpt-3.5-turbo"
        private const val MODEL_CLOUD_ADVANCED = "gpt-4"
        private const val MODEL_CLOUD_IMAGE = "dall-e-3"
    }

    /**
     * Selects the appropriate AI model based on task type and routing decision.
     */
    fun selectModel(task: Task, routingDecision: RoutingDecision): ModelId {
        return when (routingDecision.selectedSource) {
            ExecutionSource.LOCAL -> selectLocalModel(task)
            ExecutionSource.CLOUD -> selectCloudModel(task)
        }
    }

    /**
     * Executes a task using the unified adapter architecture with automatic fallback.
     */
    suspend fun executeTask(
        task: Task,
        routingDecision: RoutingDecision,
        providerConfig: ActiveProviderConfig
    ): String {
        val shouldCheckFallback = isLocalStyle(providerConfig.apiStyle)

        if (shouldCheckFallback) {
            val fallbackResult = providerFallbackManager.selectProvider(task.type, preferLocal = true)

            if (fallbackResult == null) {
                throw IllegalStateException("No providers available for task type: ${task.type}")
            }

            val effectiveConfig = if (fallbackResult.isFallback) {
                Log.i(TAG, "Provider fallback triggered: ${fallbackResult.fallbackReason}")
                fallbackResult.config
            } else {
                providerConfig
            }

            return executeWithConfig(task, effectiveConfig)
        }

        return executeWithConfig(task, providerConfig)
    }

    private suspend fun executeWithConfig(task: Task, config: ActiveProviderConfig): String {
        val transform = when (task.type) {
            TaskType.IMAGE_GEN -> Transform.TextToImage()
            TaskType.VIDEO_GEN -> Transform.TextToVideo()
            TaskType.AUDIO_GEN -> Transform.TextToAudio()
            else -> Transform.TextToText()
        }

        // Normalize input to Artifact
        val inputArtifact = Artifact.Text.create(content = task.input)

        val parameters = mapOf(
            "maxTokens" to task.budget.maxTokens,
            "temperature" to 0.7
        )

        return try {
            val result = adapterBridge.execute(config, transform, inputArtifact, parameters)
            if (result.isSuccess) {
                val output = result.getOrThrow()
                // Handle different output types (String for text, URL for images/videos)
                when (output) {
                    is String -> output
                    is com.shadowai.provideradapters.NovitaImageResult -> output.imageUrls.firstOrNull() ?: ""
                    is com.shadowai.provideradapters.PixAIAdapter.PixAIImageResult -> output.imageUrl
                    is com.shadowai.provideradapters.FluxAdapter.FluxGenerationResult -> output.imageUrls.firstOrNull() ?: ""
                    else -> output.toString()
                }
            } else {
                throw result.exceptionOrNull() ?: Exception("Unknown execution error")
            }
        } catch (e: java.io.IOException) {
            if (isLocalStyle(config.apiStyle)) {
                Log.w(TAG, "Local execution failed with IO error, attempting cloud fallback", e)
                attemptCloudFallback(task, e.message ?: "Unknown error")
            } else {
                throw e
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Execution failed - security exception", e)
            throw e
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Execution failed with illegal state", e)
            throw e
        }
    }

    private suspend fun attemptCloudFallback(task: Task, failureReason: String): String {
        val cloudCandidates = providerFallbackManager.getCloudFallbackChain(task.type)
        if (cloudCandidates.isEmpty()) {
            throw IllegalStateException("Local failed ($failureReason) and no cloud fallback available")
        }

        val failureSummaries = mutableListOf<String>()
        var lastFailure: Throwable? = null

        cloudCandidates.forEachIndexed { index, config ->
            try {
                Log.i(
                    TAG,
                    "Falling back to cloud provider ${index + 1}/${cloudCandidates.size}: ${config.providerId}"
                )
                return executeWithConfig(task, config)
            } catch (e: Exception) {
                lastFailure = e
                failureSummaries += "${config.providerId}: ${e.message ?: e::class.java.simpleName}"
                Log.w(TAG, "Cloud fallback failed for ${config.providerId}", e)
            }
        }

        throw IllegalStateException(
            "Local failed ($failureReason) and all cloud fallbacks failed: ${failureSummaries.joinToString(" | ")}",
            lastFailure
        )
    }

    private fun selectLocalModel(task: Task): ModelId {
        return when (task.type) {
            TaskType.IMAGE_GEN -> ModelId(MODEL_LOCAL_IMAGE)
            TaskType.VIDEO_GEN -> ModelId(MODEL_LOCAL_VIDEO)
            TaskType.AUDIO_GEN -> ModelId(MODEL_LOCAL_AUDIO)
            TaskType.WRITING -> ModelId(MODEL_LOCAL_WRITING)
            else -> ModelId(MODEL_LOCAL_CHAT)
        }
    }

    private fun selectCloudModel(task: Task): ModelId {
        return when (task.type) {
            TaskType.IMAGE_GEN -> ModelId(MODEL_CLOUD_IMAGE)
            TaskType.WRITING -> ModelId(MODEL_CLOUD_ADVANCED)
            else -> ModelId(MODEL_CLOUD_DEFAULT)
        }
    }

    private fun isLocalStyle(style: ApiStyle): Boolean {
        return style == ApiStyle.LOCAL_IMAGE || style == ApiStyle.LOCAL_TEXT || style == ApiStyle.LIQUID
    }
}
