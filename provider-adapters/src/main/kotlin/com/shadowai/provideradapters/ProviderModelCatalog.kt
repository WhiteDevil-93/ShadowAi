package com.shadowai.provideradapters

import com.shadowai.core.ProviderId
import com.shadowai.core.providers.ModelInfo
import com.shadowai.core.Capability

object ProviderModelCatalog {

    private fun modelList(providerId: ProviderId, capabilities: Set<Capability>, vararg ids: String): List<ModelInfo> {
        return ids.map { id -> ModelInfo(id, id, providerId, capabilities = capabilities) }
    }

    private fun modelListNamed(
        providerId: ProviderId,
        capabilities: Set<Capability>,
        vararg entries: Pair<String, String>
    ): List<ModelInfo> {
        return entries.map { (id, name) ->
            ModelInfo(id, "$name ($id)", providerId, capabilities = capabilities)
        }
    }

    private val catalog = mapOf(
        ProviderId.OPENAI to modelList(
            ProviderId.OPENAI,
            setOf(Capability.TEXT, Capability.VISION, Capability.FUNCTION_CALLING),
            "gpt-4o",
            "gpt-4o-2024-11-20",
            "gpt-4o-2024-08-06",
            "gpt-4o-2024-05-13",
            "gpt-4o-mini",
            "gpt-4o-mini-2024-07-18",
            "gpt-4-turbo",
            "gpt-4-turbo-2024-04-09",
            "gpt-4-turbo-preview",
            "gpt-4-0125-preview",
            "gpt-4-1106-preview",
            "gpt-4",
            "gpt-4-0613",
            "gpt-3.5-turbo",
            "gpt-3.5-turbo-0125",
            "gpt-3.5-turbo-1106",
            "o1-preview",
            "o1-preview-2024-09-12",
            "o1-mini",
            "o1-mini-2024-09-12"
        ),
        ProviderId.GEMINI to modelList(
            ProviderId.GEMINI,
            setOf(Capability.TEXT, Capability.VISION, Capability.FUNCTION_CALLING),
            "gemini-2.0-flash-exp",
            "gemini-1.5-pro",
            "gemini-1.5-pro-002",
            "gemini-1.5-flash",
            "gemini-1.5-flash-002",
            "gemini-1.5-flash-8b",
            "gemini-pro",
            "gemini-pro-vision"
        ),
        ProviderId.ANTHROPIC to modelList(
            ProviderId.ANTHROPIC,
            setOf(Capability.TEXT, Capability.VISION),
            "claude-opus-4-20250514",
            "claude-3-opus-20240229",
            "claude-3-7-sonnet-20250219",
            "claude-3-5-sonnet-20241022",
            "claude-3-5-sonnet-20240620",
            "claude-3-sonnet-20240229",
            "claude-3-5-haiku-20241022",
            "claude-3-haiku-20240307",
            "claude-haiku-4-5-20250110"
        ),
        ProviderId.OPENROUTER to modelList(
            ProviderId.OPENROUTER,
            setOf(Capability.TEXT, Capability.FUNCTION_CALLING),
            "google/gemini-2.5-flash-vision",
            "google/gemini-2.0-flash-exp:free",
            "google/gemini-pro-1.5",
            "google/gemini-flash-1.5",
            "anthropic/claude-3-opus",
            "anthropic/claude-3-sonnet",
            "anthropic/claude-3-haiku",
            "anthropic/claude-3.5-sonnet",
            "anthropic/claude-3.5-haiku",
            "anthropic/claude-3-7-sonnet",
            "anthropic/claude-haiku-4.5",
            "anthropic/claude-opus-4",
            "openai/gpt-4o",
            "openai/gpt-4o-mini",
            "openai/gpt-4-turbo",
            "openai/gpt-4",
            "openai/gpt-3.5-turbo",
            "openai/o1-preview",
            "openai/o1-mini",
            "meta-llama/llama-3-70b-instruct",
            "meta-llama/llama-3-8b-instruct",
            "meta-llama/llama-3.1-405b-instruct",
            "meta-llama/llama-3.1-70b-instruct",
            "meta-llama/llama-3.1-8b-instruct",
            "meta-llama/llama-3.2-90b-vision-instruct",
            "meta-llama/llama-3.2-11b-vision-instruct",
            "mistralai/mistral-large",
            "mistralai/mistral-medium",
            "mistralai/mistral-small",
            "mistralai/mixtral-8x7b-instruct",
            "mistralai/mixtral-8x22b-instruct",
            "qwen/qwen-2.5-72b-instruct",
            "deepseek/deepseek-chat",
            "deepseek/deepseek-coder",
            "cohere/command-r-plus",
            "perplexity/llama-3.1-sonar-large-128k-online"
        ),
        ProviderId.MISTRAL to modelList(
            ProviderId.MISTRAL,
            setOf(Capability.TEXT, Capability.FUNCTION_CALLING),
            "mistral-large-latest",
            "mistral-large-2411",
            "mistral-large-2407",
            "mistral-medium-latest",
            "mistral-small-latest",
            "mistral-small-2409",
            "mistral-tiny-latest",
            "open-mistral-7b",
            "open-mixtral-8x7b",
            "open-mixtral-8x22b",
            "codestral-latest",
            "codestral-2405",
            "pixtral-12b-2409"
        ),
        ProviderId.DEEPSEEK to modelList(
            ProviderId.DEEPSEEK,
            setOf(Capability.TEXT, Capability.FUNCTION_CALLING),
            "deepseek-chat",
            "deepseek-coder",
            "deepseek-reasoner"
        ),
        ProviderId.GROQ to modelList(
            ProviderId.GROQ,
            setOf(Capability.TEXT, Capability.FUNCTION_CALLING),
            "llama-3.3-70b-versatile",
            "llama-3.1-70b-versatile",
            "llama-3.1-8b-instant",
            "llama3-70b-8192",
            "llama3-8b-8192",
            "mixtral-8x7b-32768",
            "gemma-7b-it",
            "gemma2-9b-it"
        ),
        ProviderId.XAI to modelList(
            ProviderId.XAI,
            setOf(Capability.TEXT, Capability.FUNCTION_CALLING),
            "grok-beta",
            "grok-vision-beta",
            "grok-2-1212",
            "grok-2-vision-1212"
        ),
        ProviderId.COHERE to modelList(
            ProviderId.COHERE,
            setOf(Capability.TEXT),
            "command-r-plus",
            "command-r-plus-08-2024",
            "command-r",
            "command-r-08-2024",
            "command",
            "command-light",
            "command-nightly",
            "command-light-nightly"
        ),
        ProviderId.SILICON_FLOW to modelList(
            ProviderId.SILICON_FLOW,
            setOf(Capability.TEXT),
            "Qwen/Qwen2.5-72B-Instruct",
            "Qwen/Qwen2.5-32B-Instruct",
            "Qwen/Qwen2.5-14B-Instruct",
            "Qwen/Qwen2.5-7B-Instruct",
            "deepseek-ai/DeepSeek-V2.5",
            "meta-llama/Meta-Llama-3.1-70B-Instruct",
            "meta-llama/Meta-Llama-3.1-8B-Instruct"
        ),
        ProviderId.NOVITA to modelList(
            ProviderId.NOVITA,
            setOf(Capability.IMAGE_GEN),
            "novita-sdxl",
            "sdxl-turbo-novita"
        ),
        ProviderId.PIXAI to modelListNamed(
            ProviderId.PIXAI,
            setOf(Capability.IMAGE_GEN),
            "1861558740588989558" to "Haruka v2",
            "1811528826405408057" to "Hoshino",
            "1894092844569363483" to "Tsubaki",
            "1935090615918113018" to "Tsubaki v1.1",
            "1934789864939078594" to "Tsubaki Flash",
            "1914519679917387494" to "Serin",
            "1856956435031440023" to "Otome v2",
            "1869108561160475178" to "Hinata v2"
        ),
        ProviderId.NOVELAI to modelList(
            ProviderId.NOVELAI,
            setOf(Capability.TEXT, Capability.IMAGE_GEN),
            "kayra-v1",
            "clio-v1",
            "euterpe-v2",
            "nai-diffusion-3"
        ),

        ProviderId.OLLAMA_CLOUD to modelListNamed(
            ProviderId.OLLAMA_CLOUD,
            setOf(Capability.TEXT),
            "qwen3-coder:480b" to "Qwen3 Coder 480B",
            "qwen3-coder:87b" to "Qwen3 Coder 87B",
            "qwen3-coder:30b" to "Qwen3 Coder 30B",
            "qwen3-coder:2b" to "Qwen3 Coder 2B",
            "deepseek-v3" to "DeepSeek V3",
            "deepseek-v3.1" to "DeepSeek V3.1",
            "deepseek-v3.2" to "DeepSeek V3.2",
            "qwen3:480b" to "Qwen3 480B",
            "qwen3:32b" to "Qwen3 32B",
            "glm-4.7" to "GLM 4.7",
            "glm-4.6" to "GLM 4.6",
            "devstral-2" to "Devstral 2",
            "devstral-small-2" to "Devstral Small 2",
            "ministral-3" to "Ministral 3",
            "nemotron-3-nano" to "Nemotron 3 Nano",
            "rnj-1" to "RNJ-1"
        ),

        ProviderId.FLUX to modelListNamed(
            ProviderId.FLUX,
            setOf(Capability.IMAGE_GEN),
            "black-forest-labs/flux-schnell" to "FLUX.1 Schnell",
            "black-forest-labs/flux-dev" to "FLUX.1 Dev",
            "black-forest-labs/flux-pro" to "FLUX.1 Pro"
        ),

        ProviderId.REPLICATE to modelListNamed(
            ProviderId.REPLICATE,
            setOf(Capability.TEXT, Capability.IMAGE_GEN),
            "meta/meta-llama-3-70b-instruct" to "Llama 3 70B",
            "meta/meta-llama-3-8b-instruct" to "Llama 3 8B",
            "mistralai/mistral-7b-instruct-v0.2" to "Mistral 7B",
            "black-forest-labs/flux-schnell" to "FLUX.1 Schnell",
            "stability-ai/stable-diffusion-3" to "Stable Diffusion 3"
        ),

        ProviderId.ATLASCLOUD to modelList(
            ProviderId.ATLASCLOUD,
            setOf(Capability.TEXT),
            "gpt-4o",
            "gpt-4o-mini",
            "gpt-3.5-turbo"
        ),

        ProviderId.SIRAY to modelList(
            ProviderId.SIRAY,
            setOf(Capability.TEXT),
            "siray-1",
            "siray-2"
        )
    )

    fun getModels(providerId: ProviderId): List<ModelInfo> {
        return catalog[providerId] ?: emptyList()
    }
}
