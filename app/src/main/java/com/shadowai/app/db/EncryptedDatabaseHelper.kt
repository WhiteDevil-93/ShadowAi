package com.shadowai.app.db

import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.sqlcipher.database.SupportFactory

/**
 * Helper class to manage encrypted Room databases using SQLCipher.
 */
object EncryptedDatabaseHelper {

    /**
     * Creates a SupportSQLiteOpenHelper.Factory that handles
     * encryption using SQLCipher.
     * 
     * @param passphrase The passphrase to use for the database encryption.
     * @return The configured factory.
     */
    fun getFactory(passphrase: ByteArray): SupportSQLiteOpenHelper.Factory {
        // Ensure the native library is loaded
        System.loadLibrary("sqlcipher")
        return SupportFactory(passphrase)
    }
}
