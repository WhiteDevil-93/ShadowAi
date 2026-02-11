package com.shadowai.provideradapters

import com.google.gson.Gson
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
 * Unit tests for FluxAdapter.
 */
class FluxAdapterTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var gson: Gson
    private val apiKey = "test-api-key"

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
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `initialize returns true for valid Replicate config`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = com.shadowai.core.ProviderId.FLUX,
            baseUrl = "https://api.replicate.com/v1",
            apiKeySecret = apiKey.toSecretBytes()
        )
        val adapter = FluxAdapter(config, OkHttpClient(), gson)
        assertTrue(adapter.initialize())
    }

    @Test
    fun `isAvailable returns true for cloud modes`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = com.shadowai.core.ProviderId.FLUX,
            baseUrl = "https://api.replicate.com/v1",
            apiKeySecret = apiKey.toSecretBytes()
        )
        val adapter = FluxAdapter(config, OkHttpClient(), gson)
        adapter.initialize()
        assertTrue(adapter.isAvailable())
    }

    @Test
    fun `isAvailable checks local endpoint for LOCAL mode`() = runBlocking {
        val serverUrl = mockWebServer.url("/").toString()
        val localConfig = ProviderAdapterConfig(
            providerId = com.shadowai.core.ProviderId.FLUX,
            baseUrl = serverUrl
        )

        val adapter = FluxAdapter(localConfig, OkHttpClient(), gson)

        // Enqueue 200 for initialize()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        adapter.initialize()

        // Enqueue 200 for isAvailable()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        assertTrue(adapter.isAvailable())

        val request1 = mockWebServer.takeRequest()
        assertEquals("/system_stats", request1.path)

        val request2 = mockWebServer.takeRequest()
        assertEquals("/system_stats", request2.path)
    }

    @Test
    fun `execute TextToImage succeeds in FAL_AI mode`() = runBlocking {
        val serverUrl = mockWebServer.url("/").toString()
        val falAiUrl = "${serverUrl}fal.ai/"

        val config = ProviderAdapterConfig(
            providerId = com.shadowai.core.ProviderId.FLUX,
            baseUrl = falAiUrl,
            apiKeySecret = apiKey.toSecretBytes(),
            modelId = "flux/schnell"
        )
        val adapter = FluxAdapter(config, OkHttpClient(), gson)
        adapter.initialize()

        val responseBody = """
            {
              "images": [
                {
                  "url": "https://example.com/flux-image.png",
                  "width": 1024,
                  "height": 1024
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val result = adapter.execute(
            Transform.TextToImage(),
            "A futuristic city",
            mapOf("width" to 1024, "height" to 1024)
        )

        assertTrue("Result should be success, but was failure: ${result.exceptionOrNull()}", result.isSuccess)
        val fluxResult = result.getOrNull() as FluxAdapter.FluxGenerationResult
        assertEquals("https://example.com/flux-image.png", fluxResult.imageUrls.first())
    }

    @Test
    fun `execute TextToImage succeeds in REPLICATE mode with polling`() = runBlocking {
        val serverUrl = mockWebServer.url("/").toString()
        val replicateUrl = "${serverUrl}replicate/"

        val config = ProviderAdapterConfig(
            providerId = com.shadowai.core.ProviderId.FLUX,
            baseUrl = replicateUrl,
            apiKeySecret = apiKey.toSecretBytes(),
            modelId = "black-forest-labs/flux-schnell",
            timeoutSeconds = 5
        )
        val adapter = FluxAdapter(config, OkHttpClient(), gson)
        adapter.initialize()

        // Initial request response
        val initialResponse = """
            {
              "id": "pred-123",
              "status": "starting",
              "urls": {
                "get": "${mockWebServer.url("/predictions/pred-123")}"
              }
            }
        """.trimIndent()

        // Polling response
        val pollingResponse = """
            {
              "id": "pred-123",
              "status": "succeeded",
              "output": ["https://example.com/replicate-image.png"]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(201).setBody(initialResponse))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(pollingResponse))

        val result = adapter.execute(
            Transform.TextToImage(),
            "Space nebula",
            emptyMap()
        )

        assertTrue("Result should be success, but was failure: ${result.exceptionOrNull()}", result.isSuccess)
        val fluxResult = result.getOrNull() as FluxAdapter.FluxGenerationResult
        assertEquals("https://example.com/replicate-image.png", fluxResult.imageUrls.first())
    }

    @Test
    fun `getPriority returns correct values`() {
        val config = ProviderAdapterConfig(
            providerId = com.shadowai.core.ProviderId.FLUX,
            baseUrl = "http://localhost:8188"
        )
        val adapter = FluxAdapter(config, OkHttpClient(), gson)
        assertEquals(90, adapter.getPriority(Transform.TextToImage()))
        assertEquals(85, adapter.getPriority(Transform.ImageToImage()))
        assertEquals(0, adapter.getPriority(Transform.TextToText()))
    }
}
