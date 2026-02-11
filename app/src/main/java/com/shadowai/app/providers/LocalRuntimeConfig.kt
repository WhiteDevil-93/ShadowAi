package com.shadowai.app.providers

import com.shadowai.app.ai.MemoryConstants

/**
 * Simple runtime configuration for local providers (e.g., Liquid).
 * Used by execution paths to calibrate memory requirements.
 */
data class LocalRuntimeConfig(
    val modelSizeGb: Double = 1.2,
    val minFreeRamBytes: Long = MemoryConstants.LIQUID_MIN_FREE_RAM_BYTES
)
