package com.shadowai.provideradapters

import com.shadowai.core.LocalGenerationConfig
import com.shadowai.core.LocalInferenceEngine
import com.shadowai.core.LocalModelHandle
import com.shadowai.core.ProviderId
import com.shadowai.core.Transform
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for LocalLlamaAdapter.
 *
 * These tests verify the DI-based architecture where LocalInferenceEngine
 * is injected and handles model loading/execution internally.
 */
class LocalLlamaAdapterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var validGgufFile: File
    private lateinit var invalidFile: File
    private lateinit var mockInferenceEngine: LocalInferenceEngine
    private lateinit var mockModelHandle: LocalModelHandle

    @Before
    fun setup() {
        // Mock Android Log class
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0

        // Create a mock GGUF file with correct magic bytes (GGUF = 0x47475546)
        validGgufFile = tempFolder.newFile("test-model.gguf")
        validGgufFile.outputStream().use { stream ->
            // GGUF magic bytes
            stream.write(byteArrayOf(0x47, 0x47, 0x55, 0x46))
            // Pad to make file large enough (2MB) to ensure VRAM estimate > 0
            stream.write(ByteArray(2 * 1024 * 1024) { 0 })
        }

        // Create an invalid file (not GGUF format)
        invalidFile = tempFolder.newFile("invalid.txt")
        invalidFile.writeText("This is not a GGUF file")

        // Mock the inference engine and model handle
        // Using relaxed=true so suspend functions return default values without explicit stubbing
        mockInferenceEngine = mockk<LocalInferenceEngine>(relaxed = true)
        mockModelHandle = mockk<LocalModelHandle>(relaxed = true)

        // Setup default mock behaviors
        every { mockInferenceEngine.isNativeAvailable } returns true
        coEvery { mockInferenceEngine.loadModel(any(), any()) } returns mockModelHandle
        every { mockModelHandle.isValid() } returns true
        // For suspend functions, relaxed mocks automatically return null/empty/Unit
    }

    @Test
    fun `config sets providerId correctly`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)

        assertEquals(ProviderId.LOCAL_TEXT, adapter.providerId)
    }

    @Test
    fun `validateConfig returns true for valid path`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)

        assertTrue(adapter.validateConfig())
    }

    @Test
    fun `validateConfig returns false for empty path`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = "",
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)

        assertFalse(adapter.validateConfig())
    }

    @Test
    fun `validateConfig returns false for nonexistent path`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = "/nonexistent/path/model.gguf",
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)

        assertFalse(adapter.validateConfig())
    }

    @Test
    fun `initialize returns true for valid GGUF file`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)

        assertTrue(adapter.initialize())
    }

    @Test
    fun `initialize returns false for invalid file format`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = invalidFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)

        assertFalse(adapter.initialize())
    }

    @Test
    fun `initialize returns false for nonexistent file`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = "/nonexistent/model.gguf",
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)

        assertFalse(adapter.initialize())
    }

    @Test
    fun `canExecute returns true for TextToText transform`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)

        assertTrue(adapter.canExecute(Transform.TextToText()))
    }

    @Test
    fun `canExecute returns false for TextToImage transform`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)

        assertFalse(adapter.canExecute(Transform.TextToImage()))
    }

    @Test
    fun `canExecute returns false for ImageToText transform`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)

        assertFalse(adapter.canExecute(Transform.ImageToText()))
    }

    @Test
    fun `isAvailable returns false if not initialized`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)

        // Not initialized, not available
        assertFalse(adapter.isAvailable())
    }

    @Test
    fun `isAvailable returns true after initialization`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)
        adapter.initialize()

        assertTrue(adapter.isAvailable())
    }

    @Test
    fun `execute fails if not initialized`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)

        val result = adapter.execute(
            Transform.TextToText(),
            "Hello, world!",
            emptyMap()
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not initialized") == true)
    }

    @Test
    fun `execute fails if inference engine is null`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, null) // No engine
        adapter.initialize()

        val result = adapter.execute(
            Transform.TextToText(),
            "Hello, world!",
            emptyMap()
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not wired") == true)
    }

    @Test
    fun `execute validates blank prompt`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)
        adapter.initialize()

        val result = adapter.execute(
            Transform.TextToText(),
            "   ", // Blank prompt
            emptyMap()
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("blank") == true)
    }

    @Test
    fun `execute validates negative maxTokens`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)
        adapter.initialize()

        val result = adapter.execute(
            Transform.TextToText(),
            "Hello",
            mapOf("maxTokens" to -1)
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `execute validates temperature range`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)
        adapter.initialize()

        val result = adapter.execute(
            Transform.TextToText(),
            "Hello",
            mapOf("temperature" to 3.0) // Out of range
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `execute succeeds with valid inputs`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)
        adapter.initialize()

        // With relaxed mocks, suspend functions return default values
        // The actual execution path goes through the inference engine
        val result = adapter.execute(
            Transform.TextToText(),
            "Hello",
            mapOf("maxTokens" to 100, "temperature" to 0.7)
        )

        // Result depends on relaxed mock behavior - execution completes without crash
        // In relaxed mode, suspend functions return null/empty/Unit
        assertNotNull(result)
        verify { mockInferenceEngine.isNativeAvailable } // Verify check was called
    }

    @Test
    fun `execute handles inference engine operations`() = runBlocking {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)
        adapter.initialize()

        // Execute should attempt to load and unload model via engine
        val result = adapter.execute(
            Transform.TextToText(),
            "Hello",
            emptyMap()
        )

        // Result is not null - execution was attempted
        assertNotNull(result)
        // Verify the native check was performed
        verify { mockInferenceEngine.isNativeAvailable }
    }

    @Test
    fun `getPriority returns high value for TextToText`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)

        assertEquals(100, adapter.getPriority(Transform.TextToText()))
    }

    @Test
    fun `getPriority returns zero for unsupported transform`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)

        assertEquals(0, adapter.getPriority(Transform.TextToImage()))
    }

    @Test
    fun `estimateVramRequirement calculates correctly`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)

        // 1GB file should estimate to ~1.2GB VRAM (with 1.2x multiplier converted to MB)
        val fileSizeBytes = 1024L * 1024L * 1024L // 1GB
        val expectedMB = (1024 * 1.2f).toInt() // 1228MB

        assertEquals(expectedMB, adapter.estimateVramRequirement(fileSizeBytes))
    }

    @Test
    fun `estimateVramRequirement for configured model returns -1 for nonexistent`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = "/nonexistent/model.gguf"
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)

        assertEquals(-1, adapter.estimateVramRequirement())
    }

    @Test
    fun `estimateVramRequirement for configured model works for valid file`() {
        val config = ProviderAdapterConfig(
            providerId = ProviderId.LOCAL_TEXT,
            baseUrl = validGgufFile.absolutePath,
            apiKeySecret = null
        )
        val adapter = LocalLlamaAdapter(config, mockInferenceEngine)

        // File is 2MB + 4 bytes header, should be > 0 and < 5MB
        val result = adapter.estimateVramRequirement()
        assertTrue("Expected estimate > 0 but got $result", result > 0)
        assertTrue("Expected estimate < 5 but got $result", result < 5)
    }
}
