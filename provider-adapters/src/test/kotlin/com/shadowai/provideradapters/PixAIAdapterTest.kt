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
 * Unit tests for PixAIAdapter.
 */
class PixAIAdapterTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var adapter: PixAIAdapter
    private lateinit var gson: Gson
    private val apiKey = "test-pixai-key"

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
            providerId = ProviderId.PIXAI,
            baseUrl = mockWebServer.url("/").toString(),
            apiKeySecret = apiKey.toSecretBytes(),
            timeoutSeconds = 5
        )

        adapter = PixAIAdapter(config, OkHttpClient(), gson)
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
    fun `execute TextToImage succeeds with polling`() = runBlocking {
        adapter.initialize()

        // Generation submission response
        val submitResponse = """
            {
              "job_id": "job_abc",
              "code": 200,
              "status": "pending"
            }
        """.trimIndent()

        // Polling response
        val pollingResponse = """
            {
              "id": "job_abc",
              "status": "COMPLETED",
              "medias": [
                {
                  "url": "https://example.com/pixai-anime.png",
                  "type": "image"
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(submitResponse))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(pollingResponse))

        val result = adapter.execute(
            Transform.TextToImage(),
            "Anime girl with blue hair",
            emptyMap()
        )

        assertTrue(result.isSuccess)
        val pixaiResult = result.getOrNull() as PixAIAdapter.PixAIImageResult
        assertEquals("https://example.com/pixai-anime.png", pixaiResult.imageUrl)

        // Check submission
        val request1 = mockWebServer.takeRequest()
        assertEquals("/v1/generation", request1.path)

        // Check polling
        val request2 = mockWebServer.takeRequest()
        assertEquals("/v1/generation/job_abc", request2.path)
    }

    @Test
    fun `execute ImageToImage succeeds`() = runBlocking {
        adapter.initialize()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{\"job_id\": \"job_xyz\"}"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{\"status\": \"COMPLETED\", \"medias\": [{\"url\": \"https://example.com/anime-output.png\"}]}"))

        val input = "Convert to anime style" to "base64image"
        val result = adapter.execute(
            Transform.ImageToImage(),
            input,
            emptyMap()
        )

        assertTrue(result.isSuccess)
        val request1 = mockWebServer.takeRequest()
        assertEquals("/v1/generation/img2img", request1.path)
        assertTrue(request1.body.readUtf8().contains("base64image"))
    }
}
