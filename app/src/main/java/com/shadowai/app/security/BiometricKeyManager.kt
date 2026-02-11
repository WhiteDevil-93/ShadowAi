@file:Suppress("DEPRECATION")

package com.shadowai.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Manages keys that require User Authentication (Biometric/PIN).
 */
class BiometricKeyManager(private val context: Context) {
     private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

     /**
      * Generate a key that requires user authentication to use.
      */
     fun generateBiometricKey(alias: String): SecretKey {
         val keyGenerator = KeyGenerator.getInstance(
             KeyProperties.KEY_ALGORITHM_AES, 
             "AndroidKeyStore"
         )
         
         val builder = KeyGenParameterSpec.Builder(
             alias, 
             KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
         )
             .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
             .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
             .setUserAuthenticationRequired(true)
             .setUserAuthenticationValidityDurationSeconds(-1) // Require auth for every use
         
         keyGenerator.init(builder.build())
         return keyGenerator.generateKey()
     }
     
     fun getKey(alias: String): SecretKey? {
         return keyStore.getKey(alias, null) as? SecretKey
     }
}
