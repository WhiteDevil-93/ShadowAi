package com.shadowai.app.ai

import android.util.Log
import com.shadowai.app.ai.MemoryConstants.estimateModelRam
import java.io.File

/**
 * Helper class for working with GGUF model quantization levels.
 *
 * Detects quantization from model filenames and calculates memory requirements.
 */
object QuantizationHelper {

    private const val TAG = "QuantizationHelper"

    /**
     * Quantization types with their relative memory multipliers.
     *
     * Q4_0 is the baseline (1.0x), Q8_0 uses 2x more memory.
     * These are approximate multipliers relative to Q4_0.
     */
    enum class QuantizationType(
        val displayName: String,
        val relativeMultiplier: Double,
        val isRecommended: Boolean,
        val warning: String? = null
    ) {
        Q2_K("Q2_K", 0.5, false, "Very low quality, high speed"),
        Q3_K("Q3_K", 0.6, true),
        Q3_K_S("Q3_K_S", 0.6, true),
        Q3_K_M("Q3_K_M", 0.65, true),
        Q3_K_L("Q3_K_L", 0.7, true),
        Q4_0("Q4_0", 1.0, true, "Recommended - balanced quality/speed"),
        Q4_1("Q4_1", 1.05, true),
        Q4_K("Q4_K", 1.1, true),
        Q4_K_S("Q4_K_S", 0.95, true),
        Q4_K_M("Q4_K_M", 1.0, true),
        Q5_0("Q5_0", 1.2, true, "Good quality, reasonable speed"),
        Q5_1("Q5_1", 1.25, true, "Recommended - high quality"),
        Q5_K("Q5_K", 1.3, true),
        Q5_K_S("Q5_K_S", 1.15, true),
        Q5_K_M("Q5_K_M", 1.25, true),
        Q6_K("Q6_K", 1.5, true, "High quality, slower"),
        Q8_0("Q8_0", 2.0, false, "⚠️ High memory usage, not recommended"),
        Q8_K("Q8_K", 2.1, false, "⚠️ High memory usage, not recommended"),
        F16("F16", 4.0, false, "⚠️ Very high memory usage"),
        F32("F32", 6.0, false, "⚠️ Extremely high memory usage"),
        UNKNOWN("Unknown", 1.0, true)
    }

    /**
     * Model metadata including quantization info.
     */
    data class ModelInfo(
        val file: File,
        val quantization: QuantizationType,
        val fileSize: Long,
        val estimatedRamMB: Long,
        val isWarning: Boolean
    )

    /**
     * Regex patterns to match various quantization naming conventions.
     */
    private val QUANTIZATION_PATTERNS = listOf(
        Regex("""Q2_K[_SML]?"""),
        Regex("""Q3_K[_SML]?"""),
        Regex("""Q4[_\-\.]0"""),
        Regex("""Q4[_\-\.]1"""),
        Regex("""Q4_K[_SM]?"""),
        Regex("""Q5[_\-\.]0"""),
        Regex("""Q5[_\-\.]1"""),
        Regex("""Q5_K[_SM]?"""),
        Regex("""Q6_K"""),
        Regex("""Q8[_\-\.]0"""),
        Regex("""Q8_K"""),
        Regex("""F16"""),
        Regex("""F32""")
    )

    /**
     * Detect quantization type from model filename.
     *
     * @param filename Model filename (e.g., "llama-3-8b-chat-Q5_K_M.gguf")
     * @return Quantization type
     */
    fun detectQuantization(filename: String): QuantizationType {
        val upperName = filename.uppercase()

        for (pattern in QUANTIZATION_PATTERNS) {
            val match = pattern.find(upperName)
            if (match != null) {
                val quantStr = match.value
                return when {
                    // Q2 variants
                    quantStr.contains("Q2_K") -> QuantizationType.Q2_K

                    // Q3 variants
                    quantStr.contains("Q3_K_S") -> QuantizationType.Q3_K_S
                    quantStr.contains("Q3_K_M") -> QuantizationType.Q3_K_M
                    quantStr.contains("Q3_K_L") -> QuantizationType.Q3_K_L
                    quantStr.contains("Q3_K") -> QuantizationType.Q3_K

                    // Q4 variants
                    quantStr.contains("Q4_K_S") -> QuantizationType.Q4_K_S
                    quantStr.contains("Q4_K_M") -> QuantizationType.Q4_K_M
                    quantStr.contains("Q4_K") -> QuantizationType.Q4_K
                    quantStr.contains("Q4.1") || quantStr.contains("Q4_1") -> QuantizationType.Q4_1
                    quantStr.contains("Q4.0") || quantStr.contains("Q4_0") -> QuantizationType.Q4_0

                    // Q5 variants
                    quantStr.contains("Q5_K_S") -> QuantizationType.Q5_K_S
                    quantStr.contains("Q5_K_M") -> QuantizationType.Q5_K_M
                    quantStr.contains("Q5_K") -> QuantizationType.Q5_K
                    quantStr.contains("Q5.1") || quantStr.contains("Q5_1") -> QuantizationType.Q5_1
                    quantStr.contains("Q5.0") || quantStr.contains("Q5_0") -> QuantizationType.Q5_0

                    // Q6 variants
                    quantStr.contains("Q6_K") -> QuantizationType.Q6_K

                    // Q8 variants
                    quantStr.contains("Q8_K") -> QuantizationType.Q8_K
                    quantStr.contains("Q8.0") || quantStr.contains("Q8_0") -> QuantizationType.Q8_0

                    // Floating point
                    quantStr.contains("F16") -> QuantizationType.F16
                    quantStr.contains("F32") -> QuantizationType.F32

                    else -> QuantizationType.UNKNOWN
                }
            }
        }

        Log.d(TAG, "Could not detect quantization from filename: $filename")
        return QuantizationType.UNKNOWN
    }

