package com.shadowai.app

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.shadowai.app.auth.AuthState
import com.shadowai.app.auth.AuthViewModel
import com.shadowai.app.security.AutoLockManager
import com.shadowai.app.security.PreferencesManager
import com.shadowai.app.ui.ChatViewModel
import com.shadowai.app.ui.LockScreenOverlay
import com.shadowai.app.ui.auth.LoginScreen
import com.shadowai.app.ui.navigation.Chat
import com.shadowai.app.ui.navigation.NavigationRoute
import com.shadowai.app.ui.navigation.ShadowAINavGraph
import com.shadowai.app.ui.navigation.rememberMutableStateListOf
import com.shadowai.app.ui.theme.ShadowAITheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest

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

    @javax.inject.Inject
    lateinit var preferencesManager: PreferencesManager
    @javax.inject.Inject
    lateinit var autoLockManager: AutoLockManager

    // Shared content from other apps (Intent.ACTION_SEND)
    private var pendingSharedText: String? = null
    private var pendingSharedImages: List<Uri> = emptyList()

    // Track if voice input should be launched
    private var launchVoiceInput by mutableStateOf(false)
    private val hotwordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == ShadowApplication.ACTION_HOTWORD_DETECTED) {
                launchVoiceInput = true
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Hotword broadcast received; scheduling voice input launch")
                }
            }
        }
    }

    companion object {
        private const val TAG = "ComposeMainActivity"
        const val ACTION_LAUNCH_VOICE_INPUT = "com.shadowai.app.action.LAUNCH_VOICE_INPUT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Process incoming share intent immediately
        handleShareIntent(intent)

        // Handle voice input action
        if (intent?.action == ACTION_LAUNCH_VOICE_INPUT) {
            launchVoiceInput = true
        }

        // GOVERNANCE: Mandatory edge-to-edge configuration
        // This is required by the UI Orchestrator System Prompt
        // Allows Compose to handle system insets properly
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Observe screenshot protection setting
        lifecycleScope.launch {
            preferencesManager.preventScreenshots.collectLatest { prevent ->
                applyScreenshotProtection(prevent)
            }
        }

        // Apply auto-lock settings and monitor app inactivity.
        lifecycleScope.launch {
            combine(
                preferencesManager.autoLockEnabled,
                preferencesManager.autoLockTimeout
            ) { enabled, timeout -> enabled to timeout }
                .collectLatest { (enabled, timeout) ->
                    if (enabled && timeout.minutes > 0) {
                        autoLockManager.startMonitoring(timeout.minutes)
                    } else {
                        autoLockManager.stopMonitoring()
                        autoLockManager.clearLockState()
                    }
                }
        }

        // Track touch interactions globally to reset inactivity timer.
        window.decorView.setOnTouchListener { _, _ ->
            autoLockManager.recordUserInteraction()
            false
        }

        ContextCompat.registerReceiver(
            this,
            hotwordReceiver,
            IntentFilter(ShadowApplication.ACTION_HOTWORD_DETECTED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            // Request permissions on start
            RequestAppPermissions()

            ShadowAITheme {
                // Authentication state
                val authState by authViewModel.authState.collectAsState()
                val isAppLocked by autoLockManager.isLocked.collectAsState()
                val requireBiometricForHistory by preferencesManager.requireBiometricForHistory
                    .collectAsState(initial = false)

                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (authState) {
                            is AuthState.Authenticated -> {
                                val currentUser by authViewModel.currentUser.collectAsState()

                                // Check for shared content and create Chat route with initial data
                                // Consume pending content only once
                                val sharedContent = consumePendingSharedContent()
                                val initialRoute = if (sharedContent != null) {
                                    Chat.create(
                                        initialText = sharedContent.first,
                                        imageUris = sharedContent.second
                                    )
                                } else {
                                    Chat.Default
                                }

                                // FIX: If we have shared content but viewmodel is already initialized (e.g. config change),
                                // we should also push it to VM directly if it wasn't consumed for initial route.
                                // Actually consumePendingSharedContent clears it, so we rely on initialRoute or VM push.
                                // If activity recreated, intent is re-delivered? No, usually savedInstanceState.
                                // But onCreate calls handleShareIntent(intent).

                                // Push to ViewModel if we are already observing it (e.g. onNewIntent delivered content)
                                LaunchedEffect(sharedContent) {
                                    if (sharedContent != null) {
                                        sharedContent.first?.let { chatViewModel.setSharedText(it) }
                                        if (sharedContent.second.isNotEmpty()) {
                                            chatViewModel.setSharedImages(sharedContent.second)
                                        }
                                    }
                                }

                                val backStack = rememberMutableStateListOf<NavigationRoute>(initialRoute)

                                ShadowAINavGraph(
                                    backStack = backStack,
                                    chatViewModel = chatViewModel,
                                    currentUser = currentUser,
                                    requireBiometricForHistory = requireBiometricForHistory,
                                    launchVoiceInput = launchVoiceInput,
                                    onVoiceInputConsumed = { launchVoiceInput = false },
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

                        // Show lockscreen overlay when auto-lock is active.
                        if (isAppLocked && authState is AuthState.Authenticated) {
                            LockScreenOverlay(
                                onUnlockSuccess = { autoLockManager.unlock() },
                                onDismissRequest = {}
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(hotwordReceiver) }
            .onFailure { error ->
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Failed to unregister hotword receiver", error)
                }
            }
        super.onDestroy()
    }

    /**
     * Apply or remove screenshot protection based on user settings.
     * When enabled, prevents screenshots and screen recordings of the app.
     */
    private fun applyScreenshotProtection(prevent: Boolean) {
        if (prevent) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle new intents when activity is in singleTop mode
        handleShareIntent(intent)

        // Push content to ViewModel immediately if activity is already running
        val shared = consumePendingSharedContent()
        if (shared != null) {
            val (text, images) = shared
            if (!text.isNullOrBlank()) {
                chatViewModel.setSharedText(text)
            }
            if (images.isNotEmpty()) {
                chatViewModel.setSharedImages(images)
            }
        }

        // Handle voice input action
        if (intent.action == ACTION_LAUNCH_VOICE_INPUT) {
            launchVoiceInput = true
        }
    }

    /**
     * Handle incoming share intents from other apps.
     * Supports:
     * - ACTION_SEND with text/plain
     * - ACTION_SEND with image/\*
     * - ACTION_SEND_MULTIPLE with image/\*
     */
    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val mimeType = intent.type
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Received ACTION_SEND, mimeType: $mimeType")
                }

                if (mimeType?.startsWith("text/") == true) {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (sharedText != null) {
                        pendingSharedText = sharedText
                    }
                } else if (mimeType?.startsWith("image/") == true) {
                    @Suppress("DEPRECATION")
                    val imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    }

                    if (imageUri != null) {
                        pendingSharedImages = listOf(imageUri)
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val mimeType = intent.type
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Received ACTION_SEND_MULTIPLE, mimeType: $mimeType")
                }

                if (mimeType?.startsWith("image/") == true) {
                    @Suppress("DEPRECATION")
                    val imageUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                         intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    }

                    if (imageUris != null && imageUris.isNotEmpty()) {
                        pendingSharedImages = imageUris
                    }
                }
            }
        }
    }

    /**
     * Get and clear pending shared content.
     * Call this after navigation to chat to process the shared content.
     *
     * @return Pair of (sharedText, sharedImageUris) or null if no content
     */
    private fun consumePendingSharedContent(): Pair<String?, List<Uri>>? {
        val text = pendingSharedText
        val images = pendingSharedImages
        if (text != null || images.isNotEmpty()) {
            pendingSharedText = null
            pendingSharedImages = emptyList()
            return Pair(text, images)
        }
        return null
    }
}

@Composable
fun RequestAppPermissions() {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results
        permissions.entries.forEach { (permission, isGranted) ->
            if (!isGranted) {
                Log.w("Permissions", "Permission denied: $permission")
            }
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()

        // Check if permissions are already granted before requesting
        val recordAudioPermission = android.Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(
                context, recordAudioPermission
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(recordAudioPermission)
        }

        // Android 13+ Notification Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                android.Manifest.permission.POST_NOTIFICATIONS,
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_AUDIO
            ).forEach { permission ->
                if (ContextCompat.checkSelfPermission(
                        context, permission
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    permissionsToRequest.add(permission)
                }
            }
        } else {
            // Below Android 13
            val readStoragePermission = android.Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(
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
