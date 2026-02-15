package com.shadowai.app.util

import android.util.Log

/** M-5: Remove reflection discovery - safe system property accessor
 *  Completely removed Class.forName() reflection usage which triggers
 *  security warnings on Android 9+ and violates modern app guidelines.
 *
 *  Now uses System.getenv() for environment variables as a safe alternative
 *  for specific known properties, or returns default values for hidden
 *  Android system properties that are no longer accessible.
 *
 *  For legitimate system properties, prefer Build.* constants or
 *  Context.getSystemService() instead of reflection.
 */
object SystemPropertyCompat {
    private const val TAG = "SystemPropertyCompat"

    /**
     * M-5: Removed reflection-based access. Now safely returns environment
     * variables for known keys, or default value for everything else.
     *
     * Previously this used Class.forName("android.os.SystemProperties") which
     * is a hidden API and violates Android's non-SDK interface restrictions.
     */
    fun get(key: String, defaultValue: String = ""): String {
        return when {
            // Known environment variables that can be safely accessed
            key.startsWith("shadowai.") -> System.getenv(key) ?: defaultValue
            key in ALLOWED_ENV_KEYS -> System.getenv(key) ?: defaultValue
            // For all other properties (including hidden Android system properties),
            // return default value to avoid reflection-based access
            else -> {
                Log.d(TAG, "System property '$key' not accessible without reflection (returning default)")
                defaultValue
            }
        }
    }

    /**
     * Get an Android build property using official Build class.
     * This is the safe alternative to reflection-based SystemProperties access.
     */
    fun getBuildProperty(property: BuildProperty): String {
        return when (property) {
            BuildProperty.DEVICE -> android.os.Build.DEVICE
            BuildProperty.MANUFACTURER -> android.os.Build.MANUFACTURER
            BuildProperty.MODEL -> android.os.Build.MODEL
            BuildProperty.VERSION_RELEASE -> android.os.Build.VERSION.RELEASE
            BuildProperty.VERSION_SDK -> android.os.Build.VERSION.SDK_INT.toString()
            BuildProperty.BOARD -> android.os.Build.BOARD
            BuildProperty.BOOTLOADER -> android.os.Build.BOOTLOADER
            BuildProperty.BRAND -> android.os.Build.BRAND
            BuildProperty.HARDWARE -> android.os.Build.HARDWARE
            BuildProperty.PRODUCT -> android.os.Build.PRODUCT
        }
    }

    private val ALLOWED_ENV_KEYS = setOf(
        "PATH",
        "USER",
        "HOME",
        "TMPDIR",
        "ANDROID_DATA",
        "ANDROID_ROOT"
    )

    enum class BuildProperty {
        DEVICE,
        MANUFACTURER,
        MODEL,
        VERSION_RELEASE,
        VERSION_SDK,
        BOARD,
        BOOTLOADER,
        BRAND,
        HARDWARE,
        PRODUCT
    }
}
