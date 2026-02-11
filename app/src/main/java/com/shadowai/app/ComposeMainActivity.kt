package com.shadowai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.shadowai.app.auth.AuthState
import com.shadowai.app.auth.AuthViewModel
import com.shadowai.app.ui.ChatViewModel
import com.shadowai.app.ui.auth.LoginScreen
import com.shadowai.app.ui.navigation.Chat
import com.shadowai.app.ui.navigation.NavigationRoute
import com.shadowai.app.ui.navigation.ShadowAINavGraph
import com.shadowai.app.ui.navigation.rememberMutableStateListOf
import com.shadowai.app.ui.theme.ShadowAITheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Compose-based Main Activity - Implements UI Orchestrator governance
 *
 * MIGRATION: Updated for Navigation 3
 *
 * NAVIGATION 3 CHANGES:
 * - Uses rememberMutableStateListOf for back stack persistence
 * - No more NavHostController/rememberNavController
 * - Back stack is a SnapshotStateList<NavigationRoute>
 * - Handles process death and config changes automatically
 *
 * GOVERNANCE COMPLIANCE:
 * - WindowCompat.setDecorFitsSystemWindows(window, false) - MANDATORY
 * - Edge-to-edge layout
 * - Material 3 theme
 * - System insets handled by Compose modifiers
 * - Navigation graph for screen transitions
 *
 * This activity demonstrates the complete Compose migration with full
 * governance compliance from the start.
 *
 * @see ShadowAINavGraph for navigation structure
 * @see ShadowAITheme for Material 3 theme
 */
@AndroidEntryPoint
class ComposeMainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // GOVERNANCE: Mandatory edge-to-edge configuration
        // This is required by the UI Orchestrator System Prompt
        // Allows Compose to handle system insets properly
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            // Request permissions on start
            RequestAppPermissions()

            ShadowAITheme {
                // Authentication state
                val authState by authViewModel.authState.collectAsState()

                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (authState) {
                        is AuthState.Authenticated -> {
                            // User is authenticated, show main app
                            // MIGRATION: Navigation 3 uses remembered back stack
                            val backStack = rememberMutableStateListOf<NavigationRoute>(Chat)
                            val currentUser by authViewModel.currentUser.collectAsState()
                            ShadowAINavGraph(
                                backStack = backStack,
                                chatViewModel = chatViewModel,
                                currentUser = currentUser,
                                onSignOut = { authViewModel.signOut() }
                            )
                        }
                        is AuthState.Unauthenticated -> {
                            // User is not authenticated, show login screen
                            LoginScreen(
                                onLoginSuccess = {
                                    // Authentication state will automatically update
                                    // and trigger recomposition to show main app
                                }
                            )
                        }
                        else -> {
                            // Loading or error state - show login screen as fallback
                            LoginScreen(
                                onLoginSuccess = {
                                    // Authentication state will automatically update
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up ViewModel references and any ongoing operations
        // Note: ViewModels are lifecycle-aware and handle their own cleanup,
        // but we ensure no memory leaks from activity references
    }
}

@Composable
fun RequestAppPermissions() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results
        permissions.entries.forEach { (permission, isGranted) ->
            if (!isGranted) {
                android.util.Log.w("Permissions", "Permission denied: $permission")
            }
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()

        // Check if permissions are already granted before requesting
        val recordAudioPermission = android.Manifest.permission.RECORD_AUDIO
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, recordAudioPermission
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(recordAudioPermission)
        }

        // Android 13+ Notification Permission
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            listOf(
                android.Manifest.permission.POST_NOTIFICATIONS,
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_AUDIO
            ).forEach { permission ->
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context, permission
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    permissionsToRequest.add(permission)
                }
            }
        } else {
            // Below Android 13
            val readStoragePermission = android.Manifest.permission.READ_EXTERNAL_STORAGE
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context, readStoragePermission
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(readStoragePermission)
            }
        }

        // Only launch permission request if there are permissions to request
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
