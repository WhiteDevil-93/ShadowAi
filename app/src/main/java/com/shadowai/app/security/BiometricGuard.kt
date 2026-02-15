package com.shadowai.app.security

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen

/**
 * Composable wrapper for requiring biometric authentication before accessing sensitive content.
 *
 * Shows an authentication prompt when a user tries to access the protected content.
 * Once authenticated, displays the actual content.
 *
 * @param title Title shown on authentication dialog
 * @param subtitle Optional subtitle
 * @param description Optional description
 * @param requireBiometric Whether biometric auth is enabled (from settings)
 * @param content The protected content to show after auth
 */
@Composable
fun BiometricGuard(
    title: String = "Authentication Required",
    subtitle: String? = null,
    description: String? = null,
    requireBiometric: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAuthenticated by remember { mutableStateOf(!requireBiometric) }
    var showError by remember { mutableStateOf<String?>(null) }
    var authInProgress by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val biometricAuthManager = remember { BiometricAuthManager(context) }

    LaunchedEffect(requireBiometric) {
        if (!requireBiometric) {
            isAuthenticated = true
        }
    }

    if (!isAuthenticated) {
        // Show locked overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    enabled = !authInProgress,
                    onClickLabel = if (authInProgress) "Authenticating..." else "Authenticate to unlock"
                ) {
                    if (!authInProgress && requireBiometric) {
                        authInProgress = true
                        showError = null

                        // Check if device is secure before prompting
                        if (!biometricAuthManager.isDeviceSecure()) {
                            showError = "Enable device lock (PIN, pattern, or password) to access this feature"
                            authInProgress = false
                            return@clickable
                        }

                        if (context is FragmentActivity) {
                            coroutineScope.launch {
                                try {
                                    val result = biometricAuthManager.authenticate(
                                        activity = context,
                                        title = title,
                                        subtitle = subtitle,
                                        description = description,
                                        allowDeviceCredential = true
                                    )

                                    when (result) {
                                        BiometricAuthManager.AuthResult.SUCCESS -> {
                                            isAuthenticated = true
                                            authInProgress = false
                                        }
                                        BiometricAuthManager.AuthResult.CANCELLED -> {
                                            authInProgress = false
                                        }
                                        BiometricAuthManager.AuthResult.NOT_AVAILABLE,
                                        BiometricAuthManager.AuthResult.NOT_SECURE -> {
                                            showError = "Authentication not available. Enable device lock to use this feature."
                                            authInProgress = false
                                        }
                                        BiometricAuthManager.AuthResult.FAILED -> {
                                            showError = "Authentication failed. Please try again."
                                            authInProgress = false
                                        }
                                        BiometricAuthManager.AuthResult.NOT_ENROLLED -> {
                                            showError = "No credentials enrolled. Please set up a PIN, pattern, or biometrics."
                                            authInProgress = false
                                        }
                                    }
                                } catch (e: Exception) {
                                    showError = "Authentication error: ${e.message}"
                                    authInProgress = false
                                }
                            }
                        } else {
                            showError = "Authentication not available in this context"
                            authInProgress = false
                        }
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (authInProgress) {
                    Icons.Default.Lock
                } else {
                    Icons.Default.LockOpen
                },
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (authInProgress) "Authenticating..." else "Tap to authenticate",
                style = MaterialTheme.typography.headlineSmall
            )

            if (showError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = showError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    } else {
        // Show protected content
        content()
    }
}
