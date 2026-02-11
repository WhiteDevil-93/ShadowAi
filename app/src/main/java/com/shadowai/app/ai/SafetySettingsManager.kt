package com.shadowai.app.ai

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.shadowai.app.util.SystemPropertyCompat
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configurable safety settings for AI providers.
 *
 * SECURITY NOTE: This class controls content filtering for AI responses.
 * Disabling safety filters may result in harmful, explicit, or dangerous content.
 *
 * Default behavior:
 * - All builds: Safety filters ENABLED at BLOCK_MEDIUM_AND_ABOVE level
 * - Users must explicitly acknowledge risks to use BLOCK_NONE.
 *
 * CRITICAL FIXES APPLIED:
 * 1. Context Memory Leak - Using applicationContext
 * 2. Rooted Device Check - More robust detection
 * 3. SharedPreferences Migration - Versioned preferences
 * 4. Custom Checked Exception - SafetySettingsException
 */
@Singleton
class SafetySettingsManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "SafetySettings"
        private const val PREFS_NAME = "ai_safety_settings"
        private const val KEY_SAFETY_LEVEL = "safety_level"
        private const val KEY_USER_ACKNOWLEDGED = "user_acknowledged_risks"
        private const val KEY_PREFS_VERSION = "prefs_version"
        private const val CURRENT_PREFS_VERSION = 1
        
        // CRITICAL FIX: Build markers for root detection
        private const val TAG_ROOT_BUILD = "test-keys"
        private const val TAG_ROOT_CERT = "/system/etc/security/cacerts"

        /**
         * Safety levels for content filtering.
         *
         * BLOCK_NONE: No filtering (development/adult use only)
         * BLOCK_ONLY_HIGH: Block only high-confidence harmful content
         * BLOCK_MEDIUM_AND_ABOVE: Block medium and high probability content (recommended)
         * BLOCK_LOW_AND_ABOVE: Strictest filtering, may block legitimate content
         */
        enum class SafetyLevel(val apiValue: String) {
            BLOCK_NONE("BLOCK_NONE"),
            BLOCK_ONLY_HIGH("BLOCK_ONLY_HIGH"),
            BLOCK_MEDIUM_AND_ABOVE("BLOCK_MEDIUM_AND_ABOVE"),
            BLOCK_LOW_AND_ABOVE("BLOCK_LOW_AND_ABOVE")
        }

        /**
         * Gemini harm categories
         */
        enum class HarmCategory(val apiValue: String) {
            HARASSMENT("HARM_CATEGORY_HARASSMENT"),
            HATE_SPEECH("HARM_CATEGORY_HATE_SPEECH"),
            SEXUALLY_EXPLICIT("HARM_CATEGORY_SEXUALLY_EXPLICIT"),
            DANGEROUS_CONTENT("HARM_CATEGORY_DANGEROUS_CONTENT"),
            CIVIC_INTEGRITY("HARM_CATEGORY_CIVIC_INTEGRITY")
        }
    }

    // CRITICAL FIX: Use applicationContext to prevent memory leaks
    private val safeContext: Context by lazy {
        context.applicationContext
    }

    private val prefs: SharedPreferences by lazy {
        safeContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * CRITICAL FIX: Custom checked exception for safety settings
     */
    class SafetySettingsException(message: String, cause: Throwable? = null) 
        : Exception(message, cause)

    /**
     * Get the current safety level.
     */
    fun getSafetyLevel(): SafetyLevel {
        // CRITICAL FIX: Run migration if needed
        migratePreferencesIfNeeded()
        
        val savedLevel = prefs.getString(KEY_SAFETY_LEVEL, null)
        return if (savedLevel != null) {
            try {
                SafetyLevel.valueOf(savedLevel)
            } catch (e: IllegalArgumentException) {
                getDefaultSafetyLevel()
            }
        } else {
            getDefaultSafetyLevel()
        }
    }

    /**
     * Set the safety level.
     *
     * @param level The new safety level
     * @param userAcknowledged Whether the user has acknowledged the risks of disabling safety
     * @throws SafetySettingsException if trying to disable safety without acknowledgment
     */
    @Throws(SafetySettingsException::class)
    fun setSafetyLevel(level: SafetyLevel, userAcknowledged: Boolean = false) {
        if (level == SafetyLevel.BLOCK_NONE && !userAcknowledged) {
            // CRITICAL FIX: Custom checked exception instead of unchecked SecurityException
            throw SafetySettingsException(
                "Disabling safety filters requires explicit user acknowledgment. " +
                "Call setSafetyLevel(BLOCK_NONE, userAcknowledged = true) to confirm."
            )
        }

        prefs.edit()
            .putString(KEY_SAFETY_LEVEL, level.name)
            .putBoolean(KEY_USER_ACKNOWLEDGED, userAcknowledged && level == SafetyLevel.BLOCK_NONE)
            .apply()

        Log.i(TAG, "Safety level set to: $level (acknowledged: $userAcknowledged)")
    }

    /**
     * Check if user has acknowledged risks of disabled safety.
     */
    fun hasUserAcknowledgedRisks(): Boolean {
        return prefs.getBoolean(KEY_USER_ACKNOWLEDGED, false)
    }

    /**
     * Get default safety level based on build type.
     */
    private fun getDefaultSafetyLevel(): SafetyLevel {
        // Enforce safe defaults for all builds
        Log.d(TAG, "Using BLOCK_MEDIUM_AND_ABOVE default")
        return SafetyLevel.BLOCK_MEDIUM_AND_ABOVE
    }

    /**
     * CRITICAL FIX: Rooted Device Detection - More robust check
     * Checks multiple indicators of device compromise
     */
    fun isDevicePotentiallyCompromised(): Boolean {
        // Check build tags (common root indicator)
        val buildTags = android.os.Build.TAGS
        if (buildTags != null && buildTags.contains(TAG_ROOT_BUILD)) {
            Log.w(TAG, "Device has test-keys build tag")
            return true
        }

        // Check for common root indicators in system properties
        val secure = SystemPropertyCompat.get("ro.secure", "")
        if (secure == "0") {
            Log.w(TAG, "Device has ro.secure=0 (root indicator)")
            return true
        }

        // Check for Magisk or similar root manager
        val magisk = SystemPropertyCompat.get("ro.boot.vbmeta.size", "")
        if (magisk.isNotBlank()) {
            // This is not definitive, just an indicator
            Log.d(TAG, "Non-standard vbmeta detected")
        }

        // Check for installed root apps (common paths)
        val rootAppPaths = listOf(
            "/data/app/SuperSU*",
            "/data/app/com.topjohnwu.magisk*",
            "/data/app/me.phh.superuser*"
        )

        try {
            val dataDir = File("/data/app")
            if (dataDir.exists() && dataDir.isDirectory) {
                val apps = dataDir.list() ?: emptyArray()
                for (app in apps) {
                    for (pattern in rootAppPaths) {
                        if (app.contains("SuperSU") || 
                            app.contains("magisk") || 
                            app.contains("superuser")) {
                            Log.w(TAG, "Potential root app detected: $app")
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not check for root apps: ${e.message}")
        }

        // Check if system partition is mounted as read-write (root indicator)
        try {
            val systemFile = File("/system/build.prop")
            if (systemFile.canWrite()) {
                Log.w(TAG, "System partition is writable (root indicator)")
                return true
            }
        } catch (e: Exception) {
            // Expected to fail on non-rooted devices
        }

        return false
    }

    /**
     * CRITICAL FIX: SharedPreferences Migration
     * Handles versioned migrations for preference changes
     */
    private fun migratePreferencesIfNeeded() {
        val currentVersion = prefs.getInt(KEY_PREFS_VERSION, 0)
        
        if (currentVersion < CURRENT_PREFS_VERSION) {
            Log.i(TAG, "Migrating preferences from version $currentVersion to $CURRENT_PREFS_VERSION")
            
            // Perform migration based on version
            when (currentVersion) {
                0 -> migrateFromV0ToV1()
            }
            
            // Mark migration complete
            prefs.edit().putInt(KEY_PREFS_VERSION, CURRENT_PREFS_VERSION).apply()
            Log.i(TAG, "Preferences migration complete")
        }
    }

    /**
     * Migration from version 0 to 1:
     * - Added version tracking
     * - Ensured default safety level is enforced
     */
    private fun migrateFromV0ToV1() {
        // If no safety level is set, ensure default is applied
        val savedLevel = prefs.getString(KEY_SAFETY_LEVEL, null)
        if (savedLevel == null) {
            // Apply default - this was the implicit behavior before
            prefs.edit()
                .putString(KEY_SAFETY_LEVEL, SafetyLevel.BLOCK_MEDIUM_AND_ABOVE.name)
                .apply()
            Log.d(TAG, "Migrated to v1: Applied default safety level")
        }
    }

    /**
     * Build Gemini safety settings list based on current configuration.
     */
    fun buildGeminiSafetySettings(): List<GeminiSafetySetting> {
        val level = getSafetyLevel()
        return HarmCategory.values().map { category ->
            GeminiSafetySetting(
                category = category.apiValue,
                threshold = level.apiValue
            )
        }
    }

    /**
     * Data classfor Gemini API safety setting.
     */
    data class GeminiSafetySetting(
        val category: String,
        val threshold: String
    )

    /**
     * Reset to default safety level.
     */
    fun resetToDefaults() {
        prefs.edit()
            .remove(KEY_SAFETY_LEVEL)
            .remove(KEY_USER_ACKNOWLEDGED)
            .apply()
        Log.i(TAG, "Safety settings reset to defaults")
    }

    /**
     * Get human-readable description of current safety level.
     */
    fun getSafetyLevelDescription(): String {
        return when (getSafetyLevel()) {
            SafetyLevel.BLOCK_NONE -> 
                "Safety filters DISABLED. AI may generate harmful, explicit, or dangerous content."
            SafetyLevel.BLOCK_ONLY_HIGH -> 
                "Minimal filtering. Only blocks high-confidence harmful content."
            SafetyLevel.BLOCK_MEDIUM_AND_ABOVE -> 
                "Recommended. Blocks medium and high probability harmful content."
            SafetyLevel.BLOCK_LOW_AND_ABOVE -> 
                "Strict filtering. May block some legitimate content."
        }
    }

    /**
     * Check if safety settings need user attention (e.g., device potentially rooted)
     */
    fun requiresUserAttention(): Boolean {
        return isDevicePotentiallyCompromised() || 
               (getSafetyLevel() == SafetyLevel.BLOCK_NONE && !hasUserAcknowledgedRisks())
    }
}
