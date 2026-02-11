package com.shadowai.provideradapters

import android.content.Context
import com.shadowai.core.ProviderId
import com.shadowai.core.providers.Provider
import com.shadowai.core.providers.ProviderAuth
import com.shadowai.core.providers.AuthType
import com.shadowai.core.providers.ModelInfo
import com.shadowai.core.providers.ApiStyle
import com.shadowai.core.Capability

/**
 * Static provider configuration: API style mapping and default provider catalog.
 */
object ProviderConfigurationService {

    fun getApiStyle(providerId: ProviderId): ApiStyle {
        return when (providerId) {
            ProviderId.OPENAI, ProviderId.OPENROUTER, ProviderId.XAI, ProviderId.GROQ,
            ProviderId.COHERE, ProviderId.SILICON_FLOW, ProviderId.MISTRAL,
            ProviderId.DEEPSEEK, ProviderId.ATLASCLOUD, ProviderId.SIRAY,
            ProviderId.OLLAMA_CLOUD -> ApiStyle.OPENAI_COMPAT
            ProviderId.ANTHROPIC -> ApiStyle.ANTHROPIC
            ProviderId.GEMINI -> ApiStyle.GEMINI
            ProviderId.LOCAL_TEXT -> ApiStyle.LOCAL_TEXT
            ProviderId.LOCAL_IMAGE -> ApiStyle.LOCAL_IMAGE
            ProviderId.PIXAI -> ApiStyle.PIXAI
            ProviderId.NOVELAI -> ApiStyle.NOVELAI
            ProviderId.NOVITA -> ApiStyle.NOVITA_IMAGE
            ProviderId.LIQUID -> ApiStyle.LIQUID
            ProviderId.FLUX -> ApiStyle.LOCAL_IMAGE
            ProviderId.REPLICATE -> ApiStyle.OPENAI_COMPAT
            else -> ApiStyle.OPENAI_COMPAT
        }
    }

