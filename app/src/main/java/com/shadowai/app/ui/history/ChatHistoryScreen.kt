package com.shadowai.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shadowai.app.security.BiometricGuard
import com.shadowai.app.ui.ChatMessage
import kotlinx.coroutines.launch

/**
 * Chat History Screen - Displays conversation history with biometric protection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryScreen(
    viewModel: ChatHistoryViewModel = hiltViewModel(),
    requireBiometric: Boolean = true,
    onNavigateBack: () -> Unit = {},
    onConversationSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BiometricGuard(
        title = "Authentication Required",
        subtitle = "Access Conversation History",
        description = "Please authenticate to view your chat history",
        requireBiometric = requireBiometric
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Conversation History") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.showBatchExport() },
                            enabled = uiState.conversations.isNotEmpty()
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = "Batch export")
                        }
                        IconButton(
                            onClick = { viewModel.showClearAllConfirmation() },
                            enabled = uiState.conversations.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear all")
                        }
                    }
                )
            },
            modifier = modifier.fillMaxSize()
        ) { paddingValues ->
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.conversations.isEmpty() -> {
                    EmptyHistoryState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
                else -> {
                    ConversationList(
                        conversations = uiState.conversations,
                        selectedConversations = uiState.selectedConversations,
                        isInSelectionMode = uiState.isInSelectionMode,
                        onConversationSelected = onConversationSelected,
                        onConversationToggleSelection = { viewModel.toggleConversationSelection(it) },
                        onDeleteConversation = { viewModel.showDeleteConfirmation(it) },
                        onExportConversation = { viewModel.exportConversation(context, it) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
            }
        }

        // Delete confirmation dialog
        uiState.showDeleteConfirmation?.let { id ->
            AlertDialog(
                onDismissRequest = viewModel::dismissDialogs,
                title = { Text("Delete Conversation") },
                text = { Text("Are you sure you want to delete this conversation? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.deleteConversation(id)
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDialogs) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Clear all confirmation dialog
        if (uiState.showClearAllConfirmation) {
            AlertDialog(
                onDismissRequest = viewModel::dismissDialogs,
                title = { Text("Clear All History") },
                text = { Text("Are you sure you want to delete all conversations? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.clearAllConversations()
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDialogs) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Batch export dialog
        if (uiState.showBatchExportDialog) {
            BatchExportDialog(
                selectedCount = uiState.selectedConversations.size,
                totalCount = uiState.conversations.size,
                isExporting = uiState.isBatchExporting,
                exportResult = uiState.batchExportResult,
                onSelectAll = viewModel::selectAllConversations,
                onClearSelection = viewModel::clearSelection,
                onExport = { format ->
                    viewModel.exportSelectedConversations(context, format)
                },
                onDismiss = viewModel::hideBatchExport
            )
        }
    }
}

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No Conversations Yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Start chatting with Shadow AI to see your conversation history here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun ConversationList(
    conversations: List<ConversationSummary>,
    selectedConversations: Set<String>,
    isInSelectionMode: Boolean,
    onConversationSelected: (String) -> Unit,
    onConversationToggleSelection: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onExportConversation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(conversations, key = { it.id }) { conversation ->
            ConversationItem(
                conversation = conversation,
                isSelected = selectedConversations.contains(conversation.id),
                isInSelectionMode = isInSelectionMode,
                onClick = {
                    if (isInSelectionMode) {
                        onConversationToggleSelection(conversation.id)
                    } else {
                        onConversationSelected(conversation.id)
                    }
                },
                onDelete = { onDeleteConversation(conversation.id) },
                onExport = { onExportConversation(conversation.id) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationItem(
    conversation: ConversationSummary,
    isSelected: Boolean,
    isInSelectionMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isInSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${conversation.messageCount} messages • ${conversation.lastUpdated}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Export") },
                        onClick = {
                            onExport()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            onDelete()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchExportDialog(
    selectedCount: Int,
    totalCount: Int,
    isExporting: Boolean,
    exportResult: BatchExportResult?,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onExport: (format: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFormat by remember { mutableStateOf("ZIP") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Batch Export") },
        text = {
            Column {
                Text("Export $selectedCount of $totalCount conversations")

                if (selectedCount < totalCount) {
                    TextButton(
                        onClick = onSelectAll,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Select All")
                    }
                }

                Text(
                    text = "Export Format",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp)
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    listOf("ZIP", "PDF", "MARKDOWN", "JSON").forEachIndexed { index, format ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = 4
                            ),
                            onClick = { selectedFormat = format },
                            selected = selectedFormat == format
                        ) {
                            Text(format)
                        }
                    }
                }

                exportResult?.let { result ->
                    when (result) {
                        is BatchExportResult.Success -> {
                            Text(
                                text = "Export successful!\n${result.filePath}",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                        is BatchExportResult.Error -> {
                            Text(
                                text = "Export failed: ${result.message}",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onExport(selectedFormat) },
                enabled = !isExporting && selectedCount > 0,
                modifier = Modifier.padding(8.dp)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isExporting
            ) {
                Text("Cancel")
            }
        }
    )
}

data class ConversationSummary(
    val id: String,
    val title: String,
    val messageCount: Int,
    val lastUpdated: String
)

data class ChatHistoryUiState(
    val conversations: List<ConversationSummary> = emptyList(),
    val isLoading: Boolean = true,
    val showDeleteConfirmation: String? = null,
    val showClearAllConfirmation: Boolean = false,
    val exportStatus: ExportStatus? = null,
    val showBatchExportDialog: Boolean = false,
    val selectedConversations: Set<String> = emptySet(),
    val isInSelectionMode: Boolean = false,
    val isBatchExporting: Boolean = false,
    val batchExportResult: BatchExportResult? = null
)

sealed class ExportStatus {
    data object InProgress : ExportStatus()
    data class Success(val filePath: String) : ExportStatus()
    data class Error(val message: String) : ExportStatus()
}

sealed class BatchExportResult {
    data class Success(val filePath: String) : BatchExportResult()
    data class Error(val message: String) : BatchExportResult()
}
