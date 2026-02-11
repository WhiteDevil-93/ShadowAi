package com.shadowai.provideradapters

import android.util.Log
import com.shadowai.core.ProviderId
import com.shadowai.core.providers.Provider
import com.shadowai.core.providers.ModelInfo
import com.shadowai.core.providers.AuthType
import com.shadowai.core.Capability
import com.shadowai.core.security.SecretBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers available models from AI provider APIs and local storage.
 */
@Singleton
class ProviderModelDiscovery @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val localModelEngine: LocalModelEngine,
    private val secretRepository: ProviderSecretRepository,
    private val crudRepository: ProviderCrudRepository,
    private val networkTester: ProviderNetworkTester,
) {
    companion object {
        private const val TAG = "ProviderModelDiscovery"
        private const val NETWORK_TIMEOUT_SECONDS = 30L
    }

    private val localLiquidConfigMutex = Mutex()
    private val networkClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(NETWORK_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(NETWORK_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(NETWORK_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(NETWORK_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetchProviderModels(providerId: ProviderId): List<ModelInfo> {
        val provider = crudRepository.getProviderById(providerId) ?: return emptyList()
        return discoverModels(provider)
    }

    suspend fun discoverModels(provider: Provider): List<ModelInfo> = withContext(Dispatchers.IO) {
        if (provider.id == ProviderId.LIQUID) {
            if (provider.baseUrl.isNotBlank() && provider.baseUrl != "liquid-inference") {
                localLiquidConfigMutex.withLock {
                    localModelEngine.setCustomModelDirectory(provider.baseUrl)
                }
            }
            return@withContext discoverLiquidModels()
        }
        if (provider.id == ProviderId.PIXAI) {
            return@withContext ProviderModelCatalog.getModels(ProviderId.PIXAI)
        }
        if (provider.id == ProviderId.NOVELAI) {
            return@withContext ProviderModelCatalog.getModels(ProviderId.NOVELAI)
        }

        currentCoroutineContext().ensureActive()
        val apiKeyBytes = secretRepository.getApiKey(provider.id.name)?.withSecretBytes { it.copyOf() }
        val apiKey = apiKeyBytes?.let { bytes ->
            String(bytes, Charsets.UTF_8).also {
                java.util.Arrays.fill(bytes, 0.toByte())
            }
        }

        if ((provider.auth.type == AuthType.API_KEY || provider.auth.type == AuthType.OAUTH) && apiKey.isNullOrBlank()) {
            val catalogModels = ProviderModelCatalog.getModels(provider.id)
            if (catalogModels.isNotEmpty()) {
                Log.d(TAG, "No API key for ${provider.id}, returning ${catalogModels.size} catalog defaults")
                return@withContext catalogModels
            }
            if (provider.models.isNotEmpty()) {
                Log.d(TAG, "No API key for ${provider.id}, returning ${provider.models.size} provider defaults")
                return@withContext provider.models
            }
            return@withContext emptyList()
        }

        val base = provider.baseUrl.trimEnd('/')

        if (provider.id == ProviderId.ATLASCLOUD || provider.id == ProviderId.SIRAY) {
            val urls = networkTester.buildModelProbeUrls(base)
            for (url in urls) {
                currentCoroutineContext().ensureActive()
                Log.d(TAG, "Discovering ${provider.id} models via $url")
                try {
                    val requestBuilder = Request.Builder().url(url)
                    if (!apiKey.isNullOrBlank()) {
                        networkTester.appendAuthHeaders(requestBuilder, provider.id, apiKey)
                    }
                    val request = requestBuilder.build()
                    withContext(Dispatchers.IO) {
                        networkClient.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                Log.d(TAG, "Non-success ${response.code} from $url for ${provider.id}")
                                return@use
                            }
                            val responseBody = response.body?.string() ?: return@use
                            val models = parseModelsResponse(responseBody, provider.id)
                            if (models.isNotEmpty()) {
                                return@withContext models
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch ${provider.id} models from $url", e)
                }
            }
            return@withContext emptyList()
        }

        val modelsUrl = when (provider.id) {
            ProviderId.OPENAI, ProviderId.ANTHROPIC, ProviderId.OPENROUTER,
            ProviderId.GROQ, ProviderId.MISTRAL, ProviderId.DEEPSEEK,
            ProviderId.XAI, ProviderId.COHERE, ProviderId.SILICON_FLOW,
            ProviderId.NOVELAI -> "$base/models"
            ProviderId.GEMINI -> "$base/models"
            ProviderId.OLLAMA_CLOUD -> "$base/api/tags"
            ProviderId.LOCAL_TEXT, ProviderId.LOCAL_IMAGE -> {
                if (base.endsWith("/v1")) "$base/models" else "$base/v1/models"
            }
            else -> return@withContext emptyList()
        }

        try {
            val requestBuilder = Request.Builder().url(modelsUrl)
            if (!apiKey.isNullOrBlank()) {
                networkTester.appendAuthHeaders(requestBuilder, provider.id, apiKey)
            }
            val request = requestBuilder.build()

            networkClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()

                val responseBody = response.body?.string() ?: return@withContext emptyList()
                parseModelsResponse(responseBody, provider.id)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    internal fun discoverLiquidModels(): List<ModelInfo> {
        val localModels = try {
            localModelEngine.scanForModels()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to scan Liquid models", e)
            return ProviderModelCatalog.getModels(ProviderId.LIQUID)
        }
        if (localModels.isEmpty()) {
            Log.d(TAG, "No local GGUF models found, returning catalog defaults for LIQUID")
            return ProviderModelCatalog.getModels(ProviderId.LIQUID)
        }
        return localModels.map { toLiquidModelInfo(it) }
    }

    internal fun toLiquidModelInfo(model: LocalModelInfo): ModelInfo {
        val fileName = File(model.path).name.ifBlank { model.path }
        val formatLabel = model.format.ifBlank { "GGUF" }
        return ModelInfo(
            id = fileName,
            displayName = fileName,
            provider = ProviderId.LIQUID,
            notes = "$formatLabel • ${formatBytes(model.size)}",
            capabilities = setOf(Capability.TEXT),
        )
    }

    internal fun formatBytes(bytes: Long): String {
        var size = bytes.toDouble()
        var index = 0
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        while (size >= 1024 && index < units.lastIndex) {
            size /= 1024.0
            index++
        }
        return if (index == 0) {
            "${bytes}B"
        } else {
            String.format(Locale.US, "%.1f%s", size, units[index])
        }
    }

    internal fun parseModelsResponse(json: String, providerId: ProviderId): List<ModelInfo> {
        return try {
            val root = JSONObject(json)
            val data = root.optJSONArray("data")
                ?: root.optJSONArray("models")
                ?: return parseModelIdsFallback(json, providerId)
            (0 until data.length()).mapNotNull { i ->
                val modelObj = data.optJSONObject(i) ?: return@mapNotNull null
                var id = modelObj.optString("id").ifBlank {
                    modelObj.optString("name").ifBlank { "" }
                }
                if (id.isBlank()) return@mapNotNull null
                if (providerId == ProviderId.GEMINI && id.startsWith("models/")) {
                    id = id.removePrefix("models/")
                }
                ModelInfo(
                    id = id,
                    displayName = id,
                    provider = providerId,
                    capabilities = setOf(Capability.TEXT),
                )
            }.ifEmpty { parseModelIdsFallback(json, providerId) }
        } catch (e: JSONException) {
            parseModelIdsFallback(json, providerId)
        }
    }

    internal fun parseModelIdsFallback(json: String, providerId: ProviderId): List<ModelInfo> {
        val ids = collectModelIds(json).distinct()
        if (ids.isEmpty()) return emptyList()
        return ids.mapNotNull { rawId ->
            val id = normalizeModelId(providerId, rawId)
            if (id.isBlank()) null
            else ModelInfo(
                id = id,
                displayName = id,
                provider = providerId,
                capabilities = setOf(Capability.TEXT),
            )
        }
    }

    internal fun normalizeModelId(providerId: ProviderId, id: String): String {
        val trimmed = id.trim()
        if (trimmed.isBlank()) return ""
        return if (providerId == ProviderId.GEMINI && trimmed.startsWith("models/")) {
            trimmed.removePrefix("models/")
        } else {
            trimmed
        }
    }

    internal fun collectModelIds(json: String): List<String> {
        val trimmed = json.trim()
        if (trimmed.isBlank()) return emptyList()
        val results = linkedSetOf<String>()
        try {
            if (trimmed.startsWith("[")) {
                collectFromArray(JSONArray(trimmed), results, 0)
            } else if (trimmed.startsWith("{")) {
                collectFromObject(JSONObject(trimmed), results, 0)
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return results.toList()
    }

    private fun collectFromObject(obj: JSONObject, sink: MutableSet<String>, depth: Int) {
        if (depth > 32) return
        val modelKeys = setOf("id", "model", "model_id", "modelId", "model_name", "slug")
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.opt(key)
            when (value) {
                is JSONObject -> collectFromObject(value, sink, depth + 1)
                is JSONArray -> collectFromArray(value, sink, depth + 1)
                is String -> {
                    if (modelKeys.contains(key)) {
                        sink += value
                    }
                }
            }
        }
    }

    private fun collectFromArray(array: JSONArray, sink: MutableSet<String>, depth: Int) {
        if (depth > 32) return
        for (i in 0 until array.length()) {
            val value = array.opt(i)
            when (value) {
                is JSONObject -> collectFromObject(value, sink, depth + 1)
                is JSONArray -> collectFromArray(value, sink, depth + 1)
                is String -> sink += value
            }
        }
    }
}
