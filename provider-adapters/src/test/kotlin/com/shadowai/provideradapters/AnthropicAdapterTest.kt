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
 * Unit tests for AnthropicAdapter.
 */
class AnthropicAdapterTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var adapter: AnthropicAdapter
    private lateinit var gson: Gson
    private val apiKey = "test-anthropic-key"

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        gson = Gson()
        val config = ProviderAdapterConfig(
            providerId = ProviderId.ANTHROPIC,
            baseUrl = mockWebServer.url("/").toString(),
            apiKeySecret = apiKey.toSecretBytes(),
            modelId = "claude-3-opus-20240229"
        )

        adapter = AnthropicAdapter(config, OkHttpClient(), gson)
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
        assertEquals(apiKey, request.getHeader("x-api-key"))
        apiKey.toSecretBytes().dispose() // Fixed: use dispose() instead of zeroOut()
    }

    @Test
    fun `execute TextToText succeeds`() = runBlocking {
        adapter.initialize()

        val responseBody = """
            {
              "id": "msg_123",
              "type": "message",
              "role": "assistant",
              "model": "claude-3-opus-20240229",
              "content": [
                {
                  "type": "text",
                  "text": "Hello, I am Claude."
                }
              ],
              "stop_reason": "end_turn"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val result = adapter.execute(
            Transform.TextToText(),
            "Who are you?",
            emptyMap()
        )

        assertTrue(result.isSuccess)
        assertEquals("Hello, I am Claude.", result.getOrNull())

        val request = mockWebServer.takeRequest()
        assertEquals("/v1/messages", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("Who are you?"))
    }

    @Test
    fun `executeStreaming returns flow of chunks`() = runBlocking {
        adapter.initialize()

        val streamData = """
            event: message_start
            data: {"type": "message_start", "message": {"id": "msg_123", "type": "message", "role": "assistant", "model": "claude-3-opus", "content": [], "stop_reason": null}}

            event: content_block_start
            data: {"type": "content_block_start", "index": 0, "content_block": {"type": "text", "text": ""}}

            event: content_block_delta
            data: {"type": "content_block_delta", "index": 0, "delta": {"type": "text_delta", "text": "Hello"}}

            event: content_block_delta
            data: {"type": "content_block_delta", "index": 0, "delta": {"type": "text_delta", "text": " world"}}

            event: message_stop
            data: {"type": "message_stop"}

        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(streamData))

        val flow = adapter.executeStreaming("Hi", emptyMap())
        val results = flow.toList()

        // We only emit Chunk and Done in our implementation
        val chunks = results.filterIsInstance<AnthropicAdapter.StreamingResponse.Chunk>()
        assertEquals(2, chunks.size)
        assertEquals("Hello", chunks[0].content)
        assertEquals(" world", chunks[1].content)
        assertTrue(results.last() is AnthropicAdapter.StreamingResponse.Done)
    }

    @Test
    fun `execute ImageToText (Vision) succeeds`() = runBlocking {
        adapter.initialize()

        val responseBody = """
            {
              "content": [
                {
                  "type": "text",
                  "text": "The image shows a cat sitting on a mat."
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val input = "Describe this image" to "base64data"
        val result = adapter.execute(
            Transform.ImageToText(),
            input,
            emptyMap()
        )

        assertTrue(result.isSuccess)
        assertEquals("The image shows a cat sitting on a mat.", result.getOrNull())

        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("Describe this image"))
        assertTrue(body.contains("base64"))
    }

    @Test
    fun `handles API error response`() = runBlocking {
        adapter.initialize()

        val errorBody = """
            {
              "type": "error",
              "error": {
                "type": "invalid_request_error",
                "message": "Overloaded"
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(529).setBody(errorBody))

        val result = adapter.execute(
            Transform.TextToText(),
            "Hi",
            emptyMap()
        )

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is AnthropicAdapter.AnthropicException.UnknownError)
        assertTrue(exception?.message?.contains("Overloaded") == true)
    }
}
