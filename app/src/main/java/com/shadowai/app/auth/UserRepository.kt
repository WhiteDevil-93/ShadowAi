package com.shadowai.app.auth

import android.content.Context
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing user authentication and profile data.
 * 
 * MIGRATION COMPLETE: Updated to work with AndroidX Credentials API
 * - Removed dependency on deprecated GoogleSignInAccount
 * - Added saveUser(user: User) method for Credentials API
 * - Maintains backwards compatibility during transition
 */
@Singleton
class UserRepository @Inject constructor(
    private val userPreferences: UserPreferences,
    private val googleAuthManager: GoogleAuthManager
) {
    /**
     * Flow that emits the current user
     */
    val currentUser: Flow<User?> = userPreferences.currentUser

    /**
     * Flow that emits authentication state
     */
    val isLoggedIn: Flow<Boolean> = userPreferences.isLoggedIn

    /**
     * Saves a user to local preferences.
     * 
     * This is the preferred method with the new Credentials API.
     * The User object should be created from the FirebaseUser returned
     * by the sign-in flow.
     * 
     * @param user The user to save
     * @return Result indicating success or failure
     */
    suspend fun saveUser(user: User): Result<User> {
        return try {
            userPreferences.saveUser(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates or updates a User from FirebaseUser data.
     * Helper method for the new Credentials API flow.
     * 
     * @param firebaseUser The FirebaseUser from authentication
     * @return Result containing the created User
     */
    suspend fun createUserFromFirebase(firebaseUser: FirebaseUser): Result<User> {
        return try {
            val user = User(
                id = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                displayName = firebaseUser.displayName,
                photoUrl = firebaseUser.photoUrl,
                isEmailVerified = firebaseUser.isEmailVerified,
                providerId = firebaseUser.providerData.firstOrNull()?.providerId ?: "google.com",
                lastSignInAt = System.currentTimeMillis()
            )
            userPreferences.saveUser(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs out the current user
     */
    suspend fun signOut(): Result<Unit> {
        return try {
            // Sign out from Google (Credential Manager doesn't need explicit sign-out,
            // but this will sign out from Firebase)
            googleAuthManager.signOut()
            // Clear local user data
            userPreferences.clearUser()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gets the current user as a suspend function.
     * Prefer this over any blocking variant.
     */
    suspend fun getCurrentUser(): User? = userPreferences.getCurrentUserSuspend()

    /**
     * Checks if user is logged in as a suspend function.
     * Prefer this over any blocking variant.
     */
    suspend fun isUserLoggedIn(): Boolean = userPreferences.isLoggedInSuspend()

    /**
     * Refreshes user data from Firebase.
     * New method for the Credentials API.
     * 
     * @return Result containing the updated User
     */
    suspend fun refreshUserFromFirebase(): Result<User> {
        return try {
            val firebaseUser = googleAuthManager.getCurrentUser()
                ?: return Result.failure(Exception("No Firebase user found"))
            createUserFromFirebase(firebaseUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates user profile information
     */
    suspend fun updateUserProfile(updatedUser: User): Result<User> {
        return try {
            userPreferences.saveUser(updatedUser)
            Result.success(updatedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
