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
import com.shadowai.app.providers.ProviderRepository
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
    private val providerRepository: ProviderRepository,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context,
    private val localInferenceEngine: LocalInferenceEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderConfigUiState())
    val uiState: StateFlow<ProviderConfigUiState> = _uiState.asStateFlow()

    private var lastTestTime = 0L
    private val TEST_COOLDOWN_MS = 5000L

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
// ProviderId.HUGGING_FACE temporarily disabled
            // ProviderId.HUGGING_FACE -> TestEndpoint("https://api-inference.huggingface.co/models", mapOf("Authorization" to "Bearer __API_KEY__"))
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
            val provider = providerRepository.getProvider(providerId)
            val hasSavedApiKey = providerRepository.getApiKey(providerId)?.use { true } ?: false
            val selectedModels = providerRepository.getSelectedModels(providerId)
            
            // Always show default models first (from provider or catalog)
            val defaultModels = provider?.models ?: emptyList()
            
            _uiState.value = _uiState.value.copy(
                provider = provider,
                hasSavedApiKey = hasSavedApiKey,
                selectedModelIds = selectedModels,
                availableModels = defaultModels
            )
            
            // Restore SAF tree URI if saved in baseUrl
            provider?.baseUrl?.let { url ->
                if (url.startsWith("SAF:")) {
                    val uriString = url.removePrefix("SAF:")
                    try {
                        val uri = Uri.parse(uriString)
                        // Verify the URI is still accessible before setting it
                        val tree = DocumentFile.fromTreeUri(context, uri)
                        if (tree != null && tree.exists()) {
                            applyModelTreeUri(uri)
                        }
                    } catch (e: Exception) {
                        // Invalid URI stored, ignore
                    }
                }
            }
            
            // Always call discoverModels - it now returns catalog defaults if no key
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
                providerRepository.clearApiKey(providerId)
            } else {
                providerRepository.saveApiKey(providerId, apiKey.toSecretBytes())
            }
            _uiState.value = _uiState.value.copy(hasSavedApiKey = apiKey.isNotBlank())
            loadConfig(providerId)
        }
    }

    fun updateProviderBaseUrl(providerId: ProviderId, baseUrl: String) {
        viewModelScope.launch {
            val current = providerRepository.getProvider(providerId) ?: return@launch
            val updated = current.copy(
                baseUrl = baseUrl,
                enabled = if (providerId.isCloud()) current.enabled else (current.enabled || baseUrl.isNotBlank())
            )
            providerRepository.saveProvider(updated)
            loadConfig(providerId)
        }
    }

    fun discoverModels(providerId: ProviderId) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingModels = true)
            try {
                val models = providerRepository.fetchProviderModels(providerId)
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
            providerRepository.setSelectedModels(providerId, currentSelected)
        }
    }

    /**
     * Sets the SAF (Storage Access Framework) tree URI for model access.
     * This allows accessing models from external storage (Downloads, SD cards) on Android 11+.
     */
    fun setModelTreeUri(providerId: ProviderId, treeUri: Uri?) {
        if (providerId == ProviderId.LIQUID && treeUri != null) {
            applyModelTreeUri(treeUri)
            
            // Also save as provider config so it persists
            viewModelScope.launch {
                val current = providerRepository.getProvider(providerId) ?: return@launch
                val updated = current.copy(
                    baseUrl = "SAF:${treeUri}",  // Store URI in baseUrl field
                    enabled = true
                )
                providerRepository.saveProvider(updated)
            }
        }
    }

    private fun applyModelTreeUri(treeUri: Uri?) {
        (localInferenceEngine as? ModelTreeUriConfigurable)?.setCustomModelTreeUri(treeUri)
    }

    suspend fun testConnection(providerId: ProviderId): Boolean {
        val provider = _uiState.value.provider ?: return false
        _uiState.value = _uiState.value.copy(isTesting = true)
        val success = providerRepository.testConnection(provider)
        _uiState.value = _uiState.value.copy(isTesting = false, testSuccess = success)
        return success
    }

    fun testLocalConnection(providerId: ProviderId, host: String, port: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true)
            val tempProvider = _uiState.value.provider?.copy(
                 baseUrl = "http://$host:$port/v1"
            ) ?: return@launch
            
            val success = providerRepository.testConnection(tempProvider)
            
            _uiState.value = _uiState.value.copy(isTesting = false, testSuccess = success)
            if (success) {
                val models = providerRepository.discoverModels(tempProvider)
                _uiState.value = _uiState.value.copy(
                    availableModels = models
                )
            }
            onResult(success)
        }
    }
}
