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
 * Unit tests for NovitaAdapter.
 */
class NovitaAdapterTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var adapter: NovitaAdapter
    private lateinit var gson: Gson
    private val apiKey = "test-novita-key"

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
            providerId = ProviderId.NOVITA,
            baseUrl = mockWebServer.url("/").toString(),
            apiKeySecret = apiKey.toSecretBytes(),
            modelId = "sd_xl_base_1.0",
            timeoutSeconds = 5
        )

        adapter = NovitaAdapter(config, OkHttpClient(), gson)
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
        assertEquals("/v3/models", request.path)
        assertEquals("Bearer $apiKey", request.getHeader("Authorization"))
    }

    @Test
    fun `execute TextToImage succeeds with polling`() = runBlocking {
        adapter.initialize()

        // Async submission response
        val submitResponse = """
            {
              "task_id": "task_123"
            }
        """.trimIndent()

        // Polling response
        val pollingResponse = """
            {
              "task_id": "task_123",
              "status": "SUCCESS",
              "images": ["https://example.com/novita-image.png"]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(submitResponse))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(pollingResponse))

        val result = adapter.execute(
            Transform.TextToImage(),
            "A cosmic landscape",
            emptyMap()
        )

        assertTrue(result.isSuccess)

        // NovitaImageResult is internal, but execute returns Result<Any>
        // We cast to the known internal type for verification within the module
        val novitaResult = result.getOrNull() as? NovitaImageResult
        assertNotNull(novitaResult)
        assertEquals("https://example.com/novita-image.png", novitaResult?.imageUrls?.first())

        // Check submission
        val request1 = mockWebServer.takeRequest()
        assertEquals("/v3/async/txt2img", request1.path)

        // Check polling
        val request2 = mockWebServer.takeRequest()
        assertEquals("/v3/async/task-result?taskId=task_123", request2.path)
    }

    @Test
    fun `execute ImageToImage succeeds`() = runBlocking {
        adapter.initialize()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{\"task_id\": \"task_456\"}"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{\"status\": \"SUCCESS\", \"images\": [\"https://example.com/output.png\"]}"))

        val input = "Add a hat" to "base64image"
        val result = adapter.execute(
            Transform.ImageToImage(),
            input,
            emptyMap()
        )

        assertTrue(result.isSuccess)
        val request1 = mockWebServer.takeRequest()
        assertEquals("/v3/async/img2img", request1.path)
        assertTrue(request1.body.readUtf8().contains("base64image"))
    }

    @Test
    fun `handles task failure`() = runBlocking {
        adapter.initialize()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{\"task_id\": \"task_789\"}"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{\"status\": \"FAILED\", \"reason\": \"Out of credits\"}"))

        val result = adapter.execute(
            Transform.TextToImage(),
            "Prompt",
            emptyMap()
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Out of credits") == true)
    }
}
