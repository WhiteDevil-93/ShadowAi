/*
 * Copyright 2025 ShadowAi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shadowai.app.ui.adaptive

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shadowai.app.auth.User
import com.shadowai.app.ui.ChatMessage
import com.shadowai.app.ui.ChatViewModel
import com.shadowai.app.ui.theme.bg_0
import com.shadowai.app.ui.theme.bg_1

/**
 * Adaptive Chat Layout - Implements responsive design for all screen sizes.
 */
@Composable
fun AdaptiveChatLayout(
    layoutType: AdaptiveLayoutType,
    viewModel: ChatViewModel,
    user: User?,
    chatMode: com.shadowai.app.ui.chat.ChatMode,
    onModeChange: (com.shadowai.app.ui.chat.ChatMode) -> Unit,
    onSendMessage: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProviderSelection: () -> Unit,
    onNavigateToImageGeneration: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Wrapper for onSendMessage to handle the extra Uri? parameter from ChatInputField
    // For now, we ignore the URI here if coming from compact layout calls
    // Ideally, AdaptiveChatLayout signature should be updated to support multimodal input
    val onSendMessageMultimodal: (String, Uri?) -> Unit = { message, uri ->
        // TODO: Properly propagate URI through ViewModel in a future update
        // For now, append indication to text to maintain contract
        if (uri != null) {
            onSendMessage("$message [Image Attached]")
        } else {
            onSendMessage(message)
        }
    }

    when (layoutType) {
        AdaptiveLayoutType.COMPACT -> {
            CompactChatLayout(
                viewModel = viewModel,
                user = user,
                uiState = uiState,
                chatMode = chatMode,
                onModeChange = onModeChange,
                onSendMessage = onSendMessageMultimodal,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToProviderSelection = onNavigateToProviderSelection,
                onNavigateToImageGeneration = onNavigateToImageGeneration,
                onSignOut = onSignOut,
                modifier = modifier
            )
        }
        AdaptiveLayoutType.MEDIUM -> {
            MediumChatLayout(
                viewModel = viewModel,
                user = user,
                uiState = uiState,
                chatMode = chatMode,
                onModeChange = onModeChange,
                onSendMessage = onSendMessageMultimodal,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToProviderSelection = onNavigateToProviderSelection,
                onNavigateToImageGeneration = onNavigateToImageGeneration,
                onSignOut = onSignOut,
                modifier = modifier
            )
        }
        AdaptiveLayoutType.EXPANDED -> {
            ExpandedChatLayout(
                viewModel = viewModel,
                user = user,
                uiState = uiState,
                chatMode = chatMode,
                onModeChange = onModeChange,
                onSendMessage = onSendMessageMultimodal,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToProviderSelection = onNavigateToProviderSelection,
                onNavigateToImageGeneration = onNavigateToImageGeneration,
                onSignOut = onSignOut,
                modifier = modifier
            )
        }
    }
}

/**
 * Compact layout for phones (< 600dp width).
 */
