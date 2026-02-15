package com.shadowai.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.automirrored.filled.Message

/**
 * Settings screen with navigation entry points.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToCredits: () -> Unit,
    onNavigateToGeneration: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToHotSwap: () -> Unit,
    onNavigateToSecurity: () -> Unit = {},
    onNavigateToVoiceSettings: () -> Unit = {},
    onNavigateToChatHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        },
        modifier = modifier.systemBarsPadding()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Security
            ListItem(
                headlineContent = { Text("Security") },
                supportingContent = { Text("Biometric auth, Auto-lock, Screenshot protection") },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.clickable { onNavigateToSecurity() }
            )
            HorizontalDivider()

            // Chat History
            ListItem(
                headlineContent = { Text("Chat History") },
                supportingContent = { Text("View past conversations (biometric protected)") },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.clickable { onNavigateToChatHistory() }
            )
            HorizontalDivider()

            // Voice Settings
            ListItem(
                headlineContent = { Text("Voice") },
                supportingContent = { Text("Voice recognition, Hotword detection") },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.clickable { onNavigateToVoiceSettings() }
            )
            HorizontalDivider()

            // Appearance
            ListItem(
                headlineContent = { Text("Appearance") },
                supportingContent = { Text("Theme, Font size") },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { onNavigateToAppearance() }
            )
            HorizontalDivider()

            // Usage & Credits Navigation Item
            ListItem(
                headlineContent = { Text("Usage & Credits") },
                supportingContent = { Text("View API usage and remaining credits") },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { onNavigateToCredits() }
            )
            HorizontalDivider()

            // Generation Settings
            ListItem(
                headlineContent = { Text("Generation Parameters") },
                supportingContent = { Text("Temperature, Top P, Top K, Safety filters") },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { onNavigateToGeneration() }
            )
            HorizontalDivider()

            // Diagnostics
            ListItem(
                headlineContent = { Text("Diagnostics") },
                supportingContent = { Text("View error logs and export reports") },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { onNavigateToDiagnostics() }
            )
            HorizontalDivider()

            // Hot-Swap Settings
            ListItem(
                headlineContent = { Text("Provider Hot Swap") },
                supportingContent = { Text("Manage provider configs at runtime") },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { onNavigateToHotSwap() }
            )
            HorizontalDivider()
        }
    }
}
