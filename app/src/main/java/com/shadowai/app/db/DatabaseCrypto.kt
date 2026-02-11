package com.shadowai.app.db

import com.shadowai.app.security.SecurityManager
import javax.inject.Inject

/**
 * Interface for database field-level encryption.
 */
interface DatabaseCrypto {
    fun encrypt(data: String): String
    fun decrypt(data: String): String
}

/**
 * Implementation of DatabaseCrypto using SecurityManager and Hardware-backed keys.
 */
class StandardDatabaseCrypto @Inject constructor(
    private val securityManager: SecurityManager
) : DatabaseCrypto {
    private val keyAlias = "db_field_encryption_key"
    // Use lazy initialization for the key to ensure we don't block on main thread during init if possible
    private val key by lazy { securityManager.generateHardwareBackedKey(keyAlias) }

    override fun encrypt(data: String): String {
        return securityManager.encrypt(data, key)
    }

    override fun decrypt(data: String): String {
        return securityManager.decrypt(data, key)
    }
}
