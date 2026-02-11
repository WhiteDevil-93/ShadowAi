package com.shadowai.app.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.shadowai.app.BuildConfig

/**
 * Zero-Trust Data Integrity: ShadowDatabase.
 * 
 * Enables Write-Ahead Logging (WAL) for concurrent reads/writes.
 * Implements TTL-based data retention for security and performance.
 */
@Database(
    entities = [
        ChatMessageEntity::class, 
        MemoryEntity::class, 
        LedgerEntry::class, 
        TaskEntity::class, 
        AgentFailureEntity::class, 
        FeedbackEntity::class,
        ModelPathEntity::class
    ], 
    version = 9, 
    exportSchema = false
)
abstract class ShadowDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun taskDao(): TaskDao
    abstract fun failureDao(): FailureDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun modelPathDao(): ModelPathDao

    companion object {
        @Volatile
        private var INSTANCE: ShadowDatabase? = null
        
        // TTL configurations (in milliseconds)
        const val DEFAULT_TTL_MS = 48 * 60 * 60 * 1000L // 48 hours
        const val MEMORY_TTL_MS = 7 * 24 * 60 * 60 * 1000L // 7 days for memories
        const val LEDGER_TTL_MS = 48 * 60 * 60 * 1000L // 48 hours for ledger
        const val FAILURE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours for failures
        
        fun getDatabase(context: Context): ShadowDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    ShadowDatabase::class.java,
                    "shadow_ai_db"
                ).setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)

                // Integrate SQLCipher for database encryption if available
                try {
                    // Use MasterKey to generate/retrieve a secure passphrase
                    val masterKey = androidx.security.crypto.MasterKey.Builder(context.applicationContext)
                        .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                        .build()

                    // We use the MasterKey to wrap a random key, but for SQLCipher simplistic integration 
                    // we can derive a stable byte array from the MasterKey's alias if supported,
                    // OR better: use an EncryptedFile to store a random 32-byte key.
                    // For this immediate step, we will use a deterministic derivation for stability 
                    // without wiping existing data if possible, BUT the user warned about data wipe.
                    // Let's implement the standard specialized helper pattern if it exists,
                    // otherwise assume we generate a fresh key and store it in EncryptedSharedPreferences.
                    
                    val securePrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                        context.applicationContext,
                        "db_params",
                        masterKey,
                        androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                    
                    var keyB64 = securePrefs.getString("db_key", null)
                    if (keyB64 == null) {
                        val newKey = ByteArray(32)
                        java.security.SecureRandom().nextBytes(newKey)
                        keyB64 = android.util.Base64.encodeToString(newKey, android.util.Base64.NO_WRAP)
                        securePrefs.edit().putString("db_key", keyB64).apply()
                        java.util.Arrays.fill(newKey, 0.toByte())
                    }
                    
                    val passphrase = android.util.Base64.decode(keyB64, android.util.Base64.NO_WRAP)
                    // SQLCipher copies the passphrase internally, so we can safely zero it after passing
                    builder.openHelperFactory(EncryptedDatabaseHelper.getFactory(passphrase))
                    // Zero the passphrase after SQLCipher has copied it
                    java.util.Arrays.fill(passphrase, 0.toByte())
                    Log.i("ShadowDatabase", "SQLCipher encryption integrated with MasterKey")
                } catch (e: Exception) {
                    Log.e("ShadowDatabase", "Failed to integrate SQLCipher: ${e.message}")
                }
                
                // Do not allow destructive migrations - requires explicit migration specifications
                // Removing fallbackToDestructiveMigration to ensure data preservation in all builds
                // All schema changes must use proper Room migrations
                builder.addMigrations(*ShadowMigrations.getAllMigrations())
                
                try {
                    val instance = builder.build()
                    INSTANCE = instance
                    instance
                } catch (e: Exception) {
                    Log.e("ShadowDatabase", "CRITICAL: Database initialization failed", e)
                    throw e
                }
            }
        }
        
        /**
         * Get database with custom TTL settings.
         */
        fun getDatabaseWithTtl(
            context: Context,
            memoryTtlMs: Long = MEMORY_TTL_MS,
            ledgerTtlMs: Long = LEDGER_TTL_MS,
            failureTtlMs: Long = FAILURE_TTL_MS
        ): ShadowDatabase {
            // Store TTL values in SharedPreferences for the pruner worker
            val prefs = context.getSharedPreferences("db_ttl_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("memory_ttl", memoryTtlMs)
                .putLong("ledger_ttl", ledgerTtlMs)
                .putLong("failure_ttl", failureTtlMs)
                .apply()
            
            return getDatabase(context)
        }
    }
}
