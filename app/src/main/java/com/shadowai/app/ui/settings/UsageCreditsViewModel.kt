package com.shadowai.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.core.ProviderId
import com.shadowai.app.providers.ProviderRepository
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
    private val providerRepository: ProviderRepository
) : ViewModel() {
    private val _usageData = MutableStateFlow<List<ProviderUsage>>(emptyList())
    val usageData: StateFlow<List<ProviderUsage>> = _usageData

    init {
        loadUsageData()
    }

    private fun loadUsageData() {
        viewModelScope.launch {
            val providers = providerRepository.getAllProviders()
            val usageList = providers.mapNotNull { provider ->
                val hasApiKey = providerRepository.getApiKey(provider.id)?.withSecretBytes { bytes ->
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
