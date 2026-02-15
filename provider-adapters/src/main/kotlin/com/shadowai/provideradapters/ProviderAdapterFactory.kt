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
import java.security.MessageDigest

/**
 * Factory for creating provider adapters at runtime.
 * Implements the Abstract Factory pattern for adapter creation.
 * Now supports Hilt dependency injection for LocalInferenceEngine.
 *
 * M-4 FIX: Implements LRU cache with size limit for adapter caching.
 * Uses LinkedHashMap with accessOrder=true for LRU eviction when max size exceeded.
 *
 * H-6 FIX: Hash-based cache key that includes full configuration.
 * Cache is invalidated when config properties change (baseUrl, modelId, apiKeySecret).
 */
@Singleton
class ProviderAdapterFactory @Inject constructor(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val inferenceEngine: LocalInferenceEngine?
) {
    companion object {
        private const val MAX_CACHE_SIZE = 50 // Maximum number of adapters to cache
    }

    // M-4 FIX: LRU cache using LinkedHashMap with accessOrder=true
    // H-6 FIX: Hash-based cache key that includes configuration
    // Eviction occurs when size exceeds MAX_CACHE_SIZE
    private val adapterCache = object : LinkedHashMap<String, ProviderAdapter>(
        MAX_CACHE_SIZE, 0.75f, true // accessOrder=true for LRU
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ProviderAdapter>?): Boolean {
            val shouldRemove = size > MAX_CACHE_SIZE
            if (shouldRemove) {
                // Log eviction for observability
                println("ProviderAdapterFactory: Evicting oldest adapter with key ${eldest?.key}")
                // H-8 FIX: Shutdown adapter on eviction to release resources (e.g. unload local models)
                eldest?.value?.shutdown()
            }
            return shouldRemove
        }
    }

    // Thread-safe lock for cache operations
    private val cacheLock = Object()

    /**
     * H-6 FIX: Calculate a stable hash-based cache key for the configuration.
     * This ensures cache invalidation when configuration changes.
     *
     * The key includes:
     * - providerId
     * - baseUrl (endpoint can change)
     * - modelId (different model = different adapter)
     * - apiKeySecret (content hash for security + invalidation on key change)
     *
     * @param config Provider adapter configuration
     * @return Stable cache key string
     */
    private fun computeCacheKey(config: ProviderAdapterConfig): String {
        val baseKey = "${config.providerId}|${config.baseUrl}|${config.modelId}"
        val baseDigest = MessageDigest.getInstance("SHA-256")
            .digest(baseKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        // H-7 FIX: Use content hash of SecretBytes instead of identity hash
        // SecretBytes does not implement hashCode(), so we must hash the content securely
        val keyHash = config.apiKeySecret?.let { secret ->
            secret.withSecretBytes { bytes ->
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(bytes)
                digest.joinToString("") { "%02x".format(it) }
            }
        } ?: "no-key"

        return "${baseDigest}_$keyHash"
    }

    /**
     * Creates or retrieves a cached adapter for the given provider.
     * Thread-safe with LRU eviction when max size exceeded.
     *
     * H-6 FIX: Uses hash-based cache key that includes full configuration.
     * Cache is invalidated when configuration properties change.
     */
    fun getAdapter(config: ProviderAdapterConfig): ProviderAdapter {
        val cacheKey = computeCacheKey(config)

        synchronized(cacheLock) {
            val cached = adapterCache[cacheKey]
            if (cached != null) {
                return cached
            }
            return createAdapter(config).also { newAdapter ->
                // Ensure the key is computed exactly the same way
                // No need to recompute if logic is deterministic, but safe to do so
                adapterCache[cacheKey] = newAdapter
            }
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
     * Removes all adapters for a given provider from the cache.
     * H-6 FIX: Clears all cache entries matching the providerId prefix.
     */
    fun removeAdapter(providerId: ProviderId) {
        synchronized(cacheLock) {
            val iterator = adapterCache.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value.providerId == providerId) {
                    // H-8 FIX: Shutdown adapter on removal
                    entry.value.shutdown()
                    iterator.remove()
                }
            }
        }
    }

    /**
     * H-6 FIX: Invalidate cache entries for a specific provider configuration.
     * More granular invalidation when specific config properties change.
     *
     * @param config Configuration to invalidate
     */
    fun invalidateAdapter(config: ProviderAdapterConfig) {
        val cacheKey = computeCacheKey(config)
        synchronized(cacheLock) {
            val removed = adapterCache.remove(cacheKey)
            // H-8 FIX: Shutdown adapter on invalidation
            removed?.shutdown()
        }
    }

    /**
     * H-6 FIX: Invalidate all adapters matching a provider and model ID.
     * Useful when provider configuration changes.
     *
     * @param providerId Provider to invalidate
     * @param modelId Model ID (optional - clears all models if null)
     */
    fun invalidateProviderModel(providerId: ProviderId, modelId: String? = null) {
        synchronized(cacheLock) {
            val iterator = adapterCache.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value.providerId == providerId) {
                    if (modelId == null || entry.value.config.modelId == modelId) {
                        // H-8 FIX: Shutdown adapter on invalidation
                        entry.value.shutdown()
                        iterator.remove()
                    }
                }
            }
        }
    }

    /**
     * Clears all cached adapters.
     */
    fun clearCache() {
        synchronized(cacheLock) {
            // H-8 FIX: Shutdown all adapters before clearing
            adapterCache.values.forEach { it.shutdown() }
            adapterCache.clear()
        }
    }

    /**
     * Gets all cached adapters.
     */
    fun getAllAdapters(): List<ProviderAdapter> {
        synchronized(cacheLock) {
            return adapterCache.values.toList()
        }
    }

    /**
     * Get current cache size.
     */
    fun getCacheSize(): Int {
        synchronized(cacheLock) {
            return adapterCache.size
        }
    }

    /**
     * Get maximum cache size.
     */
    fun getMaxCacheSize(): Int = MAX_CACHE_SIZE
}
