package com.shadowai.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.core.ProviderId
// REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
import com.shadowai.provideradapters.ProviderCrudRepository
import com.shadowai.provideradapters.ProviderSecretRepository
import com.shadowai.app.security.useBytes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderUsage(
    val providerName: String,
    val reportedCredits: Double,
    val estimatedCredits: Double,
    val isReported: Boolean
)

@HiltViewModel
class UsageCreditsViewModel @Inject constructor(
    // REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
    private val crudRepository: ProviderCrudRepository,
    private val secretRepository: ProviderSecretRepository
) : ViewModel() {
    private val _usageData = MutableStateFlow<List<ProviderUsage>>(emptyList())
    val usageData: StateFlow<List<ProviderUsage>> = _usageData

    init {
        loadUsageData()
    }

    private fun loadUsageData() {
        viewModelScope.launch {
            // REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
            val providers = crudRepository.getAllProviders().map { it.toAppProvider() }
            val usageList = providers.mapNotNull { provider ->
                val hasApiKey = secretRepository.getApiKey(provider.id.name)?.withSecretBytes { bytes ->
                    String(bytes, Charsets.UTF_8).isNotBlank()
                } == true

                if (provider.enabled || hasApiKey) {
                    val isLocal = !provider.id.isCloud()

                    ProviderUsage(
                        providerName = provider.name,
                        reportedCredits = 0.0, // Reporting API not yet available
                        estimatedCredits = 0.0, // Local token tracking not enabled
                        isReported = !isLocal
                    )
                } else {
                    null
                }
            }
            _usageData.value = usageList
        }
    }

    private fun ProviderId.isCloud(): Boolean {
        return when (this) {
            ProviderId.LOCAL_IMAGE, ProviderId.LOCAL_TEXT, ProviderId.LIQUID -> false
            else -> true
        }
    }
}

// Extension functions to convert between Core and App types (from removed facade)
private fun com.shadowai.core.providers.Provider.toAppProvider(): com.shadowai.app.providers.Provider {
    return com.shadowai.app.providers.Provider(
        id = id,
        name = name,
        enabled = enabled,
        baseUrl = baseUrl,
        auth = com.shadowai.app.providers.ProviderAuth(
            type = when (auth.type) {
                com.shadowai.core.providers.AuthType.API_KEY -> com.shadowai.app.providers.AuthType.API_KEY
                com.shadowai.core.providers.AuthType.OAUTH -> com.shadowai.app.providers.AuthType.OAUTH
                com.shadowai.core.providers.AuthType.NONE -> com.shadowai.app.providers.AuthType.NONE
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

private fun com.shadowai.core.Capability.toAppCapability(): com.shadowai.app.providers.Capability {
    return when (this) {
        com.shadowai.core.Capability.VISION -> com.shadowai.app.providers.Capability.VISION
        com.shadowai.core.Capability.IMAGE_GEN,
        com.shadowai.core.Capability.IMAGE_GEN_FAST,
        com.shadowai.core.Capability.IMAGE_GEN_HIGH_RES,
        com.shadowai.core.Capability.IMAGE_EDIT -> com.shadowai.app.providers.Capability.IMAGE_GEN
        com.shadowai.core.Capability.FUNCTION_CALLING -> com.shadowai.app.providers.Capability.FUNCTION_CALLS
        com.shadowai.core.Capability.AUDIO_TRANSCRIBE,
        com.shadowai.core.Capability.AUDIO_SYNTHESIZE,
        com.shadowai.core.Capability.AUDIO_UNDERSTAND -> com.shadowai.app.providers.Capability.VOICE
        else -> com.shadowai.app.providers.Capability.TEXT
    }
}

private fun com.shadowai.core.providers.ModelInfo.toAppModelInfo(): com.shadowai.app.providers.ModelInfo {
    return com.shadowai.app.providers.ModelInfo(
        id = id,
        displayName = displayName,
        provider = provider,
        tier = tier,
        notes = notes,
        capabilities = capabilities.map { it.toAppCapability() }.toSet()
    )
}