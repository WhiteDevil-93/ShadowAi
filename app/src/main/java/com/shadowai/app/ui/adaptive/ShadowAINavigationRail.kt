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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shadowai.app.auth.User
import com.shadowai.app.ui.chat.ChatMode
import com.shadowai.app.ui.theme.*

/**
 * Navigation Rail for Medium layouts (small tablets, foldables).
 *
 * Provides permanent navigation on the left side with:
 * - User avatar at top
 * - Chat mode selectors (icons only)
 * - Settings and sign out at bottom
 * - 80dp width optimized for tablet touch targets
 *
 * @param selectedMode Currently selected chat mode
 * @param onModeSelected Callback when chat mode changes
 * @param onNavigateToSettings Callback for settings
 * @param onNavigateToProviders Callback for provider selection
 * @param onSignOut Callback for sign out
 * @param user Current authenticated user
 * @param modifier Optional modifier
 */
@Composable
fun ShadowAINavigationRail(
    selectedMode: ChatMode,
    onModeSelected: (ChatMode) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProviders: () -> Unit,
    onSignOut: () -> Unit,
    user: User?,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier
            .widthIn(min = 80.dp, max = 100.dp)
            .background(bg_0),
        containerColor = bg_0,
        contentColor = text_primary
    ) {
        // Top: User Avatar
        NavigationRailItem(
            icon = { UserAvatar(user = user, size = 40.dp) },
            label = { Text("Profile", style = MaterialTheme.typography.labelSmall) },
            selected = false,
            onClick = onNavigateToSettings,
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = emerald_core,
                selectedTextColor = emerald_core,
                unselectedIconColor = text_secondary,
                unselectedTextColor = text_muted,
                indicatorColor = emerald_dark
            )
        )

        Spacer(modifier = Modifier.weight(0.1f))

        // Middle: Chat Mode Navigation
        NavigationRailHeader(text = "Modes")

        ChatModeRailItem(
            mode = ChatMode.CHAT,
            icon = Icons.AutoMirrored.Default.Chat,
            label = "Chat",
            selected = selectedMode == ChatMode.CHAT,
            onClick = { onModeSelected(ChatMode.CHAT) }
        )

        ChatModeRailItem(
            mode = ChatMode.WRITE,
            icon = Icons.Default.Edit,
            label = "Write",
            selected = selectedMode == ChatMode.WRITE,
            onClick = { onModeSelected(ChatMode.WRITE) }
        )

        ChatModeRailItem(
            mode = ChatMode.CALL,
            icon = Icons.Default.Phone,
            label = "Call",
            selected = selectedMode == ChatMode.CALL,
            onClick = { onModeSelected(ChatMode.CALL) }
        )

        ChatModeRailItem(
            mode = ChatMode.IMAGE,
            icon = Icons.Default.Image,
            label = "Image",
            selected = selectedMode == ChatMode.IMAGE,
            onClick = { onModeSelected(ChatMode.IMAGE) }
        )

        Spacer(modifier = Modifier.weight(0.2f))
        HorizontalDivider(color = text_muted.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(modifier = Modifier.weight(0.1f))

        // Bottom: Actions
        NavigationRailHeader(text = "Actions")

        NavigationRailItem(
            icon = { Icon(Icons.Default.Cloud, contentDescription = "Providers") },
            label = { Text("Providers", style = MaterialTheme.typography.labelSmall) },
            selected = false,
            onClick = onNavigateToProviders,
            colors = NavigationRailItemDefaults.colors(
                unselectedIconColor = text_secondary,
                unselectedTextColor = text_muted
            )
        )

        NavigationRailItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings", style = MaterialTheme.typography.labelSmall) },
            selected = false,
            onClick = onNavigateToSettings,
            colors = NavigationRailItemDefaults.colors(
                unselectedIconColor = text_secondary,
                unselectedTextColor = text_muted
            )
        )

        NavigationRailItem(
            icon = { Icon(Icons.AutoMirrored.Default.Logout, contentDescription = "Sign Out") },
            label = { Text("Sign Out", style = MaterialTheme.typography.labelSmall) },
            selected = false,
            onClick = onSignOut,
            colors = NavigationRailItemDefaults.colors(
                unselectedIconColor = MaterialTheme.colorScheme.error,
                unselectedTextColor = MaterialTheme.colorScheme.error
            )
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Chat mode item for navigation rail with emerald accent when selected.
 */
@Composable
private fun ChatModeRailItem(
    mode: ChatMode,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationRailItem(
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        selected = selected,
        onClick = onClick,
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = emerald_core,
            selectedTextColor = emerald_core,
            unselectedIconColor = text_secondary,
            unselectedTextColor = text_muted,
            indicatorColor = emerald_dark
        )
    )
}

/**
 * Small header text for rail sections.
 */
@Composable
private fun NavigationRailHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Medium
        ),
        color = text_muted,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

/**
 * User avatar component for navigation rail.
 */
@Composable
private fun UserAvatar(user: User?, size: androidx.compose.ui.unit.Dp) {
    if (user?.photoUrl != null) {
        AsyncImage(
            model = user.photoUrl,
            contentDescription = "Profile",
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        val initials = user?.displayName
            ?.split(" ")
            ?.mapNotNull { it.firstOrNull()?.uppercaseChar() }
            ?.take(2)
            ?.joinToString("")
            ?: "U"

        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(emerald_core.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = emerald_core
            )
        }
    }
}
