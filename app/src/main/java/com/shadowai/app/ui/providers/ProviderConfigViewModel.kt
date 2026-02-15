package com.shadowai.app.ui.providers

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.app.ai.ModelTreeUriConfigurable
import com.shadowai.app.providers.ModelInfo
import com.shadowai.app.providers.Provider
import com.shadowai.core.ProviderId
// REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
import com.shadowai.provideradapters.ProviderCrudRepository
import com.shadowai.provideradapters.ProviderSecretRepository
import com.shadowai.provideradapters.ProviderModelRepository
import com.shadowai.provideradapters.ProviderModelDiscovery
import com.shadowai.provideradapters.ProviderNetworkTester
import com.shadowai.core.security.discoveredApiKey
import com.shadowai.core.security.toSecretBytes
import com.shadowai.core.LocalInferenceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
// FIX: Add missing imports
import com.shadowai.app.providers.ProviderAuth
import com.shadowai.app.providers.AuthType
import com.shadowai.app.providers.Capability

data class ProviderConfigUiState(
    val provider: Provider? = null,
    val hasSavedApiKey: Boolean = false,
    val availableModels: List<ModelInfo> = emptyList(),
    val selectedModelIds: List<String> = emptyList(),
    val isLoadingModels: Boolean = false,
    val isTesting: Boolean = false,
    val testSuccess: Boolean? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ProviderConfigViewModel @Inject constructor(
    // REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
    private val crudRepository: ProviderCrudRepository,
    private val secretRepository: ProviderSecretRepository,
    private val modelRepository: ProviderModelRepository,
    private val modelDiscovery: ProviderModelDiscovery,
    private val networkTester: ProviderNetworkTester,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context,
    private val localInferenceEngine: LocalInferenceEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderConfigUiState())
    val uiState: StateFlow<ProviderConfigUiState> = _uiState.asStateFlow()

    private var lastTestTime = 0L
    private val TEST_COOLDOWN_MS = 5000L

    // ... (rest of implementation remains similar, but omitting unchanged methods to save tokens)

    fun testApiKey(providerId: ProviderId, apiKey: String) {
        val now = System.currentTimeMillis()
        if (now - lastTestTime < TEST_COOLDOWN_MS) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Please wait ${(TEST_COOLDOWN_MS - (now - lastTestTime)) / 1000}s before testing again"
            )
            return
        }
        lastTestTime = now

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true, testSuccess = null, errorMessage = null)
            try {
                val isValid = testProviderApiKey(providerId, apiKey)
                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    testSuccess = isValid,
                    errorMessage = if (isValid) null else "API key validation failed for ${providerId.name}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    testSuccess = false,
                    errorMessage = "Test failed: ${e.message}"
                )
            }
        }
    }

    private suspend fun testProviderApiKey(providerId: ProviderId, apiKey: String): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val endpoint = getTestEndpoint(providerId)
                val requestBuilder = okhttp3.Request.Builder()
                    .url(endpoint.url)
                    .get()

                endpoint.headers.forEach { (header, template) ->
                    val value = if (template == "__API_KEY__") apiKey else template.replace("__API_KEY__", apiKey)
                    requestBuilder.addHeader(header, value)
                }

                okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    private data class TestEndpoint(
        val url: String,
        val headers: Map<String, String> = emptyMap()
    )

    private fun getTestEndpoint(providerId: ProviderId): TestEndpoint {
        return when (providerId) {
            ProviderId.OPENAI -> TestEndpoint("https://api.openai.com/v1/models", mapOf("Authorization" to "Bearer __API_KEY__"))
            ProviderId.ANTHROPIC -> TestEndpoint("https://api.anthropic.com/v1/models", mapOf("x-api-key" to "__API_KEY__"))
            ProviderId.GEMINI -> TestEndpoint("https://generativelanguage.googleapis.com/v1beta/models", mapOf("x-goog-api-key" to "__API_KEY__"))
            ProviderId.OPENROUTER -> TestEndpoint("https://openrouter.ai/api/v1/models", mapOf("Authorization" to "Bearer __API_KEY__"))
            ProviderId.GROQ -> TestEndpoint("https://api.groq.com/openai/v1/models", mapOf("Authorization" to "Bearer __API_KEY__"))
            ProviderId.MISTRAL -> TestEndpoint("https://api.mistral.ai/v1/models", mapOf("Authorization" to "Bearer __API_KEY__"))
            ProviderId.DEEPSEEK -> TestEndpoint("https://api.deepseek.com/v1/models", mapOf("Authorization" to "Bearer __API_KEY__"))
            ProviderId.XAI -> TestEndpoint("https://api.x.ai/v1/models", mapOf("Authorization" to "Bearer __API_KEY__"))
            ProviderId.COHERE -> TestEndpoint("https://api.cohere.ai/v1/models", mapOf("Authorization" to "Bearer __API_KEY__"))
            ProviderId.SILICON_FLOW -> TestEndpoint("https://api.siliconflow.cn/v1/models", mapOf("Authorization" to "Bearer __API_KEY__"))
            ProviderId.NOVITA -> TestEndpoint("https://api.novita.ai/v3/model", mapOf("Authorization" to "Bearer __API_KEY__"))
            ProviderId.PIXAI -> TestEndpoint("https://api.pixai.art/v1/models", mapOf("Authorization" to "Bearer __API_KEY__"))
            ProviderId.NOVELAI -> TestEndpoint("https://api.novelai.net/user/information", mapOf("Authorization" to "Bearer __API_KEY__"))
            ProviderId.OLLAMA_CLOUD -> TestEndpoint("http://localhost:11434/api/tags", mapOf("Authorization" to "Bearer __API_KEY__"))
            ProviderId.SIRAY, ProviderId.ATLASCLOUD -> {
                val provider = _uiState.value.provider
                val base = provider?.baseUrl?.trimEnd('/') ?: "http://localhost:8080"
                TestEndpoint("$base/models", mapOf("Authorization" to "Bearer __API_KEY__"))
            }
            else -> TestEndpoint("https://api.openai.com/v1/models", mapOf("Authorization" to "Bearer __API_KEY__"))
        }
    }

    fun loadConfig(providerId: ProviderId) {
        viewModelScope.launch {
            val provider = crudRepository.getProvider(providerId)?.toAppProvider()
            val hasSavedApiKey = secretRepository.getApiKey(providerId.name)?.use { _ -> true } ?: false
            val selectedModels = modelRepository.getSelectedModels(providerId)

            val defaultModels = when (providerId) {
                ProviderId.LIQUID -> emptyList()
                else -> provider?.models ?: emptyList()
            }

            _uiState.value = _uiState.value.copy(
                provider = provider,
                hasSavedApiKey = hasSavedApiKey,
                selectedModelIds = selectedModels,
                availableModels = defaultModels
            )

            provider?.baseUrl?.let { url ->
                if (url.startsWith("SAF:")) {
                    val uriString = url.removePrefix("SAF:")
                    try {
                        val uri = Uri.parse(uriString)
                        val tree = DocumentFile.fromTreeUri(context, uri)
                        if (tree != null && tree.exists()) {
                            applyModelTreeUri(uri)
                        }
                    } catch (e: Exception) {
                    }
                }
            }

            discoverModels(providerId)
        }
    }

    private fun ProviderId.isCloud(): Boolean {
        return when (this) {
            ProviderId.LOCAL_IMAGE, ProviderId.LOCAL_TEXT, ProviderId.LIQUID -> false
            else -> true
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun updateApiKey(providerId: ProviderId, apiKey: String) {
        _uiState.value = _uiState.value.copy(
            testSuccess = null,
            errorMessage = null
        )
    }

    fun saveConfig(providerId: ProviderId, apiKey: String) {
        viewModelScope.launch {
            if (apiKey.isBlank()) {
                secretRepository.clearApiKey(providerId.name)
            } else {
                discoveredApiKey(apiKey).use { secret ->
                    secretRepository.saveApiKey(providerId.name, secret)
                }
            }
            _uiState.value = _uiState.value.copy(hasSavedApiKey = apiKey.isNotBlank())
            loadConfig(providerId)
        }
    }

    fun updateProviderBaseUrl(providerId: ProviderId, baseUrl: String) {
        viewModelScope.launch {
            val current = crudRepository.getProvider(providerId) ?: return@launch
            val appProvider = current.toAppProvider()
            val updated = appProvider.copy(
                baseUrl = baseUrl,
                enabled = if (providerId.isCloud()) appProvider.enabled else (appProvider.enabled || baseUrl.isNotBlank())
            )
            crudRepository.saveProvider(updated.toCoreProvider())
            loadConfig(providerId)
        }
    }

    fun discoverModels(providerId: ProviderId) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingModels = true)
            try {
                val models = modelDiscovery.fetchProviderModels(providerId).map { it.toAppModelInfo() }
                _uiState.value = _uiState.value.copy(
                    availableModels = models,
                    isLoadingModels = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingModels = false,
                    errorMessage = "Failed to fetch models: ${e.message}"
                )
            }
        }
    }

    fun toggleModelSelection(providerId: ProviderId, modelId: String) {
        val currentSelected = _uiState.value.selectedModelIds.toMutableList()
        if (currentSelected.contains(modelId)) {
            currentSelected.remove(modelId)
        } else {
            currentSelected.clear()
            currentSelected.add(modelId)
        }

        _uiState.value = _uiState.value.copy(selectedModelIds = currentSelected)
        viewModelScope.launch {
            modelRepository.setSelectedModels(providerId, currentSelected)
            if (providerId == ProviderId.LIQUID && currentSelected.isNotEmpty()) {
                val currentProvider = crudRepository.getProvider(providerId)
                if (currentProvider != null && !currentProvider.enabled) {
                    crudRepository.saveProvider(currentProvider.copy(enabled = true))
                }
            }
        }
    }

    fun setModelTreeUri(providerId: ProviderId, treeUri: Uri?) {
        if (providerId == ProviderId.LIQUID && treeUri != null) {
            applyModelTreeUri(treeUri)

            viewModelScope.launch {
                val current = crudRepository.getProvider(providerId) ?: return@launch
                val appProvider = current.toAppProvider()
                val updated = appProvider.copy(
                    baseUrl = "SAF:${treeUri}",
                    enabled = true
                )
                crudRepository.saveProvider(updated.toCoreProvider())
            }
        }
    }

    private fun applyModelTreeUri(treeUri: Uri?) {
        (localInferenceEngine as? ModelTreeUriConfigurable)?.setCustomModelTreeUri(treeUri)
    }

    suspend fun testConnection(providerId: ProviderId): Boolean {
        val provider = _uiState.value.provider ?: return false
        _uiState.value = _uiState.value.copy(isTesting = true)
        val success = networkTester.testConnection(provider.toCoreProvider())
        _uiState.value = _uiState.value.copy(isTesting = false, testSuccess = success)
        return success
    }

    fun testLocalConnection(providerId: ProviderId, host: String, port: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true)
            val tempProvider = _uiState.value.provider?.copy(
                 baseUrl = "http://$host:$port/v1"
            ) ?: return@launch

            val success = networkTester.testConnection(tempProvider.toCoreProvider())

            _uiState.value = _uiState.value.copy(isTesting = false, testSuccess = success)
            if (success) {
                val models = modelDiscovery.discoverModels(tempProvider.toCoreProvider()).map { it.toAppModelInfo() }
                _uiState.value = _uiState.value.copy(
                    availableModels = models
                )
            }
            onResult(success)
        }
    }
}
// Extension functions to convert between Core and App types (from removed facade)
private fun com.shadowai.core.providers.Provider.toAppProvider(): Provider {
    return Provider(
        id = id,
        name = name,
        enabled = enabled,
        baseUrl = baseUrl,
        auth = ProviderAuth(
            type = when (auth.type) {
                com.shadowai.core.providers.AuthType.API_KEY -> AuthType.API_KEY
                com.shadowai.core.providers.AuthType.OAUTH -> AuthType.OAUTH
                com.shadowai.core.providers.AuthType.NONE -> AuthType.NONE
            },
            credentialAlias = auth.credentialAlias,
            hasCredential = auth.hasCredential
        ),
        capabilities = capabilities.map { it.toAppCapability() }.distinct(),
        models = models.map { it.toAppModelInfo() },
        selectedModels = selectedModels,
        customModels = customModels
    )
}

