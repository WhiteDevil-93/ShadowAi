package com.shadowai.app.providers

import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.core.ProviderId
import com.shadowai.core.LocalInferenceEngine
import com.shadowai.core.providers.ActiveProviderConfig
import com.shadowai.core.providers.ApiStyle as CoreApiStyle
import com.shadowai.core.Capability as CoreCapability
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveProviderManager @Inject constructor(
    private val adminRepository: AdminRepository,
    // REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
    private val crudRepository: com.shadowai.provideradapters.ProviderCrudRepository,
    private val modelRepository: com.shadowai.provideradapters.ProviderModelRepository,
    private val localInferenceEngine: LocalInferenceEngine
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
        // REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
        val providers = crudRepository.listProviders().map { it.toAppProvider() }.filter { it.enabled }
        val configs = providers.flatMap { provider ->
            getModelIds(provider).mapNotNull { modelId -> createConfig(provider, modelId) }
        }
        return if (requiredStyle == null) configs else configs.filter { it.apiStyle.name == requiredStyle.name }
    }

    private suspend fun createConfig(provider: Provider, modelId: String): ActiveProviderConfig? {
        val baseUrl = provider.baseUrl.trim()
        // REPOSITORY ADAPTER CLEANUP: Using ProviderConfigurationService directly - facade removed
        val coreApiStyle = com.shadowai.provideradapters.ProviderConfigurationService.getApiStyle(provider.id)
        val apiStyle = coreApiStyle.toAppApiStyle()

        // Validate URL if not LIQUID or local path based
        if (apiStyle != ApiStyle.LIQUID && !apiStyle.name.startsWith("LOCAL")) {
             if (baseUrl.toHttpUrlOrNull() == null) return null
        }

        val trimmedModel = modelId.trim().takeIf { it.isNotBlank() } ?: return null

        // Map App Capability to Core Capability
        // H-5 FIX: Ensure FUNCTION_CALLS capability mapping is complete
        val coreCapabilities = provider.capabilities.mapNotNull { appCap ->
            try {
                CoreCapability.valueOf(appCap.name)
            } catch (e: IllegalArgumentException) {
                // Map legacy/mismatched capabilities if needed
                when(appCap) {
                    Capability.VOICE -> CoreCapability.AUDIO_SYNTHESIZE
                    Capability.FUNCTION_CALLS -> CoreCapability.FUNCTION_CALLING // H-5: Explicit mapping
                    else -> null
                }
            }
        }

        // Map App ApiStyle to Core ApiStyle
        val finalApiStyle = try {
            CoreApiStyle.valueOf(apiStyle.name)
        } catch (e: IllegalArgumentException) {
            CoreApiStyle.OPENAI_COMPAT // Fallback
        }

        return ActiveProviderConfig(
            providerId = provider.id,
            modelId = trimmedModel,
            displayName = provider.name,
            apiStyle = finalApiStyle,
            baseUrl = baseUrl,
            isEnabled = provider.enabled,
            capabilities = coreCapabilities
        )
    }

    private suspend fun getModelIds(provider: Provider): List<String> {
        // LIQUID provider: MUST use actual file paths from disk scan, never catalog filenames
        if (provider.id == ProviderId.LIQUID) {
            val availablePaths = getLiquidModelPaths()
            if (availablePaths.isEmpty()) return emptyList()

            // Accept both full-path and legacy filename selections.
            val selectedModels = modelRepository.getSelectedModels(provider.id)
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .mapNotNull { selected ->
                    availablePaths.firstOrNull { path ->
                        path.equals(selected, ignoreCase = true) ||
                            java.io.File(path).name.equals(selected, ignoreCase = true)
                    }
                }
                .distinct()
                .toList()

            return if (selectedModels.isNotEmpty()) selectedModels else availablePaths
        }

        // REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
        val selected = modelRepository.getSelectedModels(provider.id)
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
    private suspend fun getLiquidModelPaths(): List<String> {
        // 1. Check admin-configured path first
        val preferredPath = adminRepository.getLocalTextModelPathSync()
        if (!preferredPath.isNullOrBlank()) {
            val file = java.io.File(preferredPath)
            if (file.exists() && file.isFile) {
                return listOf(preferredPath)
            }
        }

        // 2. Query the active LocalInferenceEngine for currently available GGUF models.
        // This stays consistent with SAF/imported model flows.
        val availableModels = runCatching { localInferenceEngine.getAvailableModels() }
            .getOrDefault(emptyList())
            .filter { path -> path.endsWith(".gguf", ignoreCase = true) }
            .filter { path ->
                val file = java.io.File(path)
                file.exists() && file.isFile
            }
            .sortedBy { path ->
                when {
                    path.contains("Q4_K_M", ignoreCase = true) -> 0
                    path.contains("Q4", ignoreCase = true) -> 1
                    path.contains("Q8", ignoreCase = true) -> 2
                    else -> 3
                }
            }

        if (availableModels.isNotEmpty()) {
            return availableModels
        }

        // 3. No models found - return empty list to disable LIQUID config
        // (prevents creation of broken configs that try to load non-existent catalog models)
        return emptyList()
    }
}

// Extension functions to convert between Core and App types (from removed facade)
private fun com.shadowai.core.providers.ApiStyle.toAppApiStyle(): ApiStyle {
    return ApiStyle.valueOf(name)
}

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
