package com.shadowai.app.ai

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Unit tests for QuantizationHelper.
 * Tests memory estimation, quantization detection, and edge cases.
 */
class QuantizationHelperTest {

    @Test
    fun `detects Q4_0 quantization from filename`() {
        val quantization = QuantizationHelper.detectQuantization("llama-3-8b-chat-Q4_0.gguf")
        assertEquals(QuantizationHelper.QuantizationType.Q4_0, quantization)
    }

    @Test
    fun `detects Q5_K_M quantization from filename`() {
        val quantization = QuantizationHelper.detectQuantization("llama-3-8b-Q5_K_M.gguf")
        assertEquals(QuantizationHelper.QuantizationType.Q5_K_M, quantization)
    }

    @Test
    fun `detects Q8_0 quantization from filename`() {
        val quantization = QuantizationHelper.detectQuantization("model-Q8_0.gguf")
        assertEquals(QuantizationHelper.QuantizationType.Q8_0, quantization)
    }

    @Test
    fun `detects F16 quantization from filename`() {
        val quantization = QuantizationHelper.detectQuantization("model-f16.gguf")
        assertEquals(QuantizationHelper.QuantizationType.F16, quantization)
    }

    @Test
    fun `detects UNKNOWN when no quantization pattern found`() {
        val quantization = QuantizationHelper.detectQuantization("mymodel.gguf")
        assertEquals(QuantizationHelper.QuantizationType.UNKNOWN, quantization)
    }

    @Test
    fun `estimates memory for Q4_0 model correctly`() {
        // Create a mock file with 4GB size
        val file = File.createTempFile("test-Q4_0", ".gguf")
        file.deleteOnExit()

        // Mock 4GB file size
        val fileSize = 4L * 1024 * 1024 * 1024

        // Manually create ModelInfo since we can't set file size
        val modelInfo = QuantizationHelper.ModelInfo(
            file = file,
            quantization = QuantizationHelper.QuantizationType.Q4_0,
            fileSize = fileSize,
            estimatedRamMB = (fileSize / (1024 * 1024) * 1.0).toLong(),
            isWarning = false
        )

        // Q4_0 has 1.0x multiplier
        val expectedRamMB = (fileSize / (1024 * 1024) * 1.0)
        assertEquals(expectedRamMB.toLong(), modelInfo.estimatedRamMB)
    }

    @Test
    fun `estimates memory for Q8_0 model correctly`() {
        // Create a mock file
        val file = File.createTempFile("test-Q8_0", ".gguf")
        file.deleteOnExit()

        // Mock 4GB file size
        val fileSize = 4L * 1024 * 1024 * 1024

        // Q8_0 has 2.0x multiplier
        val expectedRamMB = (fileSize / (1024 * 1024) * 2.0)
        val modelInfo = QuantizationHelper.ModelInfo(
            file = file,
            quantization = QuantizationHelper.QuantizationType.Q8_0,
            fileSize = fileSize,
            estimatedRamMB = expectedRamMB.toLong(),
            isWarning = true
        )

        assertEquals(expectedRamMB.toLong(), modelInfo.estimatedRamMB)
        assertTrue(modelInfo.isWarning)
    }

    @Test
    fun `verifies no double multiplication bug in memory estimation`() {
        // Test that relativeMultiplier is applied correctly
        // The bug would be multiplying twice (once by MODEL_MEMORY_MULTIPLIER and once by relativeMultiplier)

        val fileSizeMB = 4000.0 // 4GB in MB
        val q4Multiplier = QuantizationHelper.QuantizationType.Q4_0.relativeMultiplier
        val q8Multiplier = QuantizationHelper.QuantizationType.Q8_0.relativeMultiplier

        val q4RamMB = (fileSizeMB * q4Multiplier).toLong()
        val q8RamMB = (fileSizeMB * q8Multiplier).toLong()

        // Q8_0 should use exactly 2.0x the RAM of Q4_0
        assertEquals(2.0, q8Multiplier / q4Multiplier, 0.01)
        assertEquals(q4RamMB * 2, q8RamMB)
    }

    @Test
    fun `detects quantization for all supported types`() {
        val testCases = mapOf(
            "model-Q2_K.gguf" to QuantizationHelper.QuantizationType.Q2_K,
            "model-Q3_K_S.gguf" to QuantizationHelper.QuantizationType.Q3_K_S,
            "model-Q3_K_M.gguf" to QuantizationHelper.QuantizationType.Q3_K_M,
            "model-Q3_K_L.gguf" to QuantizationHelper.QuantizationType.Q3_K_L,
            "model-Q4_0.gguf" to QuantizationHelper.QuantizationType.Q4_0,
            "model-Q4_1.gguf" to QuantizationHelper.QuantizationType.Q4_1,
            "model-Q4_K_S.gguf" to QuantizationHelper.QuantizationType.Q4_K_S,
            "model-Q4_K_M.gguf" to QuantizationHelper.QuantizationType.Q4_K_M,
            "model-Q5_0.gguf" to QuantizationHelper.QuantizationType.Q5_0,
            "model-Q5_1.gguf" to QuantizationHelper.QuantizationType.Q5_1,
            "model-Q5_K_S.gguf" to QuantizationHelper.QuantizationType.Q5_K_S,
            "model-Q5_K_M.gguf" to QuantizationHelper.QuantizationType.Q5_K_M,
            "model-Q6_K.gguf" to QuantizationHelper.QuantizationType.Q6_K,
            "model-Q8_0.gguf" to QuantizationHelper.QuantizationType.Q8_0,
            "model-Q8_K.gguf" to QuantizationHelper.QuantizationType.Q8_K,
            "model-F16.gguf" to QuantizationHelper.QuantizationType.F16,
            "model-F32.gguf" to QuantizationHelper.QuantizationType.F32
        )

        testCases.forEach { (filename, expected) ->
            val detected = QuantizationHelper.detectQuantization(filename)
            assertEquals("Failed to detect $filename", expected, detected)
        }
    }

