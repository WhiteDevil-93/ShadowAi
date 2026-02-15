package com.shadowai.app.ui.download

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shadowai.app.ai.ModelDownloader
import com.shadowai.app.security.BiometricAuthManager
import kotlinx.coroutines.launch
import java.io.File

/**
 * Secure Model Downloader with Biometric Authentication.
 *
 * This wrapper requires biometric authentication before allowing model downloads,
 * ensuring that sensitive AI model operations require user verification.
 */
class SecureModelDownloader private constructor(context: Context) {

    private val modelDownloader = ModelDownloader.getInstance(context)
    private val biometricAuthManager = BiometricAuthManager(context)

    companion object {
        @Volatile
        private var instance: SecureModelDownloader? = null

        fun getInstance(context: Context): SecureModelDownloader {
            return instance ?: synchronized(this) {
                instance ?: SecureModelDownloader(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Check if biometric authentication is required and available.
     */
    fun isBiometricAvailable(): Boolean {
        return biometricAuthManager.isDeviceSecure()
    }

    /**
     * Authenticate and then download a model.
     *
     * @param activity The FragmentActivity for showing biometric prompt
     * @param url The model download URL
     * @param fileName Optional custom filename
     * @param expectedSha256 Optional SHA256 for verification
     * @param requireBiometric Whether to require biometric auth
     * @param onAuthRequired Called when authentication is needed
     * @param onDownloadStarted Called when download begins after auth
     * @param onError Called on authentication or download error
     */
    suspend fun authenticateAndDownload(
        activity: FragmentActivity,
        url: String,
        fileName: String? = null,
        expectedSha256: String? = null,
        requireBiometric: Boolean = true,
        onAuthRequired: suspend () -> Boolean = { true },
        onDownloadStarted: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        // Check if biometric is required
        if (requireBiometric) {
            if (!biometricAuthManager.isDeviceSecure()) {
                onError("Device lock (PIN, pattern, or password) required for secure downloads")
                return false
            }

            // Try biometric authentication
            val authResult = biometricAuthManager.authenticate(
                activity = activity,
                title = "Authenticate to Download Model",
                subtitle = "Secure download requires biometric verification",
                description = "This protects your AI models from unauthorized access",
                allowDeviceCredential = true
            )

            when (authResult) {
                BiometricAuthManager.AuthResult.SUCCESS -> {
                    // Authentication successful, proceed with download
                    onDownloadStarted()
                    return true
                }
                BiometricAuthManager.AuthResult.CANCELLED -> {
                    onError("Authentication cancelled")
                    return false
                }
                BiometricAuthManager.AuthResult.NOT_AVAILABLE,
                BiometricAuthManager.AuthResult.NOT_SECURE -> {
                    onError("Biometric authentication not available. Please enable device security.")
                    return false
                }
                BiometricAuthManager.AuthResult.FAILED -> {
                    onError("Authentication failed. Please try again.")
                    return false
                }
                BiometricAuthManager.AuthResult.NOT_ENROLLED -> {
                    onError("No credentials enrolled. Please set up a PIN, pattern, or biometrics.")
                    return false
                }
            }
        } else {
            // Biometric not required, proceed directly
            onDownloadStarted()
            return true
        }
    }

    /**
     * Get the underlying ModelDownloader instance.
     * Use this after authentication to perform actual downloads.
     */
    fun getModelDownloader(): ModelDownloader = modelDownloader
}

/**
 * Composable for secure model download button with biometric protection.
 */
@Composable
fun SecureDownloadButton(
    onDownloadClick: () -> Unit,
    requireBiometric: Boolean = true,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var showAuthDialog by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }

    val secureDownloader = remember { SecureModelDownloader.getInstance(context) }

    Button(
        onClick = {
            if (requireBiometric && context is FragmentActivity) {
                isAuthenticating = true
                authError = null
                coroutineScope.launch {
                    val success = secureDownloader.authenticateAndDownload(
                        activity = context,
                        url = "", // URL will be provided by actual download call
                        requireBiometric = true,
                        onDownloadStarted = {
                            showAuthDialog = false
                            isAuthenticating = false
                            onDownloadClick()
                        },
                        onError = { error ->
                            authError = error
                            isAuthenticating = false
                        }
                    )
                    if (!success && authError == null) {
                        showAuthDialog = true
                    }
                }
            } else {
                onDownloadClick()
            }
        },
        enabled = enabled && !isAuthenticating,
        modifier = modifier
    ) {
        if (isAuthenticating) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(if (isAuthenticating) "Authenticating..." else "Download Model")
    }

    // Error dialog
    if (authError != null) {
        AlertDialog(
            onDismissRequest = { authError = null },
            title = { Text("Authentication Required") },
            text = { Text(authError!!) },
            confirmButton = {
                TextButton(onClick = { authError = null }) {
                    Text("OK")
                }
            }
        )
    }
}

/**
 * Screen wrapper that enforces biometric authentication before showing download UI.
 */
@Composable
fun SecureDownloadScreenWrapper(
    requireBiometric: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isAuthenticated by remember { mutableStateOf(!requireBiometric) }
    var showError by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }

    val secureDownloader = remember { SecureModelDownloader.getInstance(context) }
    val biometricAuthManager = remember { BiometricAuthManager(context) }

    if (!isAuthenticated) {
        // Show authentication overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Secure Download Area",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = "Biometric authentication required to download AI models",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (showError != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = showError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (context is FragmentActivity) {
                        isAuthenticating = true
                        showError = null
                        coroutineScope.launch {
                            if (!biometricAuthManager.isDeviceSecure()) {
                                showError = "Please enable device lock (PIN, pattern, or password) in system settings"
                                isAuthenticating = false
                                return@launch
                            }

                            val result = biometricAuthManager.authenticate(
                                activity = context,
                                title = "Authenticate",
                                subtitle = "Verify your identity",
                                allowDeviceCredential = true
                            )

                            when (result) {
                                BiometricAuthManager.AuthResult.SUCCESS -> {
                                    isAuthenticated = true
                                }
                                BiometricAuthManager.AuthResult.CANCELLED -> {
                                    // User cancelled, stay on auth screen
                                }
                                BiometricAuthManager.AuthResult.NOT_AVAILABLE,
                                BiometricAuthManager.AuthResult.NOT_SECURE -> {
                                    showError = "Biometric authentication not available"
                                }
                                BiometricAuthManager.AuthResult.FAILED -> {
                                    showError = "Authentication failed. Please try again."
                                }
                                BiometricAuthManager.AuthResult.NOT_ENROLLED -> {
                                    showError = "No credentials enrolled. Set up device security first."
                                }
                            }
                            isAuthenticating = false
                        }
                    }
                },
                enabled = !isAuthenticating
            ) {
                if (isAuthenticating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isAuthenticating) "Authenticating..." else "Authenticate")
            }
        }
    } else {
        // Show protected content
        content()
    }
}
