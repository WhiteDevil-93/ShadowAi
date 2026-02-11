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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.shadowai.app.R
import com.shadowai.app.auth.User
import com.shadowai.app.ui.chat.ChatMode
import com.shadowai.app.ui.theme.*

/**
 * Permanent Navigation Drawer for Expanded layouts (large tablets).
 *
 * Provides rich, permanent navigation with full text labels:
 * - Full user profile header with details
 * - Full chat mode list with descriptions
 * - Complete settings navigation
 * - 240dp width for comfortable tablet UX
 *
 * This is essentially the same content as the modal drawer in compact layouts,
 * but permanent and always visible.
 *
 * @param user Current authenticated user
 * @param onNavigateToSettings Callback for settings
 * @param onNavigateToProviderSelection Callback for provider selection
 * @param onSignOut Callback for sign out
 * @param selectedMode Currently selected chat mode
 * @param onModeSelected Callback when mode changes
 * @param modifier Optional modifier
 */
@Composable
fun ShadowAIExpandedDrawer(
    user: User?,
    onNavigateToSettings: () -> Unit,
    onNavigateToProviderSelection: () -> Unit,
    onSignOut: () -> Unit,
    selectedMode: ChatMode,
    onModeSelected: (ChatMode) -> Unit,
    modifier: Modifier = Modifier
) {
    PermanentDrawerSheet(
        modifier = modifier
            .widthIn(min = 240.dp, max = 280.dp)
            .background(bg_0),
        drawerContainerColor = bg_0,
        drawerContentColor = text_primary
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
        ) {
            // User Profile Header (expanded version)
            ExpandedUserHeader(user = user)

            Spacer(modifier = Modifier.height(24.dp))

            // Mode Selection Section
            SectionTitle("Chat Mode")

            ModeDrawerItem(
                mode = ChatMode.CHAT,
                icon = Icons.AutoMirrored.Default.Chat,
                label = "Chat",
                description = "General conversation",
                selected = selectedMode == ChatMode.CHAT,
                onClick = { onModeSelected(ChatMode.CHAT) }
            )

            ModeDrawerItem(
                mode = ChatMode.WRITE,
                icon = Icons.Default.Edit,
                label = "Write",
                description = "Drafting & writing tasks",
                selected = selectedMode == ChatMode.WRITE,
                onClick = { onModeSelected(ChatMode.WRITE) }
            )

            ModeDrawerItem(
                mode = ChatMode.CALL,
                icon = Icons.Default.Phone,
                label = "Call",
                description = "Phone call assistance",
                selected = selectedMode == ChatMode.CALL,
                onClick = { onModeSelected(ChatMode.CALL) }
            )

            ModeDrawerItem(
                mode = ChatMode.IMAGE,
                icon = Icons.Default.Image,
                label = "Image",
                description = "Generate images",
                selected = selectedMode == ChatMode.IMAGE,
                onClick = { onModeSelected(ChatMode.IMAGE) }
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = text_muted.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(24.dp))

            // Navigation Section
            SectionTitle("Navigation")

            DrawerNavItem(
                icon = Icons.Default.Cloud,
                label = "AI Providers",
                subtitle = "Switch runtime or API",
                onClick = onNavigateToProviderSelection
            )

            DrawerNavItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                subtitle = "App configuration",
                onClick = onNavigateToSettings
            )

            Spacer(modifier = Modifier.weight(1f))

            // Sign Out Button at bottom
            if (user != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = text_muted.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Expanded user header with full details.
 */
@Composable
private fun ExpandedUserHeader(user: User?) {
    val context = LocalContext.current

    val displayName = user?.displayName ?: "Guest User"
    val initials = displayName
        .split(" ")
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifEmpty { "U" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Large Avatar
        if (user?.photoUrl != null) {
            Image(
                painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(context)
                        .data(user.photoUrl)
                        .crossfade(true)
                        .build()
                ),
                contentDescription = "Profile picture",
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(emerald_core.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = emerald_core
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // User info
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = text_primary
        )

        Text(
            text = user?.email ?: "Not signed in",
            style = MaterialTheme.typography.bodySmall,
            color = text_muted
        )

        // Status indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(emerald_core, CircleShape)
            )
            Text(
                text = "Online",
                style = MaterialTheme.typography.labelSmall,
                color = emerald_core
            )
        }
    }
}

/**
 * Section title in drawer.
 */
@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Medium
        ),
        color = text_muted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * Mode selection item with selection indicator.
 */
@Composable
private fun ModeDrawerItem(
    mode: ChatMode,
    icon: ImageVector,
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) emerald_dark else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) emerald_core else text_secondary,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) emerald_core else text_primary,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = text_muted
                )
            }

            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(emerald_core, CircleShape)
                )
            }
        }
    }
}

/**
 * Navigation drawer item for non-mode actions.
 */
@Composable
private fun DrawerNavItem(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = text_secondary,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = text_primary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = text_muted
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = text_muted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