private fun Provider.toCoreProvider(): com.shadowai.core.providers.Provider {
    return com.shadowai.core.providers.Provider(
        id = id,
        name = name,
        enabled = enabled,
        baseUrl = baseUrl,
        auth = com.shadowai.core.providers.ProviderAuth(
            type = when (auth.type) {
                AuthType.API_KEY -> com.shadowai.core.providers.AuthType.API_KEY
                AuthType.OAUTH -> com.shadowai.core.providers.AuthType.OAUTH
                AuthType.NONE -> com.shadowai.core.providers.AuthType.NONE
            },
            credentialAlias = auth.credentialAlias,
            hasCredential = auth.hasCredential
        ),
        capabilities = capabilities.map { it.toCoreCapability() },
        models = models.map { it.toCoreModelInfo() },
        selectedModels = selectedModels,
        customModels = customModels
    )
}

private fun com.shadowai.core.providers.ModelInfo.toAppModelInfo(): ModelInfo {
    return ModelInfo(
        id = id,
        displayName = displayName,
        provider = provider,
        tier = tier,
        notes = notes,
        capabilities = capabilities.map { it.toAppCapability() }.toSet()
    )
}

private fun ModelInfo.toCoreModelInfo(): com.shadowai.core.providers.ModelInfo {
    return com.shadowai.core.providers.ModelInfo(
        id = id,
        displayName = displayName,
        provider = provider,
        tier = tier,
        notes = notes,
        capabilities = capabilities.map { it.toCoreCapability() }.toSet()
    )
}

