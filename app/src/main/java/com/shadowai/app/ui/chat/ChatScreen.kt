package com.shadowai.app.ui.chat

import android.net.Uri
import com.shadowai.core.ProviderId
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.shadowai.app.R
import com.shadowai.app.ai.ConversationSummarizer
import com.shadowai.app.auth.User
import com.shadowai.app.ui.ChatMessage as UiChatMessage
import com.shadowai.app.ui.ChatViewModel
import com.shadowai.app.ui.chat.export.ExportDialog
import com.shadowai.app.ui.chat.export.ExportViewModel
import com.shadowai.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Main Chat Screen - Implements UI Orchestrator governance
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun ChatScreen(
    viewModel: ChatViewModel,
    summaryViewModel: SummaryViewModel = hiltViewModel(),
    voiceChatViewModel: VoiceChatViewModel = hiltViewModel(),
    exportViewModel: ExportViewModel = hiltViewModel(),
    user: User? = null,
    initialText: String? = null,
    initialImages: List<Uri> = emptyList(),
    launchVoiceInput: Boolean = false,
    onVoiceInputConsumed: () -> Unit = {},
    onNavigateToImageGeneration: () -> Unit = {},
    onNavigateToProviderSelection: () -> Unit = {},
    onNavigateToProviderConfig: (ProviderId) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val maxContextTokens = 4096
    val uiState by viewModel.uiState.collectAsState()
    val summaryUiState by summaryViewModel.uiState.collectAsState()
    val summaries by summaryViewModel.summaries.collectAsState()
    val currentSummary by summaryViewModel.currentSummary.collectAsState()
    val voiceUiState by voiceChatViewModel.uiState.collectAsState()
    val exportUiState by exportViewModel.uiState.collectAsState()

    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val conversationId = remember { "main-conversation" }
    var lastAutoSummaryMessageCount by remember(conversationId) { mutableStateOf(0) }
    var showSummaryDetail by remember { mutableStateOf<ConversationSummarizer.StoredSummary?>(null) }

    // Load summaries when composition starts
    LaunchedEffect(Unit) {
        summaryViewModel.loadSummaries(conversationId)
    }

    LaunchedEffect(currentSummary) {
        showSummaryDetail = currentSummary
    }

    LaunchedEffect(summaryUiState.canSummarize, summaryUiState.isSummarizing, uiState.messages.size) {
        if (!summaryUiState.canSummarize || summaryUiState.isSummarizing) return@LaunchedEffect
        if (uiState.messages.size <= lastAutoSummaryMessageCount) return@LaunchedEffect

        val chatMessages = uiState.messages.map(ChatMessage::fromUiMessage)
        summaryViewModel.checkSummarizationNeeded(chatMessages, maxContextTokens)
    }

    LaunchedEffect(summaryUiState.canSummarize, summaryUiState.isSummarizing, uiState.messages.size) {
        if (!summaryUiState.canSummarize || summaryUiState.isSummarizing) return@LaunchedEffect
        if (uiState.messages.size <= lastAutoSummaryMessageCount) return@LaunchedEffect

        val chatMessages = uiState.messages.map(ChatMessage::fromUiMessage)
        lastAutoSummaryMessageCount = uiState.messages.size
        summaryViewModel.summarizeAutomatically(
            messages = chatMessages,
            maxContext = maxContextTokens,
            conversationId = conversationId
        )
    }

    // Handle voice text results
    LaunchedEffect(voiceChatViewModel) {
        voiceChatViewModel.setVoiceTextCallback { text ->
            if (text.isNotBlank()) {
                viewModel.sendMessage(text)
            }
        }
    }

    // Show voice errors in snackbar
    LaunchedEffect(voiceUiState.errorMessage) {
        voiceUiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            voiceChatViewModel.clearError()
        }
    }

    // Trigger voice input when launched from widget/shortcut action.
    LaunchedEffect(launchVoiceInput) {
        if (launchVoiceInput) {
            voiceChatViewModel.startListening()
            onVoiceInputConsumed()
        }
    }

    // Scroll state for floating tabs
    val showTabs by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 || listState.firstVisibleItemScrollOffset < 24
        }
    }

    // Floating voice button visibility
    val showVoiceButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 || listState.firstVisibleItemScrollOffset < 100
        }
    }

    // Chat mode state
    var chatMode by remember { mutableStateOf(ChatMode.CHAT) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        voiceChatViewModel.stopListening()
    }

    // Voice permission handler
    VoicePermissionHandler(
        voiceChatViewModel = voiceChatViewModel,
        snackbarHostState = snackbarHostState,
        onPermissionGranted = {
            voiceChatViewModel.startListening()
        }
    )

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
        ExportDialog(
            isVisible = exportUiState.showExportDialog,
            onDismiss = { exportViewModel.hideExportDialog() },
            onExport = { format, shareAfter ->
                val title = uiState.messages.firstOrNull { !it.isUser && it.modelName != null }?.modelName ?: "Conversation"
                val modelName = uiState.messages.lastOrNull { !it.isUser && it.modelName != null }?.modelName
                exportViewModel.exportConversation(
                    conversationTitle = title,
                    format = format,
                    shareAfter = shareAfter,
                    conversationId = conversationId,
                    modelUsed = modelName
                )
            },
            isLoading = exportUiState.isExporting,
            exportResult = exportUiState.exportResult
        )

        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .systemBarsPadding(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                ChatTopAppBar(
                    onOpenDrawer = {
                        scope.launch {
                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                        }
                    },
                    onExportConversation = { exportViewModel.showExportDialog() },
                    modifier = Modifier.statusBarsPadding()
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                VoiceFeedbackOverlay(
                    isListening = voiceUiState.isListening,
                    confidence = voiceUiState.confidence,
                    partialText = voiceUiState.partialText,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                ChatMessageList(
                    messages = uiState.messages,
                    summaries = summaries,
                    onRetry = { viewModel.retryMessage(it) },
                    onSummaryRestore = { summary ->
                        summaryViewModel.restoreFromSummary(
                            summary,
                            onRestored = { restoredMessages ->
                                viewModel.restoreSummarizedMessages(restoredMessages)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Restored ${summary.metadata.messageCount} messages")
                                }
                            },
                            conversationId = conversationId
                        )
                    },
                    onSummaryView = { summary ->
                        summaryViewModel.viewSummary(summary)
                        showSummaryDetail = summary
                    },
                    onSummaryDismiss = { summary ->
                        summaryViewModel.dismissSummary(summary, conversationId)
                    },
                    onSummaryCopy = { summary, onComplete ->
                        summaryViewModel.copySummary(summary.content) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Summary copied to clipboard")
                            }
                            onComplete()
                        }
                    },
                    summaryDetailToShow = showSummaryDetail,
                    listState = listState,
                    modifier = Modifier.fillMaxSize()
                )

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

                AnimatedVisibility(
                    visible = showVoiceButton,
                    enter = fadeIn() + slideInHorizontally { it },
                    exit = fadeOut() + slideOutHorizontally { it },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 120.dp)
                ) {
                    FloatingVoiceButton(
                        isListening = voiceUiState.isListening,
                        confidence = voiceUiState.confidence,
                        onClick = { voiceChatViewModel.toggleVoiceInput() }
                    )
                }

                ChatInputField(
                    onSendMessage = { message, imageUri ->
                        val formattedMessage = formatMessageForMode(chatMode, message)

                        val allImageUris = mutableListOf<Uri>()
                        if (imageUri != null) allImageUris.add(imageUri)
                        val sharedImages = uiState.sharedImages
                        if (sharedImages.isNotEmpty()) {
                            allImageUris.addAll(sharedImages)
                        }

                        if (allImageUris.isNotEmpty()) {
                            viewModel.sendMessageWithAttachments(formattedMessage, allImageUris)
                        } else {
                            viewModel.sendMessage(formattedMessage)
                        }
                    },
                    mode = chatMode,
                    initialText = initialText ?: uiState.sharedText,
                    initialImageUris = if (initialImages.isNotEmpty()) initialImages else uiState.sharedImages,
                    onSharedContentConsumed = { viewModel.clearSharedContent() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
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

@Composable
private fun ChatMessageList(
    messages: List<UiChatMessage>,
    summaries: List<ConversationSummarizer.StoredSummary>,
    onRetry: (UiChatMessage) -> Unit,
    onSummaryRestore: (ConversationSummarizer.StoredSummary) -> Unit,
    onSummaryView: (ConversationSummarizer.StoredSummary) -> Unit,
    onSummaryDismiss: (ConversationSummarizer.StoredSummary) -> Unit,
    onSummaryCopy: (ConversationSummarizer.StoredSummary, () -> Unit) -> Unit,
    summaryDetailToShow: ConversationSummarizer.StoredSummary? = null,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 80.dp,
            bottom = 80.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (summaries.isNotEmpty()) {
            items(
                items = summaries,
                key = { it.metadata.id }
            ) { summary ->
                SummaryIndicator(
                    summary = summary,
                    onRestore = { onSummaryRestore(summary) },
                    onView = { onSummaryView(summary) }
                )
            }
        }

        summaryDetailToShow?.let { summary ->
            item(key = "summary-detail-${summary.metadata.id}") {
                SummaryDetail(
                    summary = summary,
                    onRestore = { onSummaryRestore(summary) },
                    onCopy = { onSummaryCopy(summary) {} },
                    onDismiss = { onSummaryDismiss(summary) }
                )
            }
        }

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
    onExportConversation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showExportMenu by remember { mutableStateOf(false) }

    Surface(
        color = Color.Black,
        modifier = modifier.fillMaxWidth()
    ) {
        CenterAlignedTopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(bg_0, shape = CircleShape)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(emerald_core, shape = CircleShape)
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
            actions = {
                IconButton(
                    onClick = { showExportMenu = true }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Export conversation",
                        tint = text_primary
                    )
                }
                DropdownMenu(
                    expanded = showExportMenu,
                    onDismissRequest = { showExportMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Export conversation") },
                        onClick = {
                            showExportMenu = false
                            onExportConversation()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = null
                            )
                        }
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = text_primary
            )
        )
    }
}

enum class ChatMode {
    CHAT,
    WRITE,
    CALL,
    IMAGE
}
