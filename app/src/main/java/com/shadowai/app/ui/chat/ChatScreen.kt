package com.shadowai.app.ui.chat

import android.net.Uri
import com.shadowai.core.ProviderId

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import com.shadowai.app.ui.theme.*
import com.shadowai.app.auth.User
import com.shadowai.app.ui.ChatMessage
import com.shadowai.app.ui.ChatViewModel
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background

/**
 * Main Chat Screen - Implements UI Orchestrator governance
 *
 * Governance Compliance:
 * - Visual hierarchy: Messages 80%, System feedback 15%, Controls 5%
 * - Floating chat tabs (auto-hide on scroll)
 * - System insets handling (imePadding, navigationBarsPadding)
 * - Material 3 compliance (colors, typography)
 * - Error pills (not full-width messages)
 *
 * @param viewModel The chat view model
 * @param user Current authenticated user data
 * @param onNavigateToImageGeneration Callback to navigate to image generation (focus mode)
 * @param onNavigateToProviderConfig Callback to navigate to provider configuration
 * @param onNavigateToSettings Callback to navigate to settings
 * @param onSignOut Callback for user sign out
 * @param modifier Optional modifier
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun ChatScreen(
    viewModel: ChatViewModel,
    user: User? = null,
    onNavigateToImageGeneration: () -> Unit = {},
    onNavigateToProviderSelection: () -> Unit = {},
    onNavigateToProviderConfig: (ProviderId) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Scroll state for floating tabs
    val showTabs by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 || listState.firstVisibleItemScrollOffset < 24
        }
    }

    // Chat mode state
    var chatMode by remember { mutableStateOf(ChatMode.CHAT) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = bg_1,
                drawerContentColor = text_primary
            ) {
                NavigationDrawerContent(
                    user = user,
                    onNavigateToSettings = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    },
                    onNavigateToProfile = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    },
                    onNavigateToProviderSelection = {
                        scope.launch { drawerState.close() }
                        onNavigateToProviderSelection()
                    },
                    onNavigateToProviderConfig = { providerId ->
                        scope.launch { drawerState.close() }
                        onNavigateToProviderConfig(providerId)
                    },
                    onSignOut = {
                        scope.launch { drawerState.close() }
                        onSignOut()
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .systemBarsPadding(), // GOVERNANCE: Mandatory system insets
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                ChatTopAppBar(
                    onOpenDrawer = {
                        scope.launch {
                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                        }
                    },
                    modifier = Modifier.statusBarsPadding() // GOVERNANCE: Mandatory status bar padding
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Main content: Chat messages (80% visual weight)
                ChatMessageList(
                    messages = uiState.messages,
                    listState = listState,
                    onRetry = { viewModel.retryMessage(it) },
                    modifier = Modifier.fillMaxSize()
                )

                // Floating chat tabs (auto-hide on scroll)
                // GOVERNANCE: Required floating tabs for mode switching
                AnimatedVisibility(
                    visible = showTabs,
                    enter = slideInVertically(),
                    exit = slideOutVertically(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    FloatingChatTabs(
                        selectedMode = chatMode,
                    onModeSelected = { mode ->
                        chatMode = mode
                        when (mode) {
                            ChatMode.IMAGE -> onNavigateToImageGeneration()
                            ChatMode.WRITE -> scope.launch {
                                snackbarHostState.showSnackbar("Write mode active: prompts are optimized for drafting.")
                            }
                            ChatMode.CALL -> scope.launch {
                                snackbarHostState.showSnackbar("Call mode active: describe who to call and the app will prepare a call action.")
                            }
                            ChatMode.CHAT -> Unit
                        }
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // Input field at bottom
                // GOVERNANCE: Must use imePadding() for keyboard handling
                ChatInputField(
                    onSendMessage = { message, imageUri ->
                        val formattedMessage = formatMessageForMode(chatMode, message)
                        if (imageUri != null) {
                            // TODO: Pass image URI to viewModel for multimodal processing
                            // For now, appending to message to indicate attachment (placeholder logic)
                            viewModel.sendMessage("$formattedMessage [Image Attached]")
                        } else {
                            viewModel.sendMessage(formattedMessage)
                        }
                    },
                    mode = chatMode,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding() // GOVERNANCE: Mandatory IME padding
                        .navigationBarsPadding() // GOVERNANCE: Mandatory navigation bar padding
                )
            }
        }
    }
}

private fun formatMessageForMode(mode: ChatMode, message: String): String {
    val trimmed = message.trim()
    return when (mode) {
        ChatMode.CHAT -> trimmed
        ChatMode.WRITE -> "Writing task: $trimmed"
        ChatMode.CALL -> "Call request: $trimmed"
        ChatMode.IMAGE -> "Image request: $trimmed"
    }
}

/**
 * Chat message list - Displays messages with proper visual hierarchy
 *
 * GOVERNANCE: Messages are visually dominant (80% weight)
 */
@Composable
private fun ChatMessageList(
    messages: List<ChatMessage>,
    listState: LazyListState,
    onRetry: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 80.dp, // Space for floating tabs
            bottom = 80.dp // Space for input field
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = messages,
            key = { it.id }
        ) { message ->
            ChatMessageItem(
                message = message,
                onRetry = { onRetry(message) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopAppBar(
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black, // Rule 2: OLED Black
        modifier = modifier.fillMaxWidth()
    ) {
        CenterAlignedTopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Emerald Signal - "Armed/Active"
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(bg_0, shape = androidx.compose.foundation.shape.CircleShape)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(emerald_core, shape = androidx.compose.foundation.shape.CircleShape)
                        )
                    }

                    Text(
                        text = "SHADOW AI",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = text_primary
                    )

                    Text(
                        text = "V2.5",
                        style = MaterialTheme.typography.labelSmall,
                        color = emerald_core.copy(alpha = 0.7f),
                        modifier = Modifier
                            .background(emerald_dark, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open navigation drawer",
                        tint = text_primary
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent, // Managed by Surface
                titleContentColor = text_primary
            )
        )
    }
}

/**
 * Chat modes for floating tabs
 */
enum class ChatMode {
    CHAT,
    WRITE,
    CALL,
    IMAGE
}