@Composable
private fun CompactChatLayout(
    viewModel: ChatViewModel,
    user: User?,
    uiState: com.shadowai.app.ui.ChatUiState,
    chatMode: com.shadowai.app.ui.chat.ChatMode,
    onModeChange: (com.shadowai.app.ui.chat.ChatMode) -> Unit,
    onSendMessage: (String, Uri?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProviderSelection: () -> Unit,
    onNavigateToImageGeneration: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    com.shadowai.app.ui.chat.ChatScreen(
        viewModel = viewModel,
        user = user,
        onNavigateToImageGeneration = onNavigateToImageGeneration,
        onNavigateToProviderSelection = onNavigateToProviderSelection,
        onNavigateToProviderConfig = { /* Handled in drawer */ },
        onNavigateToSettings = onNavigateToSettings,
        onSignOut = onSignOut,
        modifier = modifier
    )
}

/**
 * Medium layout for small tablets and foldables (600dp - 840dp width).
 */
@Composable
private fun MediumChatLayout(
    viewModel: ChatViewModel,
    user: User?,
    uiState: com.shadowai.app.ui.ChatUiState,
    chatMode: com.shadowai.app.ui.chat.ChatMode,
    onModeChange: (com.shadowai.app.ui.chat.ChatMode) -> Unit,
    onSendMessage: (String, Uri?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProviderSelection: () -> Unit,
    onNavigateToImageGeneration: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxSize()) {
        ShadowAINavigationRail(
            selectedMode = chatMode,
            onModeSelected = { mode ->
                onModeChange(mode)
                if (mode == com.shadowai.app.ui.chat.ChatMode.IMAGE) {
                    onNavigateToImageGeneration()
                }
            },
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToProviders = onNavigateToProviderSelection,
            onSignOut = onSignOut,
            user = user,
            modifier = Modifier.fillMaxHeight()
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .background(bg_0)
        ) {
            MediumChatContent(
                viewModel = viewModel,
                uiState = uiState,
                chatMode = chatMode,
                onSendMessage = onSendMessage,
                onNavigateToImageGeneration = onNavigateToImageGeneration,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Expanded layout for large tablets (>= 840dp width).
 */
@Composable
private fun ExpandedChatLayout(
    viewModel: ChatViewModel,
    user: User?,
    uiState: com.shadowai.app.ui.ChatUiState,
    chatMode: com.shadowai.app.ui.chat.ChatMode,
    onModeChange: (com.shadowai.app.ui.chat.ChatMode) -> Unit,
    onSendMessage: (String, Uri?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProviderSelection: () -> Unit,
    onNavigateToImageGeneration: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxSize()) {
        ShadowAIExpandedDrawer(
            user = user,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToProviderSelection = onNavigateToProviderSelection,
            onSignOut = onSignOut,
            selectedMode = chatMode,
            onModeSelected = { mode ->
                onModeChange(mode)
                if (mode == com.shadowai.app.ui.chat.ChatMode.IMAGE) {
                    onNavigateToImageGeneration()
                }
            },
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 280.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.6f)
                .background(bg_0)
        ) {
            ExpandedChatContent(
                viewModel = viewModel,
                uiState = uiState,
                chatMode = chatMode,
                onSendMessage = onSendMessage,
                onNavigateToImageGeneration = onNavigateToImageGeneration,
                modifier = Modifier.fillMaxSize()
            )
        }

        ContextSidebar(
            user = user,
            currentMode = chatMode,
            onModeChange = onModeChange,
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.2f)
                .background(bg_1)
        )
    }
}

/**
 * Chat content optimized for medium screens.
 */
@Composable
private fun MediumChatContent(
    viewModel: ChatViewModel,
    uiState: com.shadowai.app.ui.ChatUiState,
    chatMode: com.shadowai.app.ui.chat.ChatMode,
    onSendMessage: (String, Uri?) -> Unit,
    onNavigateToImageGeneration: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    Box(modifier = modifier) {
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = 16.dp,
                bottom = 100.dp
            ),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = uiState.messages,
                key = { it.id }
            ) { message ->
                com.shadowai.app.ui.chat.ChatMessageItem(
                    message = message,
                    onRetry = { viewModel.retryMessage(message) }
                )
            }
        }

        com.shadowai.app.ui.chat.ChatInputField(
            onSendMessage = onSendMessage,
            mode = chatMode,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
        )
    }
}

/**
 * Chat content optimized for expanded screens.
 */
@Composable
private fun ExpandedChatContent(
    viewModel: ChatViewModel,
    uiState: com.shadowai.app.ui.ChatUiState,
    chatMode: com.shadowai.app.ui.chat.ChatMode,
    onSendMessage: (String, Uri?) -> Unit,
    onNavigateToImageGeneration: () -> Unit,
    modifier: Modifier = Modifier
) {
    MediumChatContent(
        viewModel = viewModel,
        uiState = uiState,
        chatMode = chatMode,
        onSendMessage = onSendMessage,
        onNavigateToImageGeneration = onNavigateToImageGeneration,
        modifier = modifier
    )
}

/**
 * Context sidebar for expanded layout.
 */
@Composable
private fun ContextSidebar(
    user: User?,
    currentMode: com.shadowai.app.ui.chat.ChatMode,
    onModeChange: (com.shadowai.app.ui.chat.ChatMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(16.dp)
    ) {
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleSmall,
            color = com.shadowai.app.ui.theme.text_primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        com.shadowai.app.ui.chat.ChatMode.entries.forEach { mode ->
            val selected = mode == currentMode
            Surface(
                onClick = { onModeChange(mode) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = MaterialTheme.shapes.small,
                color = if (selected) com.shadowai.app.ui.theme.emerald_dark else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (mode) {
                            com.shadowai.app.ui.chat.ChatMode.CHAT -> Icons.AutoMirrored.Default.Chat
                            com.shadowai.app.ui.chat.ChatMode.WRITE -> Icons.Default.Edit
                            com.shadowai.app.ui.chat.ChatMode.CALL -> Icons.Default.Phone
                            com.shadowai.app.ui.chat.ChatMode.IMAGE -> Icons.Default.Image
                        },
                        contentDescription = mode.name,
                        tint = if (selected) com.shadowai.app.ui.theme.emerald_core else com.shadowai.app.ui.theme.text_secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) com.shadowai.app.ui.theme.emerald_core else com.shadowai.app.ui.theme.text_primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = com.shadowai.app.ui.theme.text_muted.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(24.dp))

        if (user != null) {
            Text(
                text = "Signed in as",
                style = MaterialTheme.typography.labelSmall,
                color = com.shadowai.app.ui.theme.text_muted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = user.displayName ?: "User",
                style = MaterialTheme.typography.bodyMedium,
                color = com.shadowai.app.ui.theme.text_primary
            )
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodySmall,
                color = com.shadowai.app.ui.theme.text_muted
            )
        }
    }
}
