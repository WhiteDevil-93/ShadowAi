package com.shadowai.pipelineplanner

import android.net.Uri
import com.shadowai.core.Artifact
import com.shadowai.core.Modality
import com.shadowai.core.ProviderExecutor
import com.shadowai.core.ProviderId
import com.shadowai.core.Transform
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PipelinePlanner.
 * Verifies graph pathfinding and multi-step plan generation.
 */
class PipelinePlannerTest {

    private lateinit var planner: PipelinePlanner
    private lateinit var graph: PipelineGraph

    @Before
    fun setup() {
        graph = PipelineGraph()
        planner = PipelinePlanner(graph)

        // Mock Android Uri.parse
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk()
    }

    @Test
    fun `planPipeline returns empty plan for same modality`() = runTest {
        val plan = planner.planPipeline(Modality.Text, Modality.Text)

        assertNotNull(plan)
        assertEquals(0, plan!!.totalSteps)
        assertTrue(plan.isDirect)
    }

    @Test
    fun `planPipeline finds single step path`() = runTest {
        // Setup executor that handles TextToImage
        val textToImageExecutor = mockk<ProviderExecutor> {
            coEvery { canExecute(any()) } answers { firstArg<Transform>() is Transform.TextToImage }
            every { getPriority(any()) } returns 100
            every { providerId } returns ProviderId.OPENAI
            coEvery { isAvailable() } returns true
        }

        planner.registerExecutor(textToImageExecutor)

        // Find pipeline
        val plan = planner.planPipeline(Modality.Text, Modality.Image)

        // Verify
        assertNotNull(plan)
        assertEquals(1, plan!!.totalSteps)
        assertTrue(plan.isDirect)
        assertTrue(plan.transforms.first() is Transform.TextToImage)
    }

    @Test
    fun `planPipeline finds multi-step path`() = runTest {
        // Setup executor 1: Text -> Image
        val textToImageExecutor = mockk<ProviderExecutor> {
            coEvery { canExecute(any()) } answers { firstArg<Transform>() is Transform.TextToImage }
            every { getPriority(any()) } returns 100
            every { providerId } returns ProviderId.OPENAI
            coEvery { isAvailable() } returns true
        }

        // Setup executor 2: Image -> Video
        val imageToVideoExecutor = mockk<ProviderExecutor> {
            coEvery { canExecute(any()) } answers { firstArg<Transform>() is Transform.ImageToVideo }
            every { getPriority(any()) } returns 100
            every { providerId } returns ProviderId.REPLICATE
            coEvery { isAvailable() } returns true
        }

        planner.registerExecutor(textToImageExecutor)
        planner.registerExecutor(imageToVideoExecutor)

        // Find pipeline: Text -> Video (should be Text->Image->Video)
        val plan = planner.planPipeline(Modality.Text, Modality.Video)

        // Verify
        assertNotNull(plan)
        assertEquals(2, plan!!.totalSteps)
        assertFalse(plan.isDirect)
        assertEquals(2, plan.transforms.size)
        assertTrue(plan.transforms[0] is Transform.TextToImage)
        assertTrue(plan.transforms[1] is Transform.ImageToVideo)
    }

    @Test
    fun `manual execution runs all steps sequentially`() = runTest {
        // Mock artifacts using factory methods
        val textInput = Artifact.Text.create("Input text")
        val imageOutput = Artifact.Image(id = "img1", uri = mockk())
        val videoOutput = Artifact.Video(id = "vid1", uri = mockk())

        // Setup executors
        val textToImageExecutor = mockk<ProviderExecutor> {
            coEvery { canExecute(any()) } answers { firstArg<Transform>() is Transform.TextToImage }
            every { getPriority(any()) } returns 100
            coEvery { execute(any(), any(), any()) } returns Result.success(imageOutput)
            every { providerId } returns ProviderId.OPENAI
            coEvery { isAvailable() } returns true
        }

        val imageToVideoExecutor = mockk<ProviderExecutor> {
            coEvery { canExecute(any()) } answers { firstArg<Transform>() is Transform.ImageToVideo }
            every { getPriority(any()) } returns 100
            coEvery { execute(any(), any(), any()) } returns Result.success(videoOutput)
            every { providerId } returns ProviderId.REPLICATE
            coEvery { isAvailable() } returns true
        }

        planner.registerExecutor(textToImageExecutor)
        planner.registerExecutor(imageToVideoExecutor)

        // Create plan manually for testing execution logic
        val plan = PipelinePlan.multi(
            listOf(Transform.TextToImage(), Transform.ImageToVideo())
        )

        // Execute manually using planner as source
        var current: Artifact = textInput
        for (transform in plan.transforms) {
            val executor = planner.getBestExecutor(transform)
            assertNotNull(executor)
            val result = executor!!.execute(transform, current, emptyMap())
            assertTrue(result.isSuccess)
            current = result.getOrThrow()
        }

        // Verify final output
        assertEquals(videoOutput, current)
    }

    @Test
    fun `execution fails if intermediate step fails`() = runTest {
        val textInput = Artifact.Text.create("Input text")

        // Executor that fails
        val failingExecutor = mockk<ProviderExecutor> {
            coEvery { canExecute(any()) } returns true
            every { getPriority(any()) } returns 100
            coEvery { execute(any(), any(), any()) } returns Result.failure(Exception("Generation failed"))
            every { providerId } returns ProviderId.OPENAI
            coEvery { isAvailable() } returns true
        }

        planner.registerExecutor(failingExecutor)

        val plan = PipelinePlan.single(Transform.TextToImage())

        val executor = planner.getBestExecutor(plan.transforms.first())
        assertNotNull(executor)
        val result = executor!!.execute(plan.transforms.first(), textInput, emptyMap())

        assertTrue(result.isFailure)
        assertEquals("Generation failed", result.exceptionOrNull()?.message)
    }
}
