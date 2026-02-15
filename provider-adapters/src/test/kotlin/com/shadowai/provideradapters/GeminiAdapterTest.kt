package com.shadowai.provideradapters

import com.google.gson.Gson
import com.shadowai.core.ProviderId
import com.shadowai.core.Transform
import com.shadowai.core.security.toSecretBytes
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for GeminiAdapter.
 */
class GeminiAdapterTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var adapter: GeminiAdapter
    private lateinit var gson: Gson
    private val apiKey = "test-gemini-key"

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        gson = Gson()
        val config = ProviderAdapterConfig(
            providerId = ProviderId.GEMINI,
            baseUrl = mockWebServer.url("/").toString(),
            apiKeySecret = apiKey.toSecretBytes(),
            modelId = "gemini-1.5-pro"
        )

        adapter = GeminiAdapter(config, OkHttpClient(), gson)
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

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{\"models\": []}"))

        assertTrue(adapter.isAvailable())

        val request = mockWebServer.takeRequest()
        assertTrue(request.path!!.contains("/v1beta/models"))
        assertTrue(request.path!!.contains("key=$apiKey"))
    }

    @Test
    fun `execute TextToText succeeds`() = runBlocking {
        adapter.initialize()

        val responseBody = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "Hello, I am Gemini."
                      }
                    ],
                    "role": "model"
                  },
                  "finishReason": "STOP",
                  "index": 0
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val result = adapter.execute(
            Transform.TextToText(),
            "Who are you?",
            emptyMap()
        )

        assertTrue(result.isSuccess)
        assertEquals("Hello, I am Gemini.", result.getOrNull())

        val request = mockWebServer.takeRequest()
        assertTrue(request.path!!.contains(":generateContent"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("Who are you?"))
    }

    @Test
    fun `executeStreaming returns flow of chunks`() = runBlocking {
        adapter.initialize()

        val streamData = """
            data: {"candidates": [{"content": {"parts": [{"text": "Hello"}]}}]}
            data: {"candidates": [{"content": {"parts": [{"text": " world"}]}}]}

        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(streamData))

        val flow = adapter.executeStreaming("Hi", emptyMap())
        val results = flow.toList()

        val chunks = results.filterIsInstance<GeminiAdapter.StreamingResponse.Chunk>()
        assertEquals(2, chunks.size)
        assertEquals("Hello", chunks[0].content)
        assertEquals(" world", chunks[1].content)
        // Gemini adapter streaming implementation might not emit explicit Done if stream ends naturally, checking last chunk or exception absence is usually enough
    }

    @Test
    fun `execute ImageToText (Vision) succeeds`() = runBlocking {
        adapter.initialize()

        val responseBody = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "This is a picture of a park."
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val input = "Describe this picture" to "base64data"
        val result = adapter.execute(
            Transform.ImageToText(),
            input,
            emptyMap()
        )

        assertTrue(result.isSuccess)
        assertEquals("This is a picture of a park.", result.getOrNull())

        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("Describe this picture"))
        assertTrue(body.contains("inline_data"))
    }

    @Test
    fun `handles safety blocked response`() = runBlocking {
        adapter.initialize()

        val responseBody = """
            {
              "candidates": [
                {
                  "finishReason": "SAFETY"
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val result = adapter.execute(
            Transform.TextToText(),
            "Dangerous prompt",
            emptyMap()
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GeminiAdapter.GeminiException.SafetyBlocked)
    }
}
