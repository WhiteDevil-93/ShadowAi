package com.shadowai.app.auth

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing authentication state and operations.
 * 
 * MIGRATION COMPLETE: Migrated from deprecated Google Sign-In to AndroidX Credentials API
 * - Uses CredentialManager for Google authentication
 * - Provides cleaner, more secure authentication flow
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    val userRepository: UserRepository,
    val googleAuthManager: GoogleAuthManager
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        checkAuthState()
    }

    /**
     * Checks the current authentication state
     */
    private fun checkAuthState() {
        viewModelScope.launch {
            try {
                val isLoggedIn = userRepository.isUserLoggedIn()
                val user = userRepository.getCurrentUser()

                if (isLoggedIn && user != null) {
                    _authState.value = AuthState.Authenticated
                    _currentUser.value = user
                } else {
                    _authState.value = AuthState.Unauthenticated
                    _currentUser.value = null
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Signs out the current user
     */
    fun signOut() {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                val result = userRepository.signOut()
                result.fold(
                    onSuccess = {
                        _authState.value = AuthState.Unauthenticated
                        _currentUser.value = null
                    },
                    onFailure = { error ->
                        _authState.value = AuthState.Error("Sign-out failed: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Sign-out error: ${e.message}")
            }
        }
    }

    /**
     * Initiates Google Sign-In using the AndroidX Credentials API.
     * 
     * This method launches the Credential Manager UI which provides a
     * native, consistent sign-in experience across Android versions.
     * 
     * @param activity The calling activity (required for Credential Manager)
     */
    fun signInWithGoogle(activity: Activity) {
        Log.d("AuthViewModel", "signInWithGoogle: initiating Credentials API sign-in")
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            val result = googleAuthManager.signInWithGoogle(activity)
            result.fold(
                onSuccess = { firebaseUser ->
                    Log.d("AuthViewModel", "signInWithGoogle: success for user: ${firebaseUser.email}")
                    
                    // Create or update local user record
                    val user = User(
                        id = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        displayName = firebaseUser.displayName,
                        photoUrl = firebaseUser.photoUrl
                    )
                    
                    // Save to repository
                    val saveResult = userRepository.saveUser(user)
                    saveResult.fold(
                        onSuccess = {
                            _authState.value = AuthState.Authenticated
                            _currentUser.value = user
                        },
                        onFailure = { error ->
                            Log.e("AuthViewModel", "Failed to save user", error)
                            _authState.value = AuthState.Error("Failed to save user: ${error.message}")
                        }
                    )
                },
                onFailure = { error ->
                    Log.e("AuthViewModel", "signInWithGoogle: failed", error)
                    _authState.value = AuthState.Error("Google Sign-In failed: ${error.message}")
                }
            )
        }
    }

    /**
     * Refreshes the current authentication state from repository.
     * Use this to re-check auth after app resumes or network changes.
     */
    fun refreshAuthState() {
        checkAuthState()
    }

    /**
     * Sets an error state
     */
    fun setError(message: String) {
        _authState.value = AuthState.Error(message)
    }

    /**
     * Updates user profile information
     */
    fun updateUserProfile(updatedUser: User) {
        viewModelScope.launch {
            try {
                val result = userRepository.updateUserProfile(updatedUser)
                result.fold(
                    onSuccess = { user ->
                        _currentUser.value = user
                    },
                    onFailure = { error ->
                        _authState.value = AuthState.Error("Failed to update profile: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Update error: ${e.message}")
            }
        }
    }

}

/**
 * Represents the authentication state
 */
sealed class AuthState {
    object Loading : AuthState()
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}