    fun getDefaultProviders(context: Context): List<Provider> {
        fun defaultModels(id: ProviderId): List<ModelInfo> = ProviderModelCatalog.getModels(id)
        fun defaultCaps(id: ProviderId, fallback: List<Capability>): List<Capability> {
            val fromModels = defaultModels(id).flatMap { it.capabilities }.distinct()
            return (fromModels + fallback).distinct()
        }
        return listOf(
            Provider(ProviderId.OPENAI, "OpenAI", false, "https://api.openai.com/v1", ProviderAuth(),
                capabilities = defaultCaps(ProviderId.OPENAI, listOf(Capability.TEXT, Capability.VISION, Capability.FUNCTION_CALLING)),
                models = defaultModels(ProviderId.OPENAI)),
            Provider(ProviderId.OPENROUTER, "OpenRouter", false, "https://openrouter.ai/api/v1", ProviderAuth(),
                capabilities = defaultCaps(ProviderId.OPENROUTER, listOf(Capability.TEXT, Capability.FUNCTION_CALLING)),
                models = defaultModels(ProviderId.OPENROUTER)),
            Provider(ProviderId.ANTHROPIC, "Anthropic", false, "https://api.anthropic.com/v1", ProviderAuth(),
                capabilities = defaultCaps(ProviderId.ANTHROPIC, listOf(Capability.TEXT, Capability.VISION)),
                models = defaultModels(ProviderId.ANTHROPIC)),
            Provider(ProviderId.ATLASCLOUD, "AtlasCloud", false, "https://api.atlascloud.ai/v1", ProviderAuth(),
                capabilities = listOf(Capability.TEXT, Capability.FUNCTION_CALLING)),
            Provider(ProviderId.SIRAY, "SirayAI", false, "https://api.siray.ai/v1", ProviderAuth(),
                capabilities = listOf(Capability.TEXT, Capability.FUNCTION_CALLING)),
            Provider(ProviderId.GEMINI, "Google Gemini", false, "https://generativelanguage.googleapis.com/v1", ProviderAuth(),
                capabilities = defaultCaps(ProviderId.GEMINI, listOf(Capability.TEXT, Capability.VISION, Capability.FUNCTION_CALLING)),
                models = defaultModels(ProviderId.GEMINI)),
            Provider(ProviderId.GROQ, "Groq", false, "https://api.groq.com/openai/v1", ProviderAuth(),
                capabilities = defaultCaps(ProviderId.GROQ, listOf(Capability.TEXT, Capability.FUNCTION_CALLING)),
                models = defaultModels(ProviderId.GROQ)),
            Provider(ProviderId.MISTRAL, "Mistral", false, "https://api.mistral.ai/v1", ProviderAuth(),
                capabilities = defaultCaps(ProviderId.MISTRAL, listOf(Capability.TEXT, Capability.FUNCTION_CALLING)),
                models = defaultModels(ProviderId.MISTRAL)),
            Provider(ProviderId.DEEPSEEK, "DeepSeek", false, "https://api.deepseek.com/v1", ProviderAuth(),
                capabilities = defaultCaps(ProviderId.DEEPSEEK, listOf(Capability.TEXT, Capability.FUNCTION_CALLING)),
                models = defaultModels(ProviderId.DEEPSEEK)),
            Provider(ProviderId.XAI, "xAI", false, "https://api.x.ai/v1", ProviderAuth(),
                capabilities = defaultCaps(ProviderId.XAI, listOf(Capability.TEXT, Capability.FUNCTION_CALLING)),
                models = defaultModels(ProviderId.XAI)),
            Provider(ProviderId.COHERE, "Cohere", false, "https://api.cohere.ai/v1", ProviderAuth(),
                capabilities = defaultCaps(ProviderId.COHERE, listOf(Capability.TEXT)),
                models = defaultModels(ProviderId.COHERE)),
            Provider(ProviderId.SILICON_FLOW, "Silicon Flow", false, "https://api.siliconflow.cn/v1", ProviderAuth(),
                capabilities = defaultCaps(ProviderId.SILICON_FLOW, listOf(Capability.TEXT)),
                models = defaultModels(ProviderId.SILICON_FLOW)),
            Provider(ProviderId.PIXAI, "PixAI", false, "https://api.pixai.art/v1", ProviderAuth(),
                capabilities = defaultCaps(ProviderId.PIXAI, listOf(Capability.IMAGE_GEN)),
                models = defaultModels(ProviderId.PIXAI)),
            Provider(ProviderId.NOVITA, "Novita", false, "https://api.novita.ai/v3", ProviderAuth(),
                capabilities = defaultCaps(ProviderId.NOVITA, listOf(Capability.IMAGE_GEN)),
                models = defaultModels(ProviderId.NOVITA)),
            Provider(ProviderId.NOVELAI, "NovelAI", false, "https://api.novelai.net/v1", ProviderAuth(),
                capabilities = defaultCaps(ProviderId.NOVELAI, listOf(Capability.TEXT)),
                models = defaultModels(ProviderId.NOVELAI)),
            Provider(ProviderId.LIQUID, "Liquid AI (Local)", true, context.getExternalFilesDir(null)?.absolutePath ?: "",
                ProviderAuth(type = AuthType.NONE), capabilities = listOf(Capability.TEXT),
                models = defaultModels(ProviderId.LIQUID)),
            Provider(ProviderId.LOCAL_TEXT, "Local Text Model", false, "http://localhost:8080/v1",
                ProviderAuth(type = AuthType.NONE), capabilities = listOf(Capability.TEXT)),
            Provider(ProviderId.LOCAL_IMAGE, "Local Image Model", false, "http://localhost:8081/v1",
                ProviderAuth(type = AuthType.NONE), capabilities = listOf(Capability.IMAGE_GEN)),
            Provider(ProviderId.OLLAMA_CLOUD, "Ollama Cloud", false, "https://ollama.com/api/v1", ProviderAuth(),
                capabilities = defaultCaps(ProviderId.OLLAMA_CLOUD, listOf(Capability.TEXT)),
                models = defaultModels(ProviderId.OLLAMA_CLOUD)),
            Provider(ProviderId.FLUX, "FLUX (Image Gen)", false, "https://api.replicate.com/v1", ProviderAuth(),
                capabilities = defaultCaps(ProviderId.FLUX, listOf(Capability.IMAGE_GEN)),
                models = defaultModels(ProviderId.FLUX)),
            Provider(ProviderId.REPLICATE, "Replicate", false, "https://api.replicate.com/v1", ProviderAuth(),
                capabilities = defaultCaps(ProviderId.REPLICATE, listOf(Capability.TEXT, Capability.IMAGE_GEN)),
                models = defaultModels(ProviderId.REPLICATE))
        )
    }
}
