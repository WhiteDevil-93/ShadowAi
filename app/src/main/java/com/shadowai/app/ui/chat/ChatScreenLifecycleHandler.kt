package com.shadowai.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Composable that handles voice permissions and lifecycle events for the chat screen.
 * Requests RECORD_AUDIO permission when needed and provides graceful degradation.
 */
@Composable
fun VoicePermissionHandler(
    voiceChatViewModel: VoiceChatViewModel,
    snackbarHostState: SnackbarHostState,
    onPermissionGranted: () -> Unit = {}
) {
    val context = LocalContext.current
    val voiceUiState by voiceChatViewModel.uiState.collectAsState()

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted()
            voiceChatViewModel.markPermissionRequested()
        } else {
            // Permission denied - show message
            // The ViewModel will have already set an error message
        }
    }

    // Handle voice permission requests from ViewModel
    LaunchedEffect(voiceUiState.errorMessage) {
        if (voiceUiState.errorMessage?.contains("Microphone permission required", ignoreCase = true) == true) {
            // Check if we should request permission
            val permission = Manifest.permission.RECORD_AUDIO
            val shouldShowRationale = voiceChatViewModel.shouldShowPermissionRationale()

            when {
                ContextCompat.checkSelfPermission(context, permission) ==
                        PackageManager.PERMISSION_GRANTED -> {
                    // Permission granted but somehow missed - retry
                    onPermissionGranted()
                }
                shouldShowRationale -> {
                    // First time - show rationale and then request
                    snackbarHostState.showSnackbar(
                        "Voice input requires microphone permission"
                    )
                    permissionLauncher.launch(permission)
                    voiceChatViewModel.markPermissionRequested()
                }
                else -> {
                    // Request permission
                    permissionLauncher.launch(permission)
                }
            }
        }
    }
}
