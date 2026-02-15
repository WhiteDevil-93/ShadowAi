package com.shadowai.app.ai

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects and reports device AI hardware capabilities for optimization.
 *
 * This class detects:
 * - NNAPI (Neural Networks API) support for NPU acceleration
 * - Device manufacturer-specific NPU (Pixel, Samsung, etc.)
 * - Hardware acceleration capabilities
 */
@Singleton
class DeviceCapabilities @Inject constructor() {

    companion object {
        private const val TAG = "DeviceCapabilities"

        // Known NPU-capable device brands
        private val NPU_BRANDS = setOf(
            "google",    // Pixel devices with Google Tensor/TPU
            "samsung",   // Galaxy devices with Exynos NPU
            "xiaomi",    // Some devices with Snapdragon NPU
            "oppo",      // Some devices with dedicated NPU
            "vivo",      // Some devices with dedicated NPU
            "huawei",    // Kirin NPU
            "oneplus",   // Some OnePlus devices with NPU
            "realme",    // Some devices with NPU
            "motorola"   // Some Edge devices with NPU
        )

        // Models known to have poor NNAPI support or bugs
        private val NNAPI_BLACKLIST = setOf<String>(
            // Add specific device models that should avoid NNAPI
            // Example: "Pixel 4" older than a certain build
        )
    }

    /**
     * Device capability information.
     */
    data class DeviceInfo(
        val hasNpu: Boolean,
        val nnapiAvailable: Boolean,
        val nnapiVersion: Int,
        val brand: String,
        val model: String,
        val apiLevel: Int,
        val recommendedBackend: BackendType,
        val canUseNnapi: Boolean
    )

    /**
     * Backend type for inference.
     */
    enum class BackendType {
        CPU,
        NNAPI,
        GPU,
        ACCELERATOR
    }

    private var cachedInfo: DeviceInfo? = null

    /**
     * Get device capability information.
     * Results are cached for performance.
     */
    fun getDeviceInfo(): DeviceInfo {
        cachedInfo?.let { return it }

        val brand = lowercase(Build.MANUFACTURER)
        val model = Build.MODEL
        val apiLevel = Build.VERSION.SDK_INT

        val hasNpuBrand = NPU_BRANDS.contains(brand)
        val nnapiAvailable = apiLevel >= Build.VERSION_CODES.P  // NNAPI introduced in Android 9 (API 28)
        val nnapiVersion = if (nnapiAvailable) apiLevel else -1

        // Check if NNAPI should actually be used
        val canUseNnapi = nnapiAvailable && hasNpuBrand && !NNAPI_BLACKLIST.contains(model)

        // Determine recommended backend
        val recommendedBackend = when {
            canUseNnapi -> BackendType.NNAPI
            else -> BackendType.CPU
        }

        val info = DeviceInfo(
            hasNpu = hasNpuBrand,
            nnapiAvailable = nnapiAvailable,
            nnapiVersion = nnapiVersion,
            brand = brand,
            model = model,
            apiLevel = apiLevel,
            recommendedBackend = recommendedBackend,
            canUseNnapi = canUseNnapi
        )

        cachedInfo = info

        Log.i(TAG, "Device capabilities: $info")
        if (canUseNnapi) {
            Log.i(TAG, "NNAPI acceleration available! Expected 2-3x speed boost.")
        } else if (nnapiAvailable) {
            Log.i(TAG, "NNAPI is available but not recommended for this device")
        } else {
            Log.i(TAG, "NNAPI not available (API $apiLevel, requires API ${Build.VERSION_CODES.P})")
        }

        return info
    }

    /**
     * Check if this device can benefit from NNAPI acceleration.
     */
    fun supportsNnapi(): Boolean {
        return getDeviceInfo().canUseNnapi
    }

    /**
     * Check if NNAPI is available (may not be recommended).
     */
    fun isNnapiAvailable(): Boolean {
        return getDeviceInfo().nnapiAvailable
    }

    /**
     * Get the recommended backend type for this device.
     */
    fun getRecommendedBackend(): BackendType {
        return getDeviceInfo().recommendedBackend
    }

    /**
     * Get a human-readable description of device capabilities.
     */
    fun getCapabilityDescription(): String {
        val info = getDeviceInfo()
        return buildString {
            append("Device: ${info.brand} ${info.model}\n")
            append("Android API: ${info.apiLevel}\n")

            if (info.nnapiAvailable) {
                append("NNAPI: Available (API ${info.nnapiVersion})\n")
                if (info.canUseNnapi) {
                    append("Status: ✓ Recommended for this device\n")
                    append("Expected: 2-3x inference speed boost")
                } else {
                    append("Status: Not recommended for this device\n")
                    if (!info.hasNpu) {
                        append("Reason: No known NPU hardware")
                    }
                }
            } else {
                append("NNAPI: Not available (requires Android 9+)\n")
                append("Status: Will use CPU backend")
            }
        }
    }

    private fun lowercase(s: String?): String {
        return s?.lowercase() ?: ""
    }
}
