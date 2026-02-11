package com.shadowai.app.providers

import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.core.ProviderId
import com.shadowai.app.ai.LocalLiquidEngine
import com.shadowai.app.security.useBytes
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveProviderManager @Inject constructor(
    private val adminRepository: AdminRepository,
    private val providerRepository: ProviderRepository,
    private val localLiquidEngine: LocalLiquidEngine
) {
    suspend fun getActiveConfig(): ActiveProviderConfig? {
        val preferred = adminRepository.getActiveProvider()
        val configs = getActiveConfigs(null)
        return if (preferred != null) {
            configs.firstOrNull { it.providerId == preferred }
                ?: configs.firstOrNull()
        } else {
            configs.firstOrNull()
        }
    }

    suspend fun getActiveConfigs(requiredStyle: ApiStyle? = null): List<ActiveProviderConfig> {
        val providers = providerRepository.listProviders().filter { it.enabled }
        val configs = providers.flatMap { provider ->
            getModelIds(provider).mapNotNull { modelId -> createConfig(provider, modelId) }
        }
        return if (requiredStyle == null) configs else configs.filter { it.apiStyle == requiredStyle }
    }

    private suspend fun createConfig(provider: Provider, modelId: String): ActiveProviderConfig? {
        val baseUrl = provider.baseUrl.trim()
        val apiStyle = providerRepository.getApiStyle(provider.id)
        val httpUrl = if (apiStyle == ApiStyle.LIQUID) {
            null
        } else {
            baseUrl.toHttpUrlOrNull() ?: return null
        }
        val trimmedModel = modelId.trim().takeIf { it.isNotBlank() } ?: return null
        val apiKey = providerRepository.getApiKey(provider.id)?.withSecretBytes { bytes ->
            String(bytes, Charsets.UTF_8)
        }
        val authHeader = apiKey?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }

        return ActiveProviderConfig(
            providerId = provider.id,
            baseUrl = httpUrl,
            modelId = trimmedModel,
            apiStyle = apiStyle,
            authHeader = authHeader,
            capabilities = provider.capabilities
        )
    }

    private suspend fun getModelIds(provider: Provider): List<String> {
        // LIQUID provider: MUST use actual file paths from disk scan, never catalog filenames
        if (provider.id == ProviderId.LIQUID) {
            val paths = getLiquidModelPaths()
            // Always return at least what was scanned from disk, even if empty
            // This prevents fallback to hardcoded catalog model names
            return paths
        }
        
        val selected = providerRepository.getSelectedModels(provider.id)
            .filter { it.isNotBlank() }
        if (selected.isNotEmpty()) return selected
        val custom = provider.customModels?.filter { it.isNotBlank() }.orEmpty()
        if (custom.isNotEmpty()) return custom
        val adminPreferred = adminRepository.getModelName().takeIf { it.isNotBlank() }
        if (adminPreferred != null && provider.id == adminRepository.getActiveProvider()) {
            return listOf(adminPreferred)
        }
        val defaults = provider.models.map { it.id }.filter { it.isNotBlank() }
        if (defaults.isNotEmpty()) return defaults
        return emptyList()
    }
    
    /**
     * Get actual GGUF model paths from disk. Returns full paths that can be loaded directly.
     * Returns empty list when no models are found - this prevents fallback to hardcoded catalog names.
     */
    private fun getLiquidModelPaths(): List<String> {
        // 1. Check admin-configured path first
        val preferredPath = adminRepository.getLocalTextModelPathSync()
        if (!preferredPath.isNullOrBlank()) {
            val file = java.io.File(preferredPath)
            if (file.exists() && file.isFile) {
                return listOf(preferredPath)
            }
        }
        
        // 2. Scan for actual GGUF files on device
        val scannedModels = localLiquidEngine.scanForModels()
        if (scannedModels.isNotEmpty()) {
            // Return full paths, prioritizing Q4 quantized models for mobile
            val sorted = scannedModels.sortedBy { model ->
                when {
                    model.path.contains("Q4_K_M", ignoreCase = true) -> 0
                    model.path.contains("Q4", ignoreCase = true) -> 1
                    model.path.contains("Q8", ignoreCase = true) -> 2
                    else -> 3
                }
            }
            return sorted.map { it.path }
        }
        
        // 3. No models found - return empty list to disable LIQUID config
        // (prevents creation of broken configs that try to load non-existent catalog models)
        return emptyList()
    }
}
