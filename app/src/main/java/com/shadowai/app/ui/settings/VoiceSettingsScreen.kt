package com.shadowai.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Voice settings screen for configuring voice recognition and hotword detection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    viewModel: VoiceSettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Voice Recognition Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Voice Recognition",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Divider()

                    // Hotword Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Hey Shadow Hotword",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Wake up the app by saying the hotword",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.hotwordEnabled,
                            onCheckedChange = { viewModel.setHotwordEnabled(it) }
                        )
                    }

                    // Custom Hotword Phrase
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Custom Hotword Phrase",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = uiState.customHotwordPhrase,
                            onValueChange = { viewModel.setCustomHotwordPhrase(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text("Hey Shadow")
                            },
                            singleLine = true,
                            enabled = uiState.hotwordEnabled
                        )
                        Text(
                            text = "Leave empty to use default phrase",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // Hotword Sensitivity Slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Hotword Sensitivity Threshold",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Loose",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = uiState.hotwordSensitivity,
                                onValueChange = { viewModel.setHotwordSensitivity(it) },
                                valueRange = 0.3f..0.95f,
                                modifier = Modifier.weight(1f),
                                enabled = uiState.hotwordEnabled
                            )
                            Text(
                                text = "Strict",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Current: ${(uiState.hotwordSensitivity * 100).toInt()}% confidence required",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Status Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (uiState.isListening) {
                            "🎤 Listening for voice input..."
                        } else if (uiState.hotwordEnabled) {
                            "🔊 Hotword detection is active"
                        } else {
                            "❌ Voice recognition is disabled"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.isListening) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "💡 Tips",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "• Make sure microphone permissions are granted\n• Speak clearly for better recognition\n• Lower sensitivity = more false activations\n• Custom phrases should be unique and short",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
