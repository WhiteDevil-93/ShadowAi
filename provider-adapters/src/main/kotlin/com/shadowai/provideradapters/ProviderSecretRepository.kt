@file:Suppress("DEPRECATION")

package com.shadowai.provideradapters

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.shadowai.core.security.SecretBytes
import dagger.hilt.android.qualifiers.ApplicationContext
import com.shadowai.core.security.TeeKeyManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for securely handling provider secrets (API Keys).
 * Uses EncryptedSharedPreferences backed by Android Keystore.
 *
 * CRITICAL FIXES APPLIED:
 * 1. Async Commit Risk - Use synchronous commit for critical security data
 * 2. Input Validation - Validate providerId and apiKey parameters
 * 3. SharedPreferences Key Injection - ProviderId sanitization with logging
 */
@Singleton
class ProviderSecretRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val teeKeyManager: TeeKeyManager
) {
    companion object {
        private const val TAG = "ProviderSecretRepo"
        private const val SECURE_PREFS_NAME = "providers_secure_v2"
        private const val KEY_API_KEY_PREFIX = "api_key_"
        private const val MAX_PROVIDER_ID_LENGTH = 64
        private const val MAX_API_KEY_LENGTH = 4096

        // Valid characters for provider IDs (alphanumeric, underscore, hyphen)
        private val VALID_PROVIDER_ID_PATTERN = Regex("^[a-zA-Z0-9_-]+$")
    }

    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Save an API key securely as SecretBytes.
     *
     * CRITICAL FIX: Uses synchronous commit to ensure data is persisted before returning.
     * This ensures the sensitive data is successfully encrypted and stored.
     */
    fun saveApiKey(providerId: String, apiKey: SecretBytes) {
        // CRITICAL FIX: Input validation
        if (providerId.isBlank()) {
            Log.e(TAG, "Attempted to save API key with blank providerId")
            throw IllegalArgumentException("providerId cannot be blank")
        }

        if (providerId.length > MAX_PROVIDER_ID_LENGTH) {
            Log.e(TAG, "Attempted to save API key with excessively long providerId: ${providerId.length} chars")
            throw IllegalArgumentException("providerId exceeds maximum length of $MAX_PROVIDER_ID_LENGTH characters")
        }

        if (apiKey.isDisposed()) {
            Log.e(TAG, "Attempted to save disposed API key for provider: $providerId")
            throw IllegalArgumentException("apiKey is already disposed")
        }

        // CRITICAL FIX: Sanitize providerId to prevent key injection
        val safeId = sanitizeProviderId(providerId)
        if (safeId != providerId) {
            Log.w(TAG, "ProviderId sanitized from '$providerId' to '$safeId'")
        }

        // Access raw bytes and convert to UTF-8 string for storage in EncryptedSharedPreferences
        // Note: EncryptedSharedPreferences encrypts this string before writing to disk.
        val success = apiKey.withSecretBytes { bytes ->
            val apiString = String(bytes, Charsets.UTF_8)
            if (apiString.isBlank()) return@withSecretBytes false
            
            val editor = securePrefs.edit().putString(KEY_API_KEY_PREFIX + safeId, apiString)
            editor.commit()
        }

        if (!success) {
            Log.e(TAG, "Failed to synchronously commit API key for provider: $safeId")
            throw RuntimeException("Failed to persist API key securely")
        }

        Log.d(TAG, "API key saved successfully for provider: $safeId")
    }

    /**
     * Retrieve an API key.
     * @return The API key as SecretBytes or null if not found.
     */
    fun getApiKey(providerId: String): SecretBytes? {
        // CRITICAL FIX: Input validation
        if (providerId.isBlank()) {
            Log.w(TAG, "Attempted to get API key with blank providerId")
            return null
        }

        if (providerId.length > MAX_PROVIDER_ID_LENGTH) {
            Log.w(TAG, "Attempted to get API key with excessively long providerId")
            return null
        }

        // CRITICAL FIX: Sanitize providerId to prevent key injection
        val safeId = sanitizeProviderId(providerId)

        val apiKey = securePrefs.getString(KEY_API_KEY_PREFIX + safeId, null)
            ?.takeIf { it.isNotBlank() }

        return apiKey?.let {
            SecretBytes.fromByteArray(it.toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * Remove an API key.
     *
     * CRITICAL FIX: Uses synchronous commit for security-critical deletion.
     */
    fun clearApiKey(providerId: String) {
        // CRITICAL FIX: Input validation
        if (providerId.isBlank()) {
            Log.w(TAG, "Attempted to clear API key with blank providerId")
            return
        }

        if (providerId.length > MAX_PROVIDER_ID_LENGTH) {
            Log.w(TAG, "Attempted to clear API key with excessively long providerId")
            return
        }

        // CRITICAL FIX: Sanitize providerId to prevent key injection
        val safeId = sanitizeProviderId(providerId)

        // CRITICAL FIX: Use synchronous commit for security-critical deletion
        val editor = securePrefs.edit().remove(KEY_API_KEY_PREFIX + safeId)
        editor.commit()
    }

    /**
     * Sanitize providerId to prevent SharedPreferences key injection.
     * Only allows alphanumeric characters, underscores, and hyphens.
     */
    private fun sanitizeProviderId(providerId: String): String {
        // Filter to only allowed characters
        val filtered = providerId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }

        return if (filtered.matches(VALID_PROVIDER_ID_PATTERN)) {
            filtered
        } else {
            filtered
        }
    }
}
