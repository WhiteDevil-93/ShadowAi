package com.shadowai.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shadowai.app.security.BiometricAuthManager
import kotlinx.coroutines.launch

/**
 * Lockscreen overlay shown when auto-lock timeout is reached.
 *
 * The overlay features a blurred background to prevent sensitive content
 * from being visible while locked.
 */
@Composable
fun LockScreenOverlay(
    onUnlockSuccess: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var authInProgress by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val biometricAuthManager = remember { BiometricAuthManager(context) }

    // Authentication function defined first to be accessible in LaunchedEffect and callbacks
    fun authenticate() {
        if (context !is androidx.fragment.app.FragmentActivity) {
            errorMessage = "Authentication not available"
            return
        }

        if (!biometricAuthManager.isDeviceSecure()) {
            errorMessage = "Enable device lock to use this feature"
            return
        }

        authInProgress = true
        errorMessage = null

        scope.launch {
            try {
                val result = biometricAuthManager.authenticate(
                    activity = context,
                    title = "Unlock ShadowAi",
                    subtitle = "Auto-lock timeout exceeded",
                    description = "Please authenticate to continue",
                    allowDeviceCredential = true
                )

                when (result) {
                    BiometricAuthManager.AuthResult.SUCCESS -> {
                        authInProgress = false
                        onUnlockSuccess()
                    }
                    BiometricAuthManager.AuthResult.CANCELLED -> {
                        authInProgress = false
                        errorMessage = null
                    }
                    BiometricAuthManager.AuthResult.NOT_AVAILABLE,
                    BiometricAuthManager.AuthResult.NOT_SECURE -> {
                        authInProgress = false
                        errorMessage = "Authentication not available"
                    }
                    BiometricAuthManager.AuthResult.FAILED -> {
                        authInProgress = false
                        errorMessage = "Authentication failed"
                    }
                    BiometricAuthManager.AuthResult.NOT_ENROLLED -> {
                        authInProgress = false
                        errorMessage = "No credentials enrolled"
                    }
                }
            } catch (e: Exception) {
                authInProgress = false
                errorMessage = "Authentication error: ${e.message}"
            }
        }
    }

    // Auto-trigger authentication on screen load
    LaunchedEffect(Unit) {
        if (context is androidx.fragment.app.FragmentActivity && !authInProgress) {
            authenticate()
        }
    }

    Dialog(
        onDismissRequest = { /* Cannot dismiss without auth */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        // Full-screen overlay with gradient background
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                // Lock icon with glow effect
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = RoundedCornerShape(60.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 8.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Title and subtitle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "App Locked",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Authenticate to continue using ShadowAi",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Authentication status
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = when {
                        errorMessage != null -> MaterialTheme.colorScheme.errorContainer
                        authInProgress -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when {
                            errorMessage != null -> {
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = { authenticate() },
                                    enabled = !authInProgress
                                ) {
                                    Text("Try Again")
                                }
                            }
                            authInProgress -> {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Authenticating...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            else -> {
                                Text(
                                    text = "Authentication required",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = { authenticate() },
                                    enabled = !authInProgress
                                ) {
                                    Text("Unlock")
                                }
                            }
                        }
                    }
                }

                // Device info
                Text(
                    text = when (biometricAuthManager.getAuthType()) {
                        BiometricAuthManager.AuthType.BIOMETRIC -> "Use fingerprint or face recognition"
                        BiometricAuthManager.AuthType.DEVICE_CREDENTIAL -> "Use your device PIN, pattern, or password"
                        BiometricAuthManager.AuthType.NONE -> "Set up device lock to use this feature"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