    /**
     * Try to estimate base model parameter count from filename.
     * Returns estimated parameters in billions (e.g., 7.0 for 7B model).
     */
    fun estimateParameterCount(filename: String): Double {
        // Look for patterns like "7b", "8b", "13b", "70b"
        val paramPattern = Regex("""(\d+(?:\.\d+)?)\s*b\W""", RegexOption.IGNORE_CASE)
        val match = paramPattern.find(filename.lowercase())
        if (match != null) {
            return match.groupValues[1].toDoubleOrNull() ?: 7.0
        }
        return 7.0 // Default assumption
    }

    /**
     * Calculate memory requirements for a model file.
     *
     * @param file Model file
     * @return ModelInfo with quantization and memory estimates
     */
    fun getModelInfo(file: File): ModelInfo {
        val fileSize = file.length()
        val quantization = detectQuantization(file.name)

        // NOTE: Don't use estimateModelRam() which already multiplies by MODEL_MEMORY_MULTIPLIER (2.0x)
        // Calculate directly from file size with quantization multiplier
        // The quantization relativeMultiplier already accounts for runtime overhead requirements

        // Base estimate from file size (in MB)
        val fileSizeMB = fileSize / (1024 * 1024)

        // Apply quantization multiplier which represents the actual RAM requirement relative to file size
        // Q4_0 (1.0x): 4GB file → ~6GB RAM, Q8_0 (2.0x): 4GB file → ~9GB RAM
        val estimatedRamMB = (fileSizeMB * quantization.relativeMultiplier).toLong()

        val isWarning = quantization.warning != null && quantization.warning.contains("⚠️")

        return ModelInfo(
            file = file,
            quantization = quantization,
            fileSize = fileSize,
            estimatedRamMB = estimatedRamMB,
            isWarning = isWarning
        )
    }

    /**
     * Get prioritized list of models, with recommended quantizations first.
     *
     * Recommended quantizations: Q3_K*, Q4_0, Q4_1, Q4_K*, Q5_0, Q5_1, Q5_K*, Q6_K
     *
     * @param files List of model files
     * @return List of ModelInfo sorted by priority
     */
    fun prioritizeModels(files: List<File>): List<ModelInfo> {
        return files
            .map { getModelInfo(it) }
            .sortedWith(compareBy<ModelInfo> { !it.quantization.isRecommended }
                .thenBy { it.estimatedRamMB }
                .thenByDescending { it.file.lastModified() })
    }

    /**
     * Check if a model can fit in available memory.
     *
     * @param modelInfo Model information
     * @param availableRamMB Available RAM in MB
     * @return true if model fits, false otherwise
     */
    fun fitsInMemory(modelInfo: ModelInfo, availableRamMB: Long): Boolean {
        // Leave at least 500MB buffer
        return (modelInfo.estimatedRamMB + 500) <= availableRamMB
    }

    /**
     * Get user-friendly memory description.
     */
    fun formatMemorySize(bytes: Long): String {
        val mb = bytes / (1024 * 1024)
        val gb = mb / 1024.0
        return if (gb >= 1.0) {
            String.format("%.1f GB", gb)
        } else {
            String.format("%d MB", mb)
        }
    }

    /**
     * Get memory description with quantization context.
     */
    fun getMemoryDescription(modelInfo: ModelInfo): String {
        val sizeStr = formatMemorySize(modelInfo.fileSize)
        val ramStr = formatMemorySize(modelInfo.estimatedRamMB * 1024 * 1024)

        return buildString {
            append("File: $sizeStr | Est. RAM: $ramStr")
            if (modelInfo.quantization.warning != null) {
                append(" | ${modelInfo.quantization.warning}")
            }
            if (modelInfo.quantization.isRecommended) {
                append(" ✓")
            }
        }
    }

    /**
     * Get priority ordering for quantization types.
     * Lower number = higher priority.
     */
    fun getQuantizationPriority(quantization: QuantizationType): Int {
        // Priority: Q4_0 < Q5_1 < Q4_K < Q5_K < Q5_0 < Q6_K < Q3 < Q2 < Q8 < F16 < F32
        return when (quantization) {
            QuantizationType.Q4_0 -> 1
            QuantizationType.Q5_1 -> 2
            QuantizationType.Q4_K_M -> 3
            QuantizationType.Q4_K_S -> 4
            QuantizationType.Q4_K -> 5
            QuantizationType.Q5_K_M -> 6
            QuantizationType.Q5_0 -> 7
            QuantizationType.Q4_1 -> 8
            QuantizationType.Q5_K_S -> 9
            QuantizationType.Q5_K -> 10
            QuantizationType.Q6_K -> 11
            QuantizationType.Q3_K_M -> 12
            QuantizationType.Q3_K_L -> 13
            QuantizationType.Q3_K_S -> 14
            QuantizationType.Q3_K -> 15
            QuantizationType.Q2_K -> 16
            QuantizationType.Q8_0 -> 17
            QuantizationType.Q8_K -> 18
            QuantizationType.F16 -> 19
            QuantizationType.F32 -> 20
            QuantizationType.UNKNOWN -> 50
        }
    }
}