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
@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package com.shadowai.app.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import com.shadowai.app.auth.User
import com.shadowai.app.ui.ChatViewModel
import com.shadowai.app.ui.chat.ChatMode
import com.shadowai.app.ui.theme.bg_0
import com.shadowai.app.ui.theme.text_primary

/**
 * Adaptive Scaffold - Entry point for responsive layouts in ShadowAi.
 *
 * This scaffold automatically selects the appropriate layout based on window size:
 * - COMPACT (phones): Uses NavigationSuite with bottom bar + modal drawer
 * - MEDIUM (small tablets): Navigation rail on left
 * - EXPANDED (large tablets): Permanent drawer on left + context sidebar
 *
 * ANDROIDIFY PATTERN IMPLEMENTATION:
 * Uses Material3 Adaptive's NavigationSuiteScaffold which automatically
 * handles layout switching based on window size class.
 *
 * @param viewModel Shared chat view model
 * @param user Current authenticated user
 * @param currentRoute Current navigation route for highlighting
 * @param onNavigateToRoute Callback for navigation
 * @param onNavigateToImageGeneration Callback for image generation
 * @param onNavigateToProviderSelection Callback for provider selection
 * @param onNavigateToSettings Callback for settings
 * @param onSignOut Callback for sign out
 * @param content Main content to display
 * @param modifier Optional modifier
 */
@Composable
fun AdaptiveScaffold(
    viewModel: ChatViewModel,
    user: User?,
    currentRoute: String? = null,
    onNavigateToRoute: (String) -> Unit = {},
    onNavigateToImageGeneration: () -> Unit = {},
    onNavigateToProviderSelection: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onSignOut: () -> Unit = {},
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine layout type from window size class
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val layoutType = AdaptiveLayoutType.fromWindowSizeClass(windowSizeClass)

    // Chat mode state
    var chatMode by remember { mutableStateOf(ChatMode.CHAT) }

    // Use NavigationSuiteScaffold for automatic layout adaptation
    // This handles bottom bar, rail, and drawer automatically
    Box(modifier = modifier.fillMaxSize()) {
        when (layoutType) {
            AdaptiveLayoutType.COMPACT -> {
                // Compact: Use NavigationSuite with BottomBar
                CompactNavigationSuite(
                    chatMode = chatMode,
                    onModeChange = { mode ->
                        chatMode = mode
                        if (mode == ChatMode.IMAGE) onNavigateToImageGeneration()
                    },
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToProviders = onNavigateToProviderSelection,
                    content = content
                )
            }

            AdaptiveLayoutType.MEDIUM -> {
                // Medium: Two-pane with rail
                AdaptiveChatLayout(
                    layoutType = layoutType,
                    viewModel = viewModel,
                    user = user,
                    chatMode = chatMode,
                    onModeChange = { mode ->
                        chatMode = mode
                        if (mode == ChatMode.IMAGE) onNavigateToImageGeneration()
                    },
                    onSendMessage = { message ->
                        viewModel.sendMessage(formatMessage(chatMode, message))
                    },
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToProviderSelection = onNavigateToProviderSelection,
                    onNavigateToImageGeneration = onNavigateToImageGeneration,
                    onSignOut = onSignOut
                )
            }

            AdaptiveLayoutType.EXPANDED -> {
                // Expanded: Three-pane layout
                AdaptiveChatLayout(
                    layoutType = layoutType,
                    viewModel = viewModel,
                    user = user,
                    chatMode = chatMode,
                    onModeChange = { mode ->
                        chatMode = mode
                        if (mode == ChatMode.IMAGE) onNavigateToImageGeneration()
                    },
                    onSendMessage = { message ->
                        viewModel.sendMessage(formatMessage(chatMode, message))
                    },
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToProviderSelection = onNavigateToProviderSelection,
                    onNavigateToImageGeneration = onNavigateToImageGeneration,
                    onSignOut = onSignOut
                )
            }
        }
    }
}

/**
 * Compact layout using NavigationSuiteScaffold.
 * Provides bottom navigation bar on phones.
 */
@Composable
private fun CompactNavigationSuite(
    chatMode: ChatMode,
    onModeChange: (ChatMode) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProviders: () -> Unit,
    content: @Composable () -> Unit
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    // Navigation items
    val items = listOf(
        NavigationItem(
            label = "Chat",
            icon = Icons.AutoMirrored.Default.Chat,
            selected = chatMode == ChatMode.CHAT,
            onClick = { onModeChange(ChatMode.CHAT) }
        ),
        NavigationItem(
            label = "Write",
            icon = Icons.Default.Edit,
            selected = chatMode == ChatMode.WRITE,
            onClick = { onModeChange(ChatMode.WRITE) }
        ),
        NavigationItem(
            label = "Image",
            icon = Icons.Default.Image,
            selected = chatMode == ChatMode.IMAGE,
            onClick = { onModeChange(ChatMode.IMAGE) }
        ),
        NavigationItem(
            label = "More",
            icon = Icons.Default.MoreVert,
            selected = false,
            onClick = { /* Opens more menu */ }
        )
    )

    // Determine navigation suite type based on window size
    val navigationSuiteType = when (windowSizeClass.windowWidthSizeClass) {
        WindowWidthSizeClass.COMPACT -> NavigationSuiteType.NavigationBar
        WindowWidthSizeClass.MEDIUM -> NavigationSuiteType.NavigationRail
        else -> NavigationSuiteType.NavigationBar
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            items.forEach { item ->
                item(
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label) },
                    selected = item.selected,
                    onClick = item.onClick
                )
            }
        },
        layoutType = navigationSuiteType,
        containerColor = bg_0,
        contentColor = text_primary,
        modifier = Modifier.fillMaxSize()
    ) {
        content()
    }
}

/**
 * Navigation item data class.
 */
private data class NavigationItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit
)

/**
 * Format message based on chat mode.
 */
private fun formatMessage(mode: ChatMode, message: String): String {
    val trimmed = message.trim()
    return when (mode) {
        ChatMode.CHAT -> trimmed
        ChatMode.WRITE -> "Writing task: $trimmed"
        ChatMode.CALL -> "Call request: $trimmed"
        ChatMode.IMAGE -> "Image request: $trimmed"
    }
}

/**
 * Utility to get current layout type in any composable.
 */
@Composable
fun rememberAdaptiveLayoutType(): AdaptiveLayoutType {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return AdaptiveLayoutType.fromWindowSizeClass(windowSizeClass)
}