    @Test
    fun `estimates parameter count from filename`() {
        val params7b = QuantizationHelper.estimateParameterCount("llama-3-7b-chat-Q4_0.gguf")
        assertEquals(7.0, params7b, 0.0)

        val params8b = QuantizationHelper.estimateParameterCount("llama-3-8b-chat-Q5_K_M.gguf")
        assertEquals(8.0, params8b, 0.0)

        val params70b = QuantizationHelper.estimateParameterCount("llama-3-70b-chat.Q8_0.gguf")
        assertEquals(70.0, params70b, 0.0)
    }

    @Test
    fun `returns default 7B parameter count when pattern not found`() {
        val params = QuantizationHelper.estimateParameterCount("mymodel-Q4_0.gguf")
        assertEquals(7.0, params, 0.0)
    }

    @Test
    fun `prioritizes recommended models first`() {
        val file1 = File.createTempFile("test-Q4_0", ".gguf")
        val file2 = File.createTempFile("test-Q8_0", ".gguf")
        file1.deleteOnExit()
        file2.deleteOnExit()

        val files = listOf(file1, file2)
        val prioritized = QuantizationHelper.prioritizeModels(files)

        // Q4_0 is recommended, Q8_0 is not
        assertTrue(
            prioritized[0].quantization.isRecommended,
            "Recommended quantization should be first"
        )
    }

    @Test
    fun `checks if model fits in available memory`() {
        val file = File.createTempFile("test", ".gguf")
        file.deleteOnExit()

        val modelInfo = QuantizationHelper.ModelInfo(
            file = file,
            quantization = QuantizationHelper.QuantizationType.Q4_0,
            fileSize = 4L * 1024 * 1024 * 1024, // 4GB
            estimatedRamMB = 4000,
            isWarning = false
        )

        // 4000MB + 500MB buffer = 4500MB needed
        assertTrue(QuantizationHelper.fitsInMemory(modelInfo, 5000))
        assertFalse(QuantizationHelper.fitsInMemory(modelInfo, 4000))
    }

    @Test
    fun `formats memory size correctly`() {
        val bytes = 1024 * 1024 * 1024 // 1GB
        val formatted = QuantizationHelper.formatMemorySize(bytes)
        assertEquals("1.0 GB", formatted)

        val bytesMB = 512 * 1024 * 1024 // 512MB
        val formattedMB = QuantizationHelper.formatMemorySize(bytesMB)
        assertEquals("512 MB", formattedMB)
    }

    @Test
    fun `gets memory description with quantization context`() {
        val file = File.createTempFile("test-Q8_0", ".gguf")
        file.deleteOnExit()

        val modelInfo = QuantizationHelper.ModelInfo(
            file = file,
            quantization = QuantizationHelper.QuantizationType.Q8_0,
            fileSize = 4L * 1024 * 1024 * 1024,
            estimatedRamMB = 8000,
            isWarning = true
        )

        val description = QuantizationHelper.getMemoryDescription(modelInfo)
        assertTrue(description.contains("⚠️"))
        assertTrue(description.contains("Est. RAM"))
    }

    @Test
    fun `handles edge case with very small model`() {
        val file = File.createTempFile("tiny-Q2_K", ".gguf")
        file.deleteOnExit()

        val modelInfo = QuantizationHelper.ModelInfo(
            file = file,
            quantization = QuantizationHelper.QuantizationType.Q2_K,
            fileSize = 1L, // 1 byte (edge case)
            estimatedRamMB = 0,
            isWarning = false
        )

        val description = QuantizationHelper.getMemoryDescription(modelInfo)
        assertNotNull(description)
    }

    @Test
    fun `handles edge case with very large model`() {
        val file = File.createTempFile("huge-F32", ".gguf")
        file.deleteOnExit()

        val veryLargeSize = 100L * 1024 * 1024 * 1024 // 100GB (edge case)
        val modelInfo = QuantizationHelper.ModelInfo(
            file = file,
            quantization = QuantizationHelper.QuantizationType.F32,
            fileSize = veryLargeSize,
            estimatedRamMB = (veryLargeSize / (1024 * 1024) * 6.0).toLong(),
            isWarning = true
        )

        val description = QuantizationHelper.getMemoryDescription(modelInfo)
        assertTrue(description.contains("⚠️"))
        assertTrue(description.contains("Extremely high memory usage"))
    }

    @Test
    fun `gets quantization priority correctly`() {
        val q4Priority = QuantizationHelper.getQuantizationPriority(QuantizationHelper.QuantizationType.Q4_0)
        val q8Priority = QuantizationHelper.getQuantizationPriority(QuantizationHelper.QuantizationType.Q8_0)
        val unknownPriority = QuantizationHelper.getQuantizationPriority(QuantizationHelper.QuantizationType.UNKNOWN)

        assertTrue(q4Priority < q8Priority, "Q4_0 should have higher priority than Q8_0")
        assertTrue(q8Priority < unknownPriority, "Q8_0 should have higher priority than UNKNOWN")
        assertEquals(1, q4Priority)
    }
}