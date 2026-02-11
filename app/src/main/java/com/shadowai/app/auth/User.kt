package com.shadowai.app.auth

import android.net.Uri

/**
 * User profile data class representing an authenticated user.
 * 
 * MIGRATION COMPLETE: Added support for FirebaseUser creation
 * The fromGoogleAccount() method was removed as part of migration to AndroidX Credentials API.
 */
data class User(
    val id: String,
    val email: String,
    val displayName: String? = null,
    val photoUrl: Uri? = null,
    val isEmailVerified: Boolean = false,
    val providerId: String = "google.com",
    val lastSignInAt: Long? = null,
    val createdAt: Long? = null
)
