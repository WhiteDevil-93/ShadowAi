package com.shadowai.app.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shadowai.app.auth.User
import com.shadowai.app.ui.theme.text_primary

/**
 * Navigation drawer content for the chat screen.
 */
@Composable
fun NavigationDrawerContent(
    user: User?,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToProviderSelection: () -> Unit,
    onNavigateToProviderConfig: (com.shadowai.core.ProviderId) -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // User info header
        if (user != null) {
            UserHeader(user = user)
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Navigation items
        NavigationDrawerItem(
            label = { Text("Settings") },
            selected = false,
            onClick = onNavigateToSettings,
            icon = { Icon(Icons.Default.Settings, contentDescription = null) }
        )

        NavigationDrawerItem(
            label = { Text("Profile") },
            selected = false,
            onClick = onNavigateToProfile,
            icon = { Icon(Icons.Default.Person, contentDescription = null) }
        )

        NavigationDrawerItem(
            label = { Text("AI Providers") },
            selected = false,
            onClick = onNavigateToProviderSelection,
            icon = { Icon(Icons.Default.Cloud, contentDescription = null) }
        )

        Spacer(modifier = Modifier.weight(1f))

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Sign out button
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out")
        }
    }
}

@Composable
private fun UserHeader(user: User) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // Avatar placeholder
        Surface(
            modifier = Modifier.size(64.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = user.displayName?.firstOrNull()?.toString()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = user.displayName ?: "Unknown User",
            style = MaterialTheme.typography.titleMedium,
            color = text_primary,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = user.email ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
