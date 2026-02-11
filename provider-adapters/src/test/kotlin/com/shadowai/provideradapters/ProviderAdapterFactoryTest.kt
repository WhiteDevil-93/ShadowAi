package com.shadowai.provideradapters

import com.google.gson.Gson
import com.shadowai.core.ProviderId
import com.shadowai.core.security.toSecretBytes
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ProviderAdapterFactory.
 */
class ProviderAdapterFactoryTest {

    private lateinit var factory: ProviderAdapterFactory
    private lateinit var httpClient: OkHttpClient
    private lateinit var gson: Gson

    @Before
    fun setup() {
        httpClient = OkHttpClient.Builder().build()
        gson = Gson()
        factory = ProviderAdapterFactory(httpClient, gson, null)
    }

    @Test
    fun `creates LocalLlamaAdapter for LOCAL_TEXT provider`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = "/path/to/model.gguf"
        )

        val adapter = factory.getAdapter(config)

        assertTrue(adapter is LocalLlamaAdapter)
        assertEquals(ProviderId.LOCAL_TEXT, adapter.providerId)
    }

    @Test
    fun `creates LocalLlamaAdapter for LIQUID provider`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LIQUID,
            baseUrl = "/path/to/model.gguf"
        )

        val adapter = factory.getAdapter(config)

        assertTrue(adapter is LocalLlamaAdapter)
    }

    @Test
    fun `creates OpenAICompatibleAdapter for OPENAI provider`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.OPENAI,
            baseUrl = "https://api.openai.com/v1",
            apiKeySecret = "test-key".toSecretBytes()
        )

        val adapter = factory.getAdapter(config)

        assertTrue(adapter is OpenAICompatibleAdapter)
        assertEquals(ProviderId.OPENAI, adapter.providerId)
    }

    @Test
    fun `creates OpenAICompatibleAdapter for OPENROUTER provider`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.OPENROUTER,
            baseUrl = "https://openrouter.ai/api/v1",
            apiKeySecret = "test-key".toSecretBytes()
        )

        val adapter = factory.getAdapter(config)

        assertTrue(adapter is OpenAICompatibleAdapter)
    }

    @Test
    fun `creates OpenAICompatibleAdapter for GROQ provider`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.GROQ,
            baseUrl = "https://api.groq.com/openai/v1",
            apiKeySecret = "test-key".toSecretBytes()
        )

        val adapter = factory.getAdapter(config)

        assertTrue(adapter is OpenAICompatibleAdapter)
    }

    @Test
    fun `creates OpenAICompatibleAdapter for MISTRAL provider`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.MISTRAL,
            baseUrl = "https://api.mistral.ai/v1",
            apiKeySecret = "test-key".toSecretBytes()
        )

        val adapter = factory.getAdapter(config)

        assertTrue(adapter is OpenAICompatibleAdapter)
    }

    @Test
    fun `creates OpenAICompatibleAdapter for DEEPSEEK provider`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.DEEPSEEK,
            baseUrl = "https://api.deepseek.com/v1",
            apiKeySecret = "test-key".toSecretBytes()
        )

        val adapter = factory.getAdapter(config)

        assertTrue(adapter is OpenAICompatibleAdapter)
    }

    @Test
    fun `creates AnthropicAdapter for ANTHROPIC provider`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.ANTHROPIC,
            baseUrl = "https://api.anthropic.com/v1",
            apiKeySecret = "test-key".toSecretBytes()
        )

        val adapter = factory.getAdapter(config)

        assertTrue(adapter is AnthropicAdapter)
        assertEquals(ProviderId.ANTHROPIC, adapter.providerId)
    }

    @Test
    fun `creates GeminiAdapter for GEMINI provider`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.GEMINI,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            apiKeySecret = "test-key".toSecretBytes()
        )

        val adapter = factory.getAdapter(config)

        assertTrue(adapter is GeminiAdapter)
        assertEquals(ProviderId.GEMINI, adapter.providerId)
    }

    @Test
    fun `creates PixAIAdapter for PIXAI provider`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.PIXAI,
            baseUrl = "https://api.pixai.art",
            apiKeySecret = "test-key".toSecretBytes()
        )

        val adapter = factory.getAdapter(config)

        assertTrue(adapter is PixAIAdapter)
        assertEquals(ProviderId.PIXAI, adapter.providerId)
    }

    @Test
    fun `creates NovitaAdapter for NOVITA provider`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.NOVITA,
            baseUrl = "https://api.novita.ai",
            apiKeySecret = "test-key".toSecretBytes()
        )

        val adapter = factory.getAdapter(config)

        assertTrue(adapter is NovitaAdapter)
        assertEquals(ProviderId.NOVITA, adapter.providerId)
    }

    @Test
    fun `creates NovelAIAdapter for NOVELAI provider`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.NOVELAI,
            baseUrl = "https://api.novelai.net",
            apiKeySecret = "test-key".toSecretBytes()
        )

        val adapter = factory.getAdapter(config)

        assertTrue(adapter is NovelAIAdapter)
        assertEquals(ProviderId.NOVELAI, adapter.providerId)
    }

    @Test
    fun `caches adapters by providerId`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.OPENAI,
            baseUrl = "https://api.openai.com/v1",
            apiKeySecret = "test-key".toSecretBytes()
        )

        val adapter1 = factory.getAdapter(config)
        val adapter2 = factory.getAdapter(config)

        assertSame(adapter1, adapter2)
    }

    @Test
    fun `removeAdapter removes from cache`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.ANTHROPIC,
            baseUrl = "https://api.anthropic.com/v1",
            apiKeySecret = "test-key".toSecretBytes()
        )

        val adapter1 = factory.getAdapter(config)
        factory.removeAdapter(ProviderId.ANTHROPIC)
        val adapter2 = factory.getAdapter(config)

        assertNotSame(adapter1, adapter2)
    }

    @Test
    fun `clearCache removes all adapters`() {
        factory.getAdapter(ProviderAdapterConfig(ProviderId.OPENAI, "url", apiKeySecret = "key".toSecretBytes()))
        factory.getAdapter(ProviderAdapterConfig(ProviderId.ANTHROPIC, "url", apiKeySecret = "key".toSecretBytes()))

        assertEquals(2, factory.getAllAdapters().size)

        factory.clearCache()

        assertEquals(0, factory.getAllAdapters().size)
    }

    @Test
    fun `getAllAdapters returns all cached adapters`() {
        factory.getAdapter(ProviderAdapterConfig(ProviderId.OPENAI, "url", apiKeySecret = "key".toSecretBytes()))
        factory.getAdapter(ProviderAdapterConfig(ProviderId.ANTHROPIC, "url", apiKeySecret = "key".toSecretBytes()))
        factory.getAdapter(ProviderAdapterConfig(ProviderId.GEMINI, "url", apiKeySecret = "key".toSecretBytes()))

        val all = factory.getAllAdapters()

        assertEquals(3, all.size)
    }

    @Test
    fun `creates adapters for all OpenAI-compatible providers`() {
        val openAiCompatible = listOf(
            ProviderId.OPENAI,
            ProviderId.OPENROUTER,
            ProviderId.GROQ,
            ProviderId.COHERE,
            ProviderId.SILICON_FLOW,
            ProviderId.MISTRAL,
            ProviderId.DEEPSEEK,
            ProviderId.XAI,
            ProviderId.ATLASCLOUD,
            ProviderId.SIRAY
        )

        openAiCompatible.forEach { providerId ->
            factory.clearCache()
            val config = ProviderAdapterConfig(providerId, "https://api.test.com", apiKeySecret = "key".toSecretBytes())
            val adapter = factory.getAdapter(config)

            assertTrue(
                "Expected OpenAICompatibleAdapter for $providerId",
                adapter is OpenAICompatibleAdapter
            )
        }
    }
}
