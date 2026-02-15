package com.shadowai.app.ai

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Comprehensive unit tests for QuantizationHelper.
 *
 * Tests:
 * - Memory estimation for each quantization type
 * - Edge cases (small/large models)
 * - Verify no double multiplication bug
 * - Quantization detection from filenames
 * - Model prioritization logic
 * - Memory formatting utilities
 */
@RunWith(Parameterized::class)
class QuantizationHelperParameterizedTest(
    private val filename: String,
    private val expectedQuantization: QuantizationHelper.QuantizationType,
    private val expectedMultiplier: Double
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1}")
        fun data(): Collection<Array<Any>> = listOf(
            // Q2 variants
            arrayOf("model-Q2_K.gguf", QuantizationHelper.QuantizationType.Q2_K, 0.5),
            
            // Q3 variants
            arrayOf("model-Q3_K.gguf", QuantizationHelper.QuantizationType.Q3_K, 0.6),
            arrayOf("model-Q3_K_S.gguf", QuantizationHelper.QuantizationType.Q3_K_S, 0.6),
            arrayOf("model-Q3_K_M.gguf", QuantizationHelper.QuantizationType.Q3_K_M, 0.65),
            arrayOf("model-Q3_K_L.gguf", QuantizationHelper.QuantizationType.Q3_K_L, 0.7),
            
            // Q4 variants
            arrayOf("model-Q4_0.gguf", QuantizationHelper.QuantizationType.Q4_0, 1.0),
            arrayOf("model-Q4.0.gguf", QuantizationHelper.QuantizationType.Q4_0, 1.0),
            arrayOf("model-Q4_1.gguf", QuantizationHelper.QuantizationType.Q4_1, 1.05),
            arrayOf("model-Q4.1.gguf", QuantizationHelper.QuantizationType.Q4_1, 1.05),
            arrayOf("model-Q4_K.gguf", QuantizationHelper.QuantizationType.Q4_K, 1.1),
            arrayOf("model-Q4_K_S.gguf", QuantizationHelper.QuantizationType.Q4_K_S, 0.95),
            arrayOf("model-Q4_K_M.gguf", QuantizationHelper.QuantizationType.Q4_K_M, 1.0),
            
            // Q5 variants
            arrayOf("model-Q5_0.gguf", QuantizationHelper.QuantizationType.Q5_0, 1.2),
            arrayOf("model-Q5.0.gguf", QuantizationHelper.QuantizationType.Q5_0, 1.2),
            arrayOf("model-Q5_1.gguf", QuantizationHelper.QuantizationType.Q5_1, 1.25),
            arrayOf("model-Q5.1.gguf", QuantizationHelper.QuantizationType.Q5_1, 1.25),
            arrayOf("model-Q5_K.gguf", QuantizationHelper.QuantizationType.Q5_K, 1.3),
            arrayOf("model-Q5_K_S.gguf", QuantizationHelper.QuantizationType.Q5_K_S, 1.15),
            arrayOf("model-Q5_K_M.gguf", QuantizationHelper.QuantizationType.Q5_K_M, 1.25),
            
            // Q6 variants
            arrayOf("model-Q6_K.gguf", QuantizationHelper.QuantizationType.Q6_K, 1.5),
            
            // Q8 variants
            arrayOf("model-Q8_0.gguf", QuantizationHelper.QuantizationType.Q8_0, 2.0),
            arrayOf("model-Q8.0.gguf", QuantizationHelper.QuantizationType.Q8_0, 2.0),
            arrayOf("model-Q8_K.gguf", QuantizationHelper.QuantizationType.Q8_K, 2.1),
            
            // F16/F32
            arrayOf("model-F16.gguf", QuantizationHelper.QuantizationType.F16, 4.0),
            arrayOf("model-F32.gguf", QuantizationHelper.QuantizationType.F32, 6.0),
            
            // Case insensitivity
            arrayOf("MODEL-Q4_0.GGUF", QuantizationHelper.QuantizationType.Q4_0, 1.0),
            arrayOf("Model-q5_k_m.gguf", QuantizationHelper.QuantizationType.Q5_K_M, 1.25)
        )
    }

    @Test
    fun `detects quantization from filename`() {
        val result = QuantizationHelper.detectQuantization(filename)
        assertEquals("Failed to detect $expectedQuantization from $filename", 
            expectedQuantization, result)
    }

    @Test
    fun `quantization has correct multiplier`() {
        assertEquals(expectedMultiplier, expectedQuantization.relativeMultiplier, 0.001)
    }
}

