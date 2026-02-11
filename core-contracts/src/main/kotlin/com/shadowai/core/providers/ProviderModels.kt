package com.shadowai.core.providers

import com.shadowai.core.ProviderId

enum class AuthType { API_KEY, OAUTH, NONE }

enum class ApiStyle { OPENAI, ANTHROPIC, LOCAL_TEXT, LOCAL_IMAGE, NOVELAI, NOVITA, NOVITA_IMAGE, PIXAI, GEMINI, OPENAI_COMPAT, BEDROCK, LIQUID, OLLAMA }

data class ProviderAuth(
    val type: AuthType = AuthType.API_KEY,
    val credentialAlias: String? = null,
    val hasCredential: Boolean = false
)

data class Provider(
    val id: ProviderId,
    val name: String,
    val enabled: Boolean,
    val baseUrl: String,
    val auth: ProviderAuth,
    val capabilities: List<com.shadowai.core.Capability> = emptyList(),
    val models: List<ModelInfo> = emptyList(),
    val selectedModels: List<String>? = null,
    val customModels: List<String>? = null
)

data class ModelInfo(
    val id: String,
    val displayName: String,
    val provider: ProviderId,
    val tier: String? = null,
    val notes: String? = null,
    val capabilities: Set<com.shadowai.core.Capability> = emptySet()
) {
    val description: String? get() = notes
}
