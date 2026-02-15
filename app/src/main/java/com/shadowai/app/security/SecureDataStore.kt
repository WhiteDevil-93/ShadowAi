package com.shadowai.app.security

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure DataStore implementation using Google Tink for encryption.
 */
@Singleton
class SecureDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private const val KEYSET_NAME = "tink_keyset"
        private const val PREFERENCE_FILE = "__tink_keyset_prefs__"
        private const val MASTER_KEY_URI = "android-keystore://shadowai_master_key"

        init {
            AeadConfig.register()
        }
    }

    /**
     * Exposes the underlying DataStore for advanced operations like key listing.
     * Note: Values returned by this DataStore are encrypted.
     */
    val data: DataStore<Preferences> get() = dataStore

    private val aead: Aead by lazy {
        val keysetManager = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, PREFERENCE_FILE)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()

        keysetManager.keysetHandle.getPrimitive(Aead::class.java)
    }

    suspend fun putString(key: String, value: String) {
        val prefKey = stringPreferencesKey(key)
        val encrypted = encrypt(value, key)
        dataStore.edit { preferences ->
            preferences[prefKey] = encrypted
        }
    }

    suspend fun getString(key: String): String? {
        val prefKey = stringPreferencesKey(key)
        return dataStore.data.map { preferences ->
            preferences[prefKey]?.let { encrypted ->
                try {
                    decrypt(encrypted, key)
                } catch (e: Exception) {
                    Log.w("SecureDataStore", "Failed to decrypt value for key: $key", e)
                    null
                }
            }
        }.first()
    }

    suspend fun remove(key: String) {
        val prefKey = stringPreferencesKey(key)
        dataStore.edit { preferences ->
            preferences.remove(prefKey)
        }
    }

    suspend fun contains(key: String): Boolean {
        val prefKey = stringPreferencesKey(key)
        return dataStore.data.map { preferences ->
            preferences.contains(prefKey)
        }.first()
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    fun observeString(key: String): Flow<String?> {
        val prefKey = stringPreferencesKey(key)
        return dataStore.data.map { preferences ->
            preferences[prefKey]?.let { encrypted ->
                try {
                    decrypt(encrypted, key)
                } catch (e: Exception) {
                    Log.w("SecureDataStore", "Failed to decrypt observed value for key: $key", e)
                    null
                }
            }
        }
    }

    private fun encrypt(plaintext: String, associatedKey: String = ""): String {
        val aad = associatedKey.toByteArray(Charsets.UTF_8)
        val ciphertext = aead.encrypt(plaintext.toByteArray(Charsets.UTF_8), aad)
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(ciphertext: String, associatedKey: String = ""): String {
        val decoded = Base64.decode(ciphertext, Base64.NO_WRAP)
        val aad = associatedKey.toByteArray(Charsets.UTF_8)
        val plaintext = aead.decrypt(decoded, aad)
        return String(plaintext, Charsets.UTF_8)
    }
}
