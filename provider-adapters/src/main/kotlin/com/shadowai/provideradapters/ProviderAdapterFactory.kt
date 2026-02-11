package com.shadowai.provideradapters

import com.shadowai.core.LocalInferenceEngine
import com.shadowai.core.ProviderId
import com.shadowai.core.Transform
import com.google.gson.Gson
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import com.shadowai.provideradapters.RetryConfig
import com.shadowai.provideradapters.withRetry
import com.shadowai.provideradapters.AnthropicAdapter
import com.shadowai.provideradapters.GeminiAdapter
import com.shadowai.provideradapters.PixAIAdapter
import com.shadowai.provideradapters.NovitaAdapter
import com.shadowai.provideradapters.NovelAIAdapter
import com.shadowai.provideradapters.FluxAdapter
import com.shadowai.provideradapters.ReplicateAdapter
import com.shadowai.provideradapters.OllamaCloudAdapter
import com.shadowai.provideradapters.LocalLlamaAdapter
import com.shadowai.provideradapters.OpenAICompatibleAdapter

/**
 * Factory for creating provider adapters at runtime.
 * Implements the Abstract Factory pattern for adapter creation.
 * Now supports Hilt dependency injection for LocalInferenceEngine.
 */
@Singleton
class ProviderAdapterFactory @Inject constructor(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val inferenceEngine: LocalInferenceEngine?
) {
    private val adapterCache = ConcurrentHashMap<ProviderId, ProviderAdapter>()

    /**
     * Creates or retrieves a cached adapter for the given provider.
     */
    fun getAdapter(config: ProviderAdapterConfig): ProviderAdapter {
        return adapterCache.getOrPut(config.providerId) {
            createAdapter(config)
        }
    }

    /**
     * Creates a new adapter for the given configuration.
     * LocalLlamaAdapter receives LocalInferenceEngine via DI for proper cross-module access.
     */
    private fun createAdapter(config: ProviderAdapterConfig): ProviderAdapter {
        return when (config.providerId) {
            ProviderId.LOCAL_TEXT,
            ProviderId.LOCAL_IMAGE,
            ProviderId.LIQUID -> LocalLlamaAdapter(config, inferenceEngine)

            ProviderId.OPENAI,
            ProviderId.OPENROUTER,
            ProviderId.GROQ,
            ProviderId.COHERE,
            ProviderId.SILICON_FLOW,
            ProviderId.MISTRAL,
            ProviderId.DEEPSEEK,
            ProviderId.XAI,
            ProviderId.ATLASCLOUD,
            ProviderId.SIRAY -> OpenAICompatibleAdapter(config, httpClient, gson)

            ProviderId.ANTHROPIC -> AnthropicAdapter(config, httpClient, gson)

            ProviderId.GEMINI -> GeminiAdapter(config, httpClient, gson)

            ProviderId.PIXAI -> PixAIAdapter(config, httpClient, gson)

            ProviderId.NOVITA -> NovitaAdapter(config, httpClient, gson)

            ProviderId.NOVELAI -> NovelAIAdapter(config, httpClient, gson)

            ProviderId.FLUX -> FluxAdapter(config, httpClient, gson)

            ProviderId.REPLICATE -> ReplicateAdapter(config, httpClient, gson)

            ProviderId.OLLAMA_CLOUD -> OllamaCloudAdapter(config, httpClient, gson)

            ProviderId.UNKNOWN -> throw IllegalArgumentException("Unknown provider: ${config.providerId}")
        }
    }

    /**
     * Removes an adapter from the cache.
     */
    fun removeAdapter(providerId: ProviderId) {
        adapterCache.remove(providerId)
    }

    /**
     * Clears all cached adapters.
     */
    fun clearCache() {
        adapterCache.clear()
    }

    /**
     * Gets all cached adapters.
     */
    fun getAllAdapters(): List<ProviderAdapter> = adapterCache.values.toList()
}
