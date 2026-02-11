@file:Suppress("DEPRECATION")

package com.shadowai.app.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Migration utility to transfer data from deprecated EncryptedSharedPreferences
 * to the new SecureDataStore backed by Google Tink.
 * 
 * This class is intentionally suppressing deprecation warnings as it needs
 * to read from the legacy EncryptedSharedPreferences during migration.
 */
@Singleton
class SecureStoreMigration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureDataStore: SecureDataStore
) {
    companion object {
        private const val TAG = "SecureStoreMigration"
        
        // Legacy preference file names
        private const val LEGACY_PROVIDERS_SECURE = "providers_secure"
        private const val LEGACY_PROVIDERS_SECURE_V2 = "providers_secure_v2"
        private const val LEGACY_SECURE_PREFS = "secure_prefs"
        private const val LEGACY_ADMIN_SECURE = "admin_secure"
        
        // Migration status keys (stored in regular prefs)
        private const val MIGRATION_PREFS = "secure_migration_status"
        private const val KEY_PROVIDERS_MIGRATED = "providers_migrated"
        private const val KEY_ADMIN_MIGRATED = "admin_migrated"
        private const val KEY_SECURITY_MIGRATED = "security_migrated"
    }

    private val migrationPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
    }

    /**
     * Migrate all legacy EncryptedSharedPreferences data to SecureDataStore.
     * Safe to call multiple times - will skip already migrated data.
     */
    suspend fun migrateAll() {
        withContext(Dispatchers.IO) {
            try {
                migrateProvidersSecure()
                migrateSecurityPrefs()
                migrateAdminSecure()
                Log.i(TAG, "Migration completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Migration failed", e)
                // Don't throw - app should continue even if migration fails
                // Data will remain in legacy storage and work with suppressed deprecation
            }
        }
    }

    /**
     * Migrate provider API keys from legacy EncryptedSharedPreferences.
     */
    private suspend fun migrateProvidersSecure() {
        if (migrationPrefs.getBoolean(KEY_PROVIDERS_MIGRATED, false)) {
            Log.d(TAG, "Providers already migrated, skipping")
            return
        }

        try {
            // Try migrating from both v1 and v2 legacy files
            val legacyFiles = listOf(LEGACY_PROVIDERS_SECURE, LEGACY_PROVIDERS_SECURE_V2)
            
            for (legacyFileName in legacyFiles) {
                val legacyPrefs = getLegacyPrefs(legacyFileName) ?: continue
                val allEntries = legacyPrefs.all
                
                if (allEntries.isEmpty()) continue
                
                Log.i(TAG, "Migrating ${allEntries.size} entries from $legacyFileName")
                
                for ((key, value) in allEntries) {
                    if (value is String && value.isNotBlank()) {
                        // Prefix keys to avoid collision with other migrations
                        secureDataStore.putString("provider_$key", value)
                    }
                }
                
                // Clear legacy data after successful migration
                legacyPrefs.edit().clear().commit()
            }
            
            migrationPrefs.edit().putBoolean(KEY_PROVIDERS_MIGRATED, true).apply()
            Log.i(TAG, "Provider secrets migrated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate provider secrets", e)
        }
    }

    /**
     * Migrate security manager data from legacy EncryptedSharedPreferences.
     */
    private suspend fun migrateSecurityPrefs() {
        if (migrationPrefs.getBoolean(KEY_SECURITY_MIGRATED, false)) {
            Log.d(TAG, "Security prefs already migrated, skipping")
            return
        }

        try {
            val legacyPrefs = getLegacyPrefs(LEGACY_SECURE_PREFS) ?: run {
                migrationPrefs.edit().putBoolean(KEY_SECURITY_MIGRATED, true).apply()
                return
            }
            
            val allEntries = legacyPrefs.all
            if (allEntries.isEmpty()) {
                migrationPrefs.edit().putBoolean(KEY_SECURITY_MIGRATED, true).apply()
                return
            }
            
            Log.i(TAG, "Migrating ${allEntries.size} security entries")
            
            for ((key, value) in allEntries) {
                if (value is String && value.isNotBlank()) {
                    secureDataStore.putString("security_$key", value)
                }
            }
            
            legacyPrefs.edit().clear().commit()
            migrationPrefs.edit().putBoolean(KEY_SECURITY_MIGRATED, true).apply()
            Log.i(TAG, "Security prefs migrated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate security prefs", e)
        }
    }

    /**
     * Migrate admin secure data from legacy EncryptedSharedPreferences.
     */
    private suspend fun migrateAdminSecure() {
        if (migrationPrefs.getBoolean(KEY_ADMIN_MIGRATED, false)) {
            Log.d(TAG, "Admin prefs already migrated, skipping")
            return
        }

        try {
            val legacyPrefs = getLegacyPrefs(LEGACY_ADMIN_SECURE) ?: run {
                migrationPrefs.edit().putBoolean(KEY_ADMIN_MIGRATED, true).apply()
                return
            }
            
            val allEntries = legacyPrefs.all
            if (allEntries.isEmpty()) {
                migrationPrefs.edit().putBoolean(KEY_ADMIN_MIGRATED, true).apply()
                return
            }
            
            Log.i(TAG, "Migrating ${allEntries.size} admin entries")
            
            for ((key, value) in allEntries) {
                if (value is String && value.isNotBlank()) {
                    secureDataStore.putString("admin_$key", value)
                }
            }
            
            legacyPrefs.edit().clear().commit()
            migrationPrefs.edit().putBoolean(KEY_ADMIN_MIGRATED, true).apply()
            Log.i(TAG, "Admin prefs migrated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate admin prefs", e)
        }
    }

    /**
     * Get legacy EncryptedSharedPreferences, or null if file doesn't exist
     * or can't be opened (e.g., key corruption).
     */
    private fun getLegacyPrefs(fileName: String): SharedPreferences? {
        return try {
            // Check if the file exists first
            val prefsFile = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            if (prefsFile.all.isEmpty()) {
                // Try encrypted version
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                
                EncryptedSharedPreferences.create(
                    context,
                    fileName,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } else {
                prefsFile
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not open legacy prefs: $fileName - ${e.message}")
            null
        }
    }

    /**
     * Check if migration has been completed for all components.
     */
    fun isMigrationComplete(): Boolean {
        return migrationPrefs.getBoolean(KEY_PROVIDERS_MIGRATED, false) &&
               migrationPrefs.getBoolean(KEY_ADMIN_MIGRATED, false) &&
               migrationPrefs.getBoolean(KEY_SECURITY_MIGRATED, false)
    }

    /**
     * Reset migration status (for testing or recovery purposes).
     */
    fun resetMigrationStatus() {
        migrationPrefs.edit()
            .remove(KEY_PROVIDERS_MIGRATED)
            .remove(KEY_ADMIN_MIGRATED)
            .remove(KEY_SECURITY_MIGRATED)
            .apply()
    }
}
