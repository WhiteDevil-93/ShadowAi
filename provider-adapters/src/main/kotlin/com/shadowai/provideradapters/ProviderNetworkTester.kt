package com.shadowai.provideradapters

import android.util.Log
import com.shadowai.core.ProviderId
import com.shadowai.core.providers.Provider
import com.shadowai.core.providers.ApiStyle
import com.shadowai.core.security.SecretBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tests network connectivity to AI provider endpoints.
 */
@Singleton
class ProviderNetworkTester @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val secretRepository: ProviderSecretRepository,
) {
    companion object {
        private const val TAG = "ProviderNetTest"
        private const val NETWORK_TIMEOUT_SECONDS = 30L
    }

    private val networkClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(NETWORK_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(NETWORK_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(NETWORK_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(NETWORK_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    suspend fun testConnection(provider: Provider): Boolean = withContext(Dispatchers.IO) {
        if (provider.id == ProviderId.LIQUID) return@withContext true
        if (provider.id == ProviderId.PIXAI || provider.id == ProviderId.NOVELAI) {
            return@withContext secretRepository.getApiKey(provider.id.name) != null
        }

        currentCoroutineContext().ensureActive()
        val apiKeyBytes = secretRepository.getApiKey(provider.id.name)?.withSecretBytes { it.copyOf() } ?: return@withContext false
        val apiKey = String(apiKeyBytes, Charsets.UTF_8)
        java.util.Arrays.fill(apiKeyBytes, 0.toByte())
        val base = provider.baseUrl.trimEnd('/')

        if (provider.id == ProviderId.ATLASCLOUD || provider.id == ProviderId.SIRAY) {
            val urls = buildModelProbeUrls(base)
            for (url in urls) {
                currentCoroutineContext().ensureActive()
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .header("X-API-Key", apiKey)
                    .build()
                try {
                    networkClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) return@withContext true
                    }
                } catch (_: Exception) {
                    // try next
                }
            }
            return@withContext false
        }

        val probeUrl = when (provider.id) {
            ProviderId.ANTHROPIC -> "$base/messages"
            ProviderId.OLLAMA_CLOUD -> "$base/api/tags"
            ProviderId.LOCAL_TEXT, ProviderId.LOCAL_IMAGE -> {
                if (base.endsWith("/v1")) "$base/models" else "$base/v1/models"
            }
            ProviderId.OPENAI, ProviderId.OPENROUTER, ProviderId.GEMINI, ProviderId.GROQ,
            ProviderId.MISTRAL, ProviderId.DEEPSEEK, ProviderId.XAI, ProviderId.COHERE,
            ProviderId.SILICON_FLOW, ProviderId.ATLASCLOUD, ProviderId.SIRAY,
            ProviderId.NOVELAI -> "$base/models"
            else -> return@withContext false
        }

        return@withContext try {
            val requestBuilder = Request.Builder().url(probeUrl)
            appendAuthHeaders(requestBuilder, provider.id, apiKey)
            networkClient.newCall(requestBuilder.build()).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    internal fun appendAuthHeaders(builder: Request.Builder, providerId: ProviderId, apiKey: String) {
        if (apiKey.isBlank()) return
        when (providerId) {
            ProviderId.GEMINI -> builder.header("x-goog-api-key", apiKey)
            ProviderId.ATLASCLOUD, ProviderId.SIRAY -> {
                builder.header("Authorization", "Bearer $apiKey")
                builder.header("X-API-Key", apiKey)
            }
            else -> builder.header("Authorization", "Bearer $apiKey")
        }
    }

    internal fun buildModelProbeUrls(base: String): List<String> {
        val normalized = base.trimEnd('/')
        val urls = mutableListOf(
            "$normalized/models",
            "$normalized/model",
            "$normalized/models/list",
            "$normalized/model/list",
            "$normalized/models/available",
        )
        if (!normalized.endsWith("/v1")) {
            urls += "$normalized/v1/models"
        }
        return urls.distinct()
    }
}