class QuantizationHelperTest {

    // ========== Memory Estimation Tests ==========

    @Test
    fun `memory estimation uses single multiplication only - no double multiplication bug`() {
        // Create a temp file of known size (4GB = 4096 MB)
        val fileSizeBytes = 4L * 1024 * 1024 * 1024 // 4GB
        val tempFile = createTempFileOfSize(fileSizeBytes)
        
        try {
            val modelInfo = QuantizationHelper.getModelInfo(tempFile)
            
            // For Q4_0 (1.0x multiplier), 4GB file should estimate to ~4GB RAM
            // NOT 8GB (which would indicate double multiplication with MODEL_MEMORY_MULTIPLIER)
            val fileSizeMB = fileSizeBytes / (1024 * 1024)
            val expectedRAM = (fileSizeMB * 1.0).toLong()
            
            assertEquals("Memory estimation should NOT double multiply", 
                expectedRAM, modelInfo.estimatedRamMB)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `memory estimation for Q4_0 is baseline`() {
        val fileSizeMB = 4096L // 4GB file
        val tempFile = createTempFileOfSize(fileSizeMB * 1024 * 1024)
        
        try {
            val modelInfo = QuantizationHelper.getModelInfo(tempFile)
            
            assertEquals(QuantizationHelper.QuantizationType.Q4_0, modelInfo.quantization)
            // Q4_0 has 1.0x multiplier
            assertEquals(fileSizeMB, modelInfo.estimatedRamMB)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `memory estimation for Q8_0 is double baseline`() {
        val fileSizeMB = 4096L
        val tempFile = File.createTempFile("model-Q8_0-", ".gguf")
        
        try {
            // Create sparse file by writing at offset
            tempFile.outputStream().use { fos ->
                fos.channel.position(fileSizeMB * 1024 * 1024 - 1)
                fos.write(0)
            }
            
            val modelInfo = QuantizationHelper.getModelInfo(tempFile)
            
            assertEquals(QuantizationHelper.QuantizationType.Q8_0, modelInfo.quantization)
            // Q8_0 has 2.0x multiplier
            assertEquals((fileSizeMB * 2.0).toLong(), modelInfo.estimatedRamMB)
            assertTrue("Q8_0 should have warning", modelInfo.isWarning)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `memory estimation for F32 is 6x baseline`() {
        val fileSizeMB = 4096L
        val tempFile = File.createTempFile("model-F32-", ".gguf")
        
        try {
            tempFile.outputStream().use { fos ->
                fos.channel.position(fileSizeMB * 1024 * 1024 - 1)
                fos.write(0)
            }
            
            val modelInfo = QuantizationHelper.getModelInfo(tempFile)
            
            assertEquals(QuantizationHelper.QuantizationType.F32, modelInfo.quantization)
            // F32 has 6.0x multiplier
            assertEquals((fileSizeMB * 6.0).toLong(), modelInfo.estimatedRamMB)
        } finally {
            tempFile.delete()
        }
    }

    // ========== Small Model Edge Cases ==========

    @Test
    fun `handles very small model files`() {
        // 1MB model (tiny edge case)
        val tempFile = createTempFileOfSize(1024 * 1024)
        
        try {
            val modelInfo = QuantizationHelper.getModelInfo(tempFile)
            
            assertNotNull(modelInfo)
            assertEquals(1L, modelInfo.fileSize / (1024 * 1024))
            assertTrue("Small model should fit in any memory", 
                QuantizationHelper.fitsInMemory(modelInfo, 512))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `handles empty model files gracefully`() {
        val tempFile = File.createTempFile("model-Q4_0-", ".gguf")
        
        try {
            // Empty file
            val modelInfo = QuantizationHelper.getModelInfo(tempFile)
            
            assertNotNull(modelInfo)
            assertEquals(0L, modelInfo.estimatedRamMB)
            assertTrue("Empty model should fit anywhere", 
                QuantizationHelper.fitsInMemory(modelInfo, 0))
        } finally {
            tempFile.delete()
        }
    }

    // ========== Large Model Edge Cases ==========

    @Test
    fun `handles very large model files`() {
        // Simulate 70B model (~40GB file for Q4)
        val fileSizeMB = 40 * 1024L // 40GB
        val tempFile = File.createTempFile("llama-70b-Q4_0-", ".gguf")
        
        try {
            // Create sparse file
            tempFile.outputStream().use { fos ->
                fos.channel.position(fileSizeMB * 1024 * 1024 - 1)
                fos.write(0)
            }
            
            val modelInfo = QuantizationHelper.getModelInfo(tempFile)
            
            assertNotNull(modelInfo)
            assertTrue("Should detect Q4_0", 
                modelInfo.quantization == QuantizationHelper.QuantizationType.Q4_0)
            assertFalse("Large Q4 model should not fit in 8GB", 
                QuantizationHelper.fitsInMemory(modelInfo, 8 * 1024))
            assertTrue("Large Q4 model should fit in 64GB", 
                QuantizationHelper.fitsInMemory(modelInfo, 64 * 1024))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `parameter count estimation from filename`() {
        assertEquals(7.0, QuantizationHelper.estimateParameterCount("llama-7b-chat.gguf"), 0.001)
        assertEquals(8.0, QuantizationHelper.estimateParameterCount("meta-llama-3-8b.gguf"), 0.001)
        assertEquals(13.0, QuantizationHelper.estimateParameterCount("llama-13b.gguf"), 0.001)
        assertEquals(70.0, QuantizationHelper.estimateParameterCount("llama-70b-instruct.gguf"), 0.001)
        assertEquals(3.0, QuantizationHelper.estimateParameterCount("phi-4-3b.gguf"), 0.001)
        
        // Default when no match
        assertEquals(7.0, QuantizationHelper.estimateParameterCount("unknown-model.gguf"), 0.001)
    }

    // ========== Quantization Detection Edge Cases ==========

    @Test
    fun `returns unknown for non-quantized filenames`() {
        assertEquals(QuantizationHelper.QuantizationType.UNKNOWN, 
            QuantizationHelper.detectQuantization("model.gguf"))
        assertEquals(QuantizationHelper.QuantizationType.UNKNOWN, 
            QuantizationHelper.detectQuantization("random-file.txt"))
        assertEquals(QuantizationHelper.QuantizationType.UNKNOWN, 
            QuantizationHelper.detectQuantization(""))
    }

    @Test
    fun `handles multiple quant patterns in filename - uses first match`() {
        // If someone accidentally puts multiple quant patterns, use first
        val result = QuantizationHelper.detectQuantization("model-Q4_0-Q8_0.gguf")
        assertEquals(QuantizationHelper.QuantizationType.Q4_0, result)
    }

    @Test
    fun `handles hyphen and underscore variations`() {
        assertEquals(QuantizationHelper.QuantizationType.Q5_K_M,
            QuantizationHelper.detectQuantization("model-Q5-K-M.gguf"))
        assertEquals(QuantizationHelper.QuantizationType.Q4_0,
            QuantizationHelper.detectQuantization("model-Q4-0.gguf"))
    }

    // ========== Model Prioritization Tests ==========

    @Test
    fun `prioritize models puts recommended first`() {
        val files = listOf(
            createTempFileWithName("model-F32.gguf"),
            createTempFileWithName("model-Q4_0.gguf"),
            createTempFileWithName("model-Q8_0.gguf")
        )
        
        try {
            val prioritized = QuantizationHelper.prioritizeModels(files)
            
            // Q4_0 is recommended, F32 and Q8_0 are not
            assertTrue("Q4_0 should be first (recommended)", 
                prioritized[0].quantization == QuantizationHelper.QuantizationType.Q4_0)
            assertTrue("Recommended should come before non-recommended",
                prioritized[0].quantization.isRecommended && !prioritized[2].quantization.isRecommended)
        } finally {
            files.forEach { it.delete() }
        }
    }

    @Test
    fun `prioritize models sorts by memory when both recommended`() {
        val files = listOf(
            createTempFileWithName("model-Q6_K.gguf", 6 * 1024 * 1024 * 1024L), // 6GB
            createTempFileWithName("model-Q4_0.gguf", 4 * 1024 * 1024 * 1024L), // 4GB
            createTempFileWithName("model-Q5_0.gguf", 5 * 1024 * 1024 * 1024L)  // 5GB
        )
        
        try {
            val prioritized = QuantizationHelper.prioritizeModels(files)
            
            // Within recommended, should sort by estimated RAM (lower = better)
            val ramValues = prioritized.map { it.estimatedRamMB }
            assertTrue("Should be sorted by RAM ascending", 
                ramValues[0] <= ramValues[1] && ramValues[1] <= ramValues[2])
        } finally {
            files.forEach { it.delete() }
        }
    }

    @Test
    fun `prioritize handles empty list`() {
        val result = QuantizationHelper.prioritizeModels(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `prioritize handles single model`() {
        val file = createTempFileWithName("model-Q4_0.gguf")
        
        try {
            val result = QuantizationHelper.prioritizeModels(listOf(file))
            assertEquals(1, result.size)
            assertEquals(QuantizationHelper.QuantizationType.Q4_0, result[0].quantization)
        } finally {
            file.delete()
        }
    }

    // ========== Memory Formatting Tests ==========

    @Test
    fun `format memory in MB for small sizes`() {
        assertEquals("512 MB", QuantizationHelper.formatMemorySize(512 * 1024 * 1024))
        assertEquals("999 MB", QuantizationHelper.formatMemorySize(999 * 1024 * 1024))
    }

    @Test
    fun `format memory in GB for large sizes`() {
        assertEquals("1.0 GB", QuantizationHelper.formatMemorySize(1024 * 1024 * 1024))
        assertEquals("4.0 GB", QuantizationHelper.formatMemorySize(4L * 1024 * 1024 * 1024))
        assertEquals("7.5 GB", QuantizationHelper.formatMemorySize((7.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `getMemoryDescription includes file size and RAM estimate`() {
        val tempFile = createTempFileWithName("model-Q8_0.gguf", 4L * 1024 * 1024 * 1024)
        
        try {
            val modelInfo = QuantizationHelper.getModelInfo(tempFile)
            val description = QuantizationHelper.getMemoryDescription(modelInfo)
            
            assertTrue("Should contain file size", description.contains("File:"))
            assertTrue("Should contain RAM estimate", description.contains("Est. RAM:"))
            assertTrue("Should contain warning for Q8_0", description.contains("⚠️"))
        } finally {
            tempFile.delete()
        }
    }

    // ========== Memory Fit Tests ==========

    @Test
    fun `fitsInMemory accounts for 500MB buffer`() {
        val tempFile = createTempFileWithName("model-Q4_0.gguf", 4000L * 1024 * 1024) // ~4000MB
        
        try {
            val modelInfo = QuantizationHelper.getModelInfo(tempFile)
            
            // Model needs 4000MB + 500MB buffer = 4500MB
            assertFalse("Should not fit in 4499MB (needs 4500 with buffer)",
                QuantizationHelper.fitsInMemory(modelInfo, 4499))
            assertTrue("Should fit in 4500MB exactly",
                QuantizationHelper.fitsInMemory(modelInfo, 4500))
            assertTrue("Should fit in 5000MB",
                QuantizationHelper.fitsInMemory(modelInfo, 5000))
        } finally {
            tempFile.delete()
        }
    }

    // ========== Quantization Priority Tests ==========

    @Test
    fun `Q4_0 has highest priority`() {
        val priority = QuantizationHelper.getQuantizationPriority(QuantizationHelper.QuantizationType.Q4_0)
        assertEquals(1, priority)
    }

    @Test
    fun `unknown has lowest priority`() {
        val priority = QuantizationHelper.getQuantizationPriority(QuantizationHelper.QuantizationType.UNKNOWN)
        assertEquals(50, priority)
    }

    @Test
    fun `priorities are ordered correctly`() {
        // Lower number = higher priority
        val q4Priority = QuantizationHelper.getQuantizationPriority(QuantizationHelper.QuantizationType.Q4_0)
        val q5Priority = QuantizationHelper.getQuantizationPriority(QuantizationHelper.QuantizationType.Q5_0)
        val q8Priority = QuantizationHelper.getQuantizationPriority(QuantizationHelper.QuantizationType.Q8_0)
        val f32Priority = QuantizationHelper.getQuantizationPriority(QuantizationHelper.QuantizationType.F32)
        
        assertTrue("Q4 should be higher priority than Q5", q4Priority < q5Priority)
        assertTrue("Q5 should be higher priority than Q8", q5Priority < q8Priority)
        assertTrue("Q8 should be higher priority than F32", q8Priority < f32Priority)
    }

    // ========== ModelInfo Data Class Tests ==========

    @Test
    fun `modelInfo contains correct properties`() {
        val tempFile = createTempFileWithName("model-Q5_1.gguf", 1024L * 1024 * 1024)
        
        try {
            val modelInfo = QuantizationHelper.getModelInfo(tempFile)
            
            assertEquals(tempFile, modelInfo.file)
            assertEquals(QuantizationHelper.QuantizationType.Q5_1, modelInfo.quantization)
            assertTrue("Q5_1 should not have warning", !modelInfo.isWarning)
            assertTrue("Q5_1 should be recommended", modelInfo.quantization.isRecommended)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `warning flag set for high memory quantizations`() {
        val q8File = createTempFileWithName("model-Q8_0.gguf", 1024L * 1024 * 1024)
        val f16File = createTempFileWithName("model-F16.gguf", 1024L * 1024 * 1024)
        val q4File = createTempFileWithName("model-Q4_0.gguf", 1024L * 1024 * 1024)
        
        try {
            assertTrue("Q8_0 should have warning", 
                QuantizationHelper.getModelInfo(q8File).isWarning)
            assertTrue("F16 should have warning", 
                QuantizationHelper.getModelInfo(f16File).isWarning)
            assertFalse("Q4_0 should not have warning", 
                QuantizationHelper.getModelInfo(q4File).isWarning)
        } finally {
            q8File.delete()
            f16File.delete()
            q4File.delete()
        }
    }

    // ========== Helper Methods ==========

    private fun createTempFileOfSize(sizeBytes: Long): File {
        val tempFile = File.createTempFile("model-Q4_0-", ".gguf")
        tempFile.outputStream().use { fos ->
            if (sizeBytes > 0) {
                fos.channel.position(sizeBytes - 1)
                fos.write(0)
            }
        }
        return tempFile
    }

    private fun createTempFileWithName(name: String, sizeBytes: Long = 1024 * 1024): File {
        val quant = name.substringAfterLast("-").substringBefore(".gguf")
        val tempFile = File.createTempFile("model-$quant-", ".gguf")
        tempFile.outputStream().use { fos ->
            if (sizeBytes > 0) {
                fos.channel.position(sizeBytes - 1)
                fos.write(0)
            }
        }
        return tempFile
    }
}