package com.shadowai.app.ai

import com.shadowai.app.auth.UserPreferences
import com.shadowai.core.LocalGenerationConfig
import com.shadowai.core.LocalInferenceEngine
import com.shadowai.core.LocalModelHandle
import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SwitchableLocalInferenceEngine.
 *
 * Tests mode switching logic between isolated and in-process inference,
 * service binding/unbinding, and proper delegation to the correct
 * inference engine based on isolation preference.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwitchableLocalInferenceEngineTest {

    @RelaxedMockK
    private lateinit var localManager: LocalInferenceManager

    @RelaxedMockK
    private lateinit var isolatedManager: IsolatedInferenceManager

    @RelaxedMockK
    private lateinit var userPreferences: UserPreferences

    @RelaxedMockK
    private lateinit var mockHandle: LocalModelHandle

    @RelaxedMockK
    private lateinit var mockModelUri: android.net.Uri

    private lateinit var engine: SwitchableLocalInferenceEngine

    private val inferenceFlow = MutableStateFlow(false)

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { userPreferences.inferenceIsolationEnabled } returns inferenceFlow
    }

    private fun createEngine(isolated: Boolean = false) {
        inferenceFlow.value = isolated
        engine = SwitchableLocalInferenceEngine(
            localManager = localManager,
            isolatedManager = isolatedManager,
            userPreferences = userPreferences
        )
    }

    // ========== Mode Initialization Tests ==========

    @Test
    fun `should use non-isolated mode by default when BuildConfig allows`() = runTest {
        // Given - inferenceIsolationEnabled starts as false
        createEngine(isolated = false)

        // When - Check mode
        // Give coroutine time to collect flow
        advanceTimeBy(100)

        // Then
        assertFalse(engine.isIsolatedMode())
    }

    @Test
    fun `should use isolated mode when preference enabled`() = runTest {
        // Given - Enable isolation
        createEngine(isolated = true)
        advanceTimeBy(100)

        // Then
        assertTrue(engine.isIsolatedMode())
    }

    @Test
    fun `should react to preference changes`() = runTest {
        // Given - Start with non-isolated
        createEngine(isolated = false)
        advanceTimeBy(100)
        assertFalse(engine.isIsolatedMode())

        // When - Change preference to true
        inferenceFlow.value = true
        advanceTimeBy(100)

        // Then
        assertTrue(engine.isIsolatedMode())
    }

    // ========== isNativeAvailable Tests ==========

    @Test
    fun `isNativeAvailable should delegate to localManager in non-isolated mode`() {
        // Given
        createEngine(isolated = false)
        every { localManager.isNativeAvailable } returns true
        every { isolatedManager.isNativeAvailable } returns false

        // When
        val result = engine.isNativeAvailable

        // Then
        assertTrue(result)
    }

    @Test
    fun `isNativeAvailable should delegate to isolatedManager in isolated mode`() = runTest {
        // Given
        createEngine(isolated = true)
        advanceTimeBy(100)
        every { localManager.isNativeAvailable } returns false
        every { isolatedManager.isNativeAvailable } returns true

        // When
        val result = engine.isNativeAvailable

        // Then
        assertTrue(result)
    }

    // ========== loadModel Tests ==========

    @Test
    fun `loadModel should use localManager in non-isolated mode`() = runTest {
        // Given
        createEngine(isolated = false)
        val modelPath = "model.gguf"
        val config = LocalGenerationConfig()
        coEvery { localManager.loadModel(modelPath, config) } returns mockHandle

        // When
        val result = engine.loadModel(modelPath, config)

        // Then
        assertEquals(mockHandle, result)
        coVerify { localManager.loadModel(modelPath, config) }
        coVerify(exactly = 0) { isolatedManager.loadModel(any(), any()) }
    }

    @Test
    fun `loadModel should use isolatedManager in isolated mode`() = runTest {
        // Given
        createEngine(isolated = true)
        advanceTimeBy(100)
        val modelPath = "model.gguf"
        val config = LocalGenerationConfig()
        coEvery { isolatedManager.loadModel(modelPath, config) } returns mockHandle

        // When
        val result = engine.loadModel(modelPath, config)

        // Then
        assertEquals(mockHandle, result)
        coVerify { isolatedManager.loadModel(modelPath, config) }
        coVerify(exactly = 0) { localManager.loadModel(any(), any()) }
    }

    @Test
    fun `loadModel should return null when engine returns null`() = runTest {
        // Given
        createEngine(isolated = false)
        val modelPath = "model.gguf"
        val config = LocalGenerationConfig()
        coEvery { localManager.loadModel(modelPath, config) } returns null

        // When
        val result = engine.loadModel(modelPath, config)

        // Then
        assertNull(result)
    }

    @Test
    fun `loadModel should track handle ownership`() = runTest {
        // Given
        createEngine(isolated = false)
        val modelPath = "model.gguf"
        val config = LocalGenerationConfig()
        coEvery { localManager.loadModel(modelPath, config) } returns mockHandle

        // When - Load model
        engine.loadModel(modelPath, config)

        // Then - Verify handle ownership is tracked in ConcurrentHashMap
        // This is internal state, verified indirectly through unloadModel behavior
        coEvery { localManager.unloadModel(mockHandle) } just Runs
        engine.unloadModel(mockHandle)
        coVerify { localManager.unloadModel(mockHandle) }
    }

    // ========== unloadModel Tests ==========

    @Test
    fun `unloadModel should use owner engine from handleOwners`() = runTest {
        // Given - Load with isolated mode
        createEngine(isolated = true)
        advanceTimeBy(100)
        val modelPath = "model.gguf"
        val config = LocalGenerationConfig()
        coEvery { isolatedManager.loadModel(modelPath, config) } returns mockHandle

        // Load a model
        engine.loadModel(modelPath, config)

        // When - Unload
        coEvery { isolatedManager.unloadModel(mockHandle) } just Runs
        engine.unloadModel(mockHandle)

        // Then
        coVerify { isolatedManager.unloadModel(mockHandle) }
    }

    @Test
    fun `unloadModel should fallback to activeEngine when handle not in owners map`() = runTest {
        // Given
        createEngine(isolated = false)
        val unknownHandle = mockk<LocalModelHandle>()
        coEvery { localManager.unloadModel(unknownHandle) } just Runs

        // When - Unload a handle that wasn't loaded (not in owners map)
        engine.unloadModel(unknownHandle)

        // Then
        coVerify { localManager.unloadModel(unknownHandle) }
    }

    // ========== isModelLoaded Tests ==========

    @Test
    fun `isModelLoaded should delegate to localManager in non-isolated mode`() = runTest {
        // Given
        createEngine(isolated = false)
        val modelPath = "model.gguf"
        coEvery { localManager.isModelLoaded(modelPath) } returns true

        // When
        val result = engine.isModelLoaded(modelPath)

        // Then
        assertTrue(result)
        coVerify { localManager.isModelLoaded(modelPath) }
    }

    @Test
    fun `isModelLoaded should delegate to isolatedManager in isolated mode`() = runTest {
        // Given
        createEngine(isolated = true)
        advanceTimeBy(100)
        val modelPath = "model.gguf"
        coEvery { isolatedManager.isModelLoaded(modelPath) } returns true

        // When
        val result = engine.isModelLoaded(modelPath)

        // Then
        assertTrue(result)
        coVerify { isolatedManager.isModelLoaded(modelPath) }
    }

    // ========== getAvailableModels Tests ==========

    @Test
    fun `getAvailableModels should delegate to localManager in non-isolated mode`() = runTest {
        // Given
        createEngine(isolated = false)
        val models = listOf("model1.gguf", "model2.gguf")
        coEvery { localManager.getAvailableModels() } returns models

        // When
        val result = engine.getAvailableModels()

        // Then
        assertEquals(models, result)
        coVerify { localManager.getAvailableModels() }
    }

    @Test
    fun `getAvailableModels should delegate to isolatedManager in isolated mode`() = runTest {
        // Given
        createEngine(isolated = true)
        advanceTimeBy(100)
        val models = listOf("model1.gguf", "model2.gguf")
        coEvery { isolatedManager.getAvailableModels() } returns models

        // When
        val result = engine.getAvailableModels()

        // Then
        assertEquals(models, result)
        coVerify { isolatedManager.getAvailableModels() }
    }

    @Test
    fun `getAvailableModels should return empty list when no models`() = runTest {
        // Given
        createEngine(isolated = false)
        coEvery { localManager.getAvailableModels() } returns emptyList()

        // When
        val result = engine.getAvailableModels()

        // Then
        assertTrue(result.isEmpty())
    }

    // ========== warmup Tests ==========

    @Test
    fun `warmup should return success in non-isolated mode`() = runTest {
        // Given
        createEngine(isolated = false)

        // When
        val result = engine.warmup()

        // Then
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { isolatedManager.bindService() }
    }

    @Test
    fun `warmup should bind service in isolated mode`() = runTest {
        // Given
        createEngine(isolated = true)
        advanceTimeBy(100)
        coEvery { isolatedManager.bindService() } returns Result.success(Unit)

        // When
        val result = engine.warmup()

        // Then
        assertTrue(result.isSuccess)
        coVerify { isolatedManager.bindService() }
    }

    @Test
    fun `warmup should return failure when bindService fails`() = runTest {
        // Given
        createEngine(isolated = true)
        advanceTimeBy(100)
        val error = Exception("Bind failed")
        coEvery { isolatedManager.bindService() } returns Result.failure(error)

        // When
        val result = engine.warmup()

        // Then
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    // ========== setCustomModelTreeUri Tests ==========

    @Test
    fun `setCustomModelTreeUri should update both managers`() {
        // Given
        createEngine(isolated = false)
        justRun { localManager.setCustomModelTreeUri(mockModelUri) }
        justRun { isolatedManager.setCustomModelTreeUri(mockModelUri) }

        // When
        engine.setCustomModelTreeUri(mockModelUri)

        // Then
        verify { localManager.setCustomModelTreeUri(mockModelUri) }
        verify { isolatedManager.setCustomModelTreeUri(mockModelUri) }
    }

    @Test
    fun `setCustomModelTreeUri should handle null uri`() {
        // Given
        createEngine(isolated = false)
        justRun { localManager.setCustomModelTreeUri(null) }
        justRun { isolatedManager.setCustomModelTreeUri(null) }

        // When
        engine.setCustomModelTreeUri(null)

        // Then
        verify { localManager.setCustomModelTreeUri(null) }
        verify { isolatedManager.setCustomModelTreeUri(null) }
    }

    // ========== switchMode Tests ==========

    @Test
    fun `switchMode should return success when already in requested mode`() = runTest {
        // Given - Start in non-isolated mode
        createEngine(isolated = false)

        // When - Switch to non-isolated (same mode)
        val result = engine.switchMode(isolated = false)

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `switchMode should bind service when switching to isolated mode`() = runTest {
        // Given - Start in non-isolated mode
        createEngine(isolated = false)
        initialDelay()
        coEvery { isolatedManager.bindService() } returns Result.success(Unit)

        // When - Switch to isolated mode
        val result = engine.switchMode(isolated = true)

        // Then
        assertTrue(result.isSuccess)
        coVerify { isolatedManager.bindService() }
        assertTrue(engine.isIsolatedMode())
    }

    @Test
    fun `switchMode should return failure when bindService fails`() = runTest {
        // Given - Start in non-isolated mode
        createEngine(isolated = false)
        initialDelay()
        val error = Exception("Service bind failed")
        coEvery { isolatedManager.bindService() } returns Result.failure(error)

        // When - Try to switch to isolated mode
        val result = engine.switchMode(isolated = true)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to bind isolated inference service") == true)
    }

    @Test
    fun `switchMode should not require binding when switching to non-isolated mode`() = runTest {
        // Given - Start in isolated mode
        createEngine(isolated = true)
        advanceTimeBy(100)

        // When - Switch to non-isolated mode
        val result = engine.switchMode(isolated = false)

        // Then
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { isolatedManager.bindService() }
        assertFalse(engine.isIsolatedMode())
    }

    @Test
    fun `switchMode should update isIsolatedMode state`() = runTest {
        // Given - Start in non-isolated mode
        createEngine(isolated = false)
        initialDelay()
        coEvery { isolatedManager.bindService() } returns Result.success(Unit)

        // When - Switch to isolated and back
        engine.switchMode(isolated = true)
        engine.switchMode(isolated = false)

        // Then
        assertFalse(engine.isIsolatedMode())
    }

    // ========== isIsolatedMode Tests ==========

    @Test
    fun `isIsolatedMode should return false initially when preference is false`() = runTest {
        // Given
        createEngine(isolated = false)
        advanceTimeBy(100)

        // Then
        assertFalse(engine.isIsolatedMode())
    }

    @Test
    fun `isIsolatedMode should return true initially when preference is true`() = runTest {
        // Given
        createEngine(isolated = true)
        advanceTimeBy(100)

        // Then
        assertTrue(engine.isIsolatedMode())
    }

    // ========== Error Handling Tests ==========

    @Test
    fun `switchMode should handle exceptions gracefully`() = runTest {
        // Given - Start in non-isolated mode
        createEngine(isolated = false)
        initialDelay()
        coEvery { isolatedManager.bindService() } throws RuntimeException("Unexpected error")

        // When - Try to switch to isolated mode
        val result = engine.switchMode(isolated = true)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `operations should continue after mode switch`() = runTest {
        // Given
        createEngine(isolated = false)
        initialDelay()
        coEvery { isolatedManager.bindService() } returns Result.success(Unit)
        coEvery { isolatedManager.loadModel(any(), any()) } returns mockHandle

        // When - Switch to isolated and load model
        engine.switchMode(isolated = true)
        val handle = engine.loadModel("model.gguf", LocalGenerationConfig())

        // Then
        assertEquals(mockHandle, handle)
        coVerify { isolatedManager.loadModel("model.gguf", any()) }
    }

    // ========== Handle Ownership Tracking Tests ==========

    @Test
    fun `handle ownership should be set correctly on model load`() = runTest {
        // Given - Isolated mode
        createEngine(isolated = true)
        advanceTimeBy(100)
        val config = LocalGenerationConfig()
        coEvery { isolatedManager.loadModel("test.gguf", config) } returns mockHandle

        // When - Load and unload
        engine.loadModel("test.gguf", config)

        // Then - Should be able to unload with correct owner
        coEvery { isolatedManager.unloadModel(mockHandle) } just Runs
        engine.unloadModel(mockHandle)
        coVerify { isolatedManager.unloadModel(mockHandle) }
    }

    @Test
    fun `handle ownership should use activeEngine after unload of untracked handle`() = runTest {
        // Given - Non-isolated mode
        createEngine(isolated = false)
        val unknownHandle = mockk<LocalModelHandle>()
        coEvery { localManager.unloadModel(unknownHandle) } just Runs

        // When - Unload a handle that was never loaded
        engine.unloadModel(unknownHandle)

        // Then - Falls back to activeEngine
        coVerify { localManager.unloadModel(unknownHandle) }
    }

    // Helper to give time for flow collection to stabilize
    private suspend fun initialDelay() {
        advanceTimeBy(50)
    }
}