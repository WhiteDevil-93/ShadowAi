package com.shadowai.provideradapters

import com.google.gson.Gson
import com.shadowai.core.ProviderId
import com.shadowai.core.Transform
import com.shadowai.core.security.toSecretBytes
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for OpenAICompatibleAdapter.
 */
class OpenAICompatibleAdapterTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var adapter: OpenAICompatibleAdapter
    private lateinit var gson: Gson
    private val apiKey = "test-openai-key"

    @Before
    fun setup() {
        // Mock Android Log class
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0

        mockWebServer = MockWebServer()
        mockWebServer.start()

        gson = Gson()
        val config = ProviderAdapterConfig(
            providerId = ProviderId.OPENAI,
            baseUrl = mockWebServer.url("/").toString(),
            apiKeySecret = apiKey.toSecretBytes(),
            modelId = "gpt-4"
        )

        adapter = OpenAICompatibleAdapter(config, OkHttpClient(), gson)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `initialize returns true for valid config`() = runBlocking {
        assertTrue(adapter.initialize())
    }

    @Test
    fun `isAvailable returns true if API responds with 200`() = runBlocking {
        adapter.initialize()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{\"data\": []}"))

        assertTrue(adapter.isAvailable())

        val request = mockWebServer.takeRequest()
        assertEquals("/v1/models", request.path)
        assertEquals("Bearer $apiKey", request.getHeader("Authorization"))
    }

    @Test
    fun `execute TextToText succeeds`() = runBlocking {
        adapter.initialize()

        val responseBody = """
            {
              "id": "chatcmpl-123",
              "object": "chat.completion",
              "created": 1677652288,
              "model": "gpt-4",
              "choices": [{
                "index": 0,
                "message": {
                  "role": "assistant",
                  "content": "Hello! How can I help you today?"
                },
                "finish_reason": "stop"
              }],
              "usage": {
                "prompt_tokens": 9,
                "completion_tokens": 12,
                "total_tokens": 21
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val result = adapter.execute(
            Transform.TextToText(),
            "Hello",
            emptyMap()
        )

        assertTrue(result.isSuccess)
        assertEquals("Hello! How can I help you today?", result.getOrNull())

        val request = mockWebServer.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("gpt-4"))
        assertTrue(body.contains("Hello"))
    }

    @Test
    fun `execute ImageToText succeeds`() = runBlocking {
        adapter.initialize()

        val responseBody = """
            {
              "choices": [{
                "message": {
                  "content": "A beautiful sunset over the ocean."
                }
              }]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val input = "What's in this image?" to "base64encodedimagecontent"
        val result = adapter.execute(
            Transform.ImageToText(),
            input,
            emptyMap()
        )

        assertTrue(result.isSuccess)
        assertEquals("A beautiful sunset over the ocean.", result.getOrNull())

        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("image_url"))
        assertTrue(body.contains("base64"))
    }

    @Test
    fun `handles HTTP error 401`() = runBlocking {
        adapter.initialize()

        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\": {\"message\": \"Invalid API key\"}}"))

        val result = adapter.execute(
            Transform.TextToText(),
            "Hi",
            emptyMap()
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }
}
