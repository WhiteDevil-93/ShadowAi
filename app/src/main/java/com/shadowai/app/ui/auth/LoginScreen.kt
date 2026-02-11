package com.shadowai.app.ui.auth

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.shadowai.app.R
import com.shadowai.app.auth.AuthState
import com.shadowai.app.auth.AuthViewModel
import com.shadowai.app.ui.theme.ShadowAITheme

/**
 * Login screen composable with Google Sign-In button.
 * 
 * MIGRATION COMPLETE: Updated to use AndroidX Credentials API
 * - Removed legacy Google Sign-In intent launcher
 * - Now uses CredentialManager for native sign-in flow
 * - Simplified authentication flow (no manual intent handling)
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val authState by viewModel.authState.collectAsState()

    // Handle authentication state changes
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                onLoginSuccess()
            }
            else -> {
                // Handle other states if needed
            }
        }
    }

    ShadowAITheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // App Logo/Icon
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "ShadowAI Logo",
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Welcome Text
                Text(
                    text = "Welcome to ShadowAI",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Sign in to access your personalized AI assistant",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(64.dp))

                // Google Sign-In Button using new Credentials API
                GoogleSignInButton(
                    onClick = {
                        if (activity != null) {
                            viewModel.signInWithGoogle(activity)
                        } else {
                            viewModel.setError("Sign-in requires an Activity context")
                        }
                    },
                    enabled = authState !is AuthState.Loading
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Loading/Error States
                when (authState) {
                    is AuthState.Loading -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Signing you in...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is AuthState.Error -> {
                        Text(
                            text = (authState as AuthState.Error).message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        
                        // Retry button on error
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { viewModel.refreshAuthState() },
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Text("Retry")
                        }
                    }
                    else -> {
                        // Show nothing for other states
                    }
                }
            }
        }
    }
}

/**
 * Custom Google Sign-In button composable.
 * 
 * Note: With the Credentials API, this button triggers the Credential Manager
 * UI instead of launching an external intent. The user experience is native
 * and consistent across Android versions.
 */
@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Google Logo placeholder - you can add a proper Google logo drawable
            Text(
                text = "G",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp)
            )

            Text(
                text = stringResource(R.string.button_continue_google),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