private fun Capability.toCoreCapability(): com.shadowai.core.Capability {
    return when (this) {
        Capability.TEXT -> com.shadowai.core.Capability.TEXT
        Capability.VISION -> com.shadowai.core.Capability.VISION
        Capability.IMAGE_GEN -> com.shadowai.core.Capability.IMAGE_GEN
        Capability.FUNCTION_CALLS -> com.shadowai.core.Capability.FUNCTION_CALLING
        Capability.VOICE -> com.shadowai.core.Capability.AUDIO_SYNTHESIZE
    }
}

private fun com.shadowai.core.Capability.toAppCapability(): Capability {
    return when (this) {
        com.shadowai.core.Capability.VISION -> Capability.VISION
        com.shadowai.core.Capability.IMAGE_GEN,
        com.shadowai.core.Capability.IMAGE_GEN_FAST,
        com.shadowai.core.Capability.IMAGE_GEN_HIGH_RES,
        com.shadowai.core.Capability.IMAGE_EDIT -> Capability.IMAGE_GEN
        com.shadowai.core.Capability.FUNCTION_CALLING -> Capability.FUNCTION_CALLS
        com.shadowai.core.Capability.AUDIO_TRANSCRIBE,
        com.shadowai.core.Capability.AUDIO_SYNTHESIZE,
        com.shadowai.core.Capability.AUDIO_UNDERSTAND -> Capability.VOICE
        else -> Capability.TEXT
    }
}
