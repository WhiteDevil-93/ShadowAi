package com.shadowai.core

import java.util.Locale

/**
 * Enumeration of all supported AI providers.
 * Both local and remote providers are represented here for unified handling.
 */
enum class ProviderId {
    /** Local image generation model */
    LOCAL_IMAGE,

    /** Local text model (e.g., llama.cpp) */
    LOCAL_TEXT,

    /** OpenAI API */
    OPENAI,

    /** AtlasCloud API */
    ATLASCLOUD,

    /** SirayAI API */
    SIRAY,

    /** OpenRouter API */
    OPENROUTER,

    /** PixAI image generation */
    PIXAI,

    /** NovelAI */
    NOVELAI,

    /** Novita AI */
    NOVITA,

    /** Google Gemini */
    GEMINI,

    // TODO: Add HUGGING_FACE adapter when Hugging Face Inference API support is implemented
    // HUGGING_FACE,

    /** Anthropic Claude API */
    ANTHROPIC,

    /** xAI API */
    XAI,

    /** Groq API */
    GROQ,

    /** Cohere API */
    COHERE,

    /** Silicon Flow API */
    SILICON_FLOW,

    /** Mistral AI API */
    MISTRAL,

    /** DeepSeek API */
    DEEPSEEK,

    // TODO: Add AMAZON_BEDROCK adapter when Amazon Bedrock support is implemented
    // AMAZON_BEDROCK,

    /** Liquid AI local model */
    LIQUID,

    /** Flux image generation (local or via API) */
    FLUX,

    /** Replicate API */
    REPLICATE,

    /** Ollama Cloud API */
    OLLAMA_CLOUD,

    /** Unknown or unspecified provider */
    UNKNOWN;

    /**
     * Returns the display name for this provider.
     */
    fun getDisplayName(): String = when (this) {
        LOCAL_IMAGE -> "Local Image"
        LOCAL_TEXT -> "Local Text"
        OPENAI -> "OpenAI"
        ATLASCLOUD -> "AtlasCloud"
        SIRAY -> "SirayAI"
        OPENROUTER -> "OpenRouter"
        PIXAI -> "PixAI"
        NOVELAI -> "NovelAI"
        NOVITA -> "Novita"
        GEMINI -> "Google Gemini"
        // HUGGING_FACE -> "Hugging Face" // TODO: Uncomment when adapter is implemented
        ANTHROPIC -> "Anthropic"
        XAI -> "xAI"
        GROQ -> "Groq"
        COHERE -> "Cohere"
        SILICON_FLOW -> "Silicon Flow"
        MISTRAL -> "Mistral"
        DEEPSEEK -> "DeepSeek"
        // AMAZON_BEDROCK -> "Amazon Bedrock" // TODO: Uncomment when adapter is implemented
        LIQUID -> "Liquid AI"
        FLUX -> "Flux"
        REPLICATE -> "Replicate"
        OLLAMA_CLOUD -> "Ollama Cloud"
        UNKNOWN -> "Unknown"
    }

    /**
     * Returns whether this provider is a local provider.
     */
    fun isLocal(): Boolean = this == LOCAL_IMAGE || this == LOCAL_TEXT || this == LIQUID || this == FLUX

    /**
     * Returns whether this provider is a remote/cloud provider.
     */
    fun isRemote(): Boolean = !isLocal() && this != UNKNOWN

    companion object {
        /**
         * Parses a string value to ProviderId, or returns null if invalid.
         */
        fun parseOrNull(raw: String?): ProviderId? {
            if (raw.isNullOrBlank()) return null
            return try {
                valueOf(raw.trim().uppercase(Locale.US))
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
