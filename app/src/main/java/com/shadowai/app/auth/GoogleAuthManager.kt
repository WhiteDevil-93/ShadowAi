package com.shadowai.app.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPasswordOption
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Google authentication using the AndroidX Credentials API.
 * 
 * MIGRATED FROM: Legacy Google Sign-In (play-services-auth)
 * TO: AndroidX Credentials API with Google ID Credential
 * 
 * The Credential Manager provides a unified sign-in API that supports:
 * - Passkeys (FIDO2)
 * - Google Sign-In
 * - Password-based sign-in
 * 
 * Benefits over legacy Google Sign-In:
 * - No Play Services dependency issues
 * - Consistent UX across Android versions
 * - Future-proof (supports passkeys)
 * - Single API for all credential types
 */
@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val firebaseAuth = FirebaseAuth.getInstance()
    
    /**
     * CredentialManager instance for handling all credential operations.
     * This replaces the legacy GoogleSignInClient.
     */
    private val credentialManager = CredentialManager.create(context)

    /**
     * Initiates Google Sign-In using the Credentials API.
     * 
     * This method creates a credential request with Google ID option and
     * launches the Credential Manager UI for user selection.
     * 
     * @param activity The calling activity for the credential request
     * @return Result containing the FirebaseUser on success, or exception on failure
     */
    suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser> {
        return try {
            // Generate a nonce for security (prevents replay attacks)
            val nonce = generateNonce()
            
            // Create the Google ID option
            val googleIdOption = GetGoogleIdOption.Builder()
                // Request the server's client ID (from google-services.json)
                .setServerClientId(context.getString(com.shadowai.app.R.string.default_web_client_id))
                // Enable automatic selection of the verified Google account
                .setAutoSelectEnabled(true)
                // Set the nonce for this request
                .setNonce(nonce)
                // Don't filter by authorized accounts (allow user to pick any Google account)
                .setFilterByAuthorizedAccounts(false)
                .build()

            // Create the credential request
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            // Request credentials from the Credential Manager
            val result = credentialManager.getCredential(
                request = request,
                context = activity
            )

            // Process the credential response
            handleSignIn(result)
        } catch (e: GetCredentialException) {
            Result.failure(Exception("Failed to get credentials: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Handles the credential response from Credential Manager.
     * 
     * @param result The GetCredentialResponse from Credential Manager
     * @return Result containing the FirebaseUser on success
     */
    private suspend fun handleSignIn(result: GetCredentialResponse): Result<FirebaseUser> {
        val credential = result.credential

        return when {
            // Check if it's a Google ID token credential
            credential is CustomCredential && 
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                try {
                    // Parse the Google ID token credential
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    
                    // Authenticate with Firebase using the Google ID token
                    val idToken = googleIdTokenCredential.idToken
                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                    val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
                    
                    authResult.user?.let { user ->
                        Result.success(user)
                    } ?: Result.failure(Exception("Firebase authentication returned null user"))
                } catch (e: GoogleIdTokenParsingException) {
                    Result.failure(Exception("Failed to parse Google ID token: ${e.message}"))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            // Handle other credential types if needed (password, passkey)
            credential is PublicKeyCredential -> {
                Result.failure(Exception("Passkey authentication not yet implemented"))
            }
            else -> {
                Result.failure(Exception("Unsupported credential type: ${credential::class.java.simpleName}"))
            }
        }
    }

    /**
     * Signs out from both Firebase and clears credential state.
     * 
     * Note: Since the Credentials API doesn't maintain a persistent session
     * like the legacy Google Sign-In, we only need to sign out from Firebase.
     * The Credential Manager state can be cleared for completeness.
     */
    suspend fun signOut(): Result<Unit> {
        return try {
            // Sign out from Firebase
            firebaseAuth.signOut()
            
            // Clear the credential state (optional, for completeness)
            try {
                credentialManager.clearCredentialState(
                    ClearCredentialStateRequest()
                )
            } catch (e: ClearCredentialException) {
                // Non-fatal: clearing credential state is optional
                e.printStackTrace()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs out from Firebase only.
     */
    fun signOutFirebase() {
        firebaseAuth.signOut()
    }

    /**
     * Gets the current Firebase user.
     */
    fun getCurrentUser(): FirebaseUser? = firebaseAuth.currentUser

    /**
     * Generates a cryptographically secure nonce for the credential request.
     * This helps prevent replay attacks.
     */
    private fun generateNonce(): String {
        val rawNonce = UUID.randomUUID().toString()
        val bytes = rawNonce.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.fold(StringBuilder()) { sb, it ->
            sb.append(String.format("%02x", it))
        }.toString()
    }

    /**
     * Retrieves the Google ID token from a previous authentication.
     * This is useful for backend verification.
     * 
     * @return The Google ID token if available, null otherwise
     */
    suspend fun getGoogleIdToken(activity: Activity): Result<String> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(context.getString(com.shadowai.app.R.string.default_web_client_id))
                .setAutoSelectEnabled(true)
                .setFilterByAuthorizedAccounts(true) // Only previously authorized accounts
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activity
            )

            val credential = result.credential
            if (credential is CustomCredential && 
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                Result.success(googleIdTokenCredential.idToken)
            } else {
                Result.failure(Exception("No Google ID token credential available"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        /**
         * Legacy constant kept for backwards compatibility with UI code.
         * Modern apps don't need this as the Credential Manager handles
         * the result internally.
         */
        @Deprecated("Not needed with Credentials API - kept for backwards compatibility")
        const val RC_SIGN_IN = 9001
    }
}
