package com.shadowai.app.providers

import com.shadowai.core.ProviderId

enum class AuthType { API_KEY, OAUTH, NONE }

enum class ApiStyle {
    OPENAI,
    ANTHROPIC,
    LOCAL_TEXT,
    LOCAL_IMAGE,
    NOVELAI,
    NOVITA,
    NOVITA_IMAGE,
    PIXAI,
    GEMINI,
    OPENAI_COMPAT,
    BEDROCK,
    LIQUID,
    OLLAMA
}

enum class Capability { TEXT, VISION, IMAGE_GEN, FUNCTION_CALLS, VOICE }

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
    val capabilities: List<Capability> = emptyList(),
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
    val capabilities: Set<Capability> = emptySet()
) {
    val description: String? get() = notes
}

enum class FunctionId { PIXAI_IMAGE, NOVELAI_STORY }

data class FunctionInfo(
    val id: FunctionId,
    val name: String,
    val requiredProvider: ProviderId,
    val available: Boolean,
    val description: String
)
