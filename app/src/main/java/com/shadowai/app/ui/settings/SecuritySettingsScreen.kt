package com.shadowai.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shadowai.app.security.BiometricAuthManager
import com.shadowai.app.security.PreferencesManager

/**
 * Security Settings Screen - Configures app security features
 *
 * Features:
 * - Biometric authentication requirements
 * - Screenshot prevention
 * - Auto-lock configuration
 * - Lock status display
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBiometricSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SecuritySettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val biometricAuthManager = remember { BiometricAuthManager(context) }
    
    // Biometric settings
    val requireBiometricForDownloads by viewModel.requireBiometricForDownloads.collectAsStateWithLifecycle()
    val requireBiometricForHistory by viewModel.requireBiometricForHistory.collectAsStateWithLifecycle()
    val requireBiometricForSettings by viewModel.requireBiometricForSettings.collectAsStateWithLifecycle()
    
    // Screenshot protection
    val preventScreenshots by viewModel.preventScreenshots.collectAsStateWithLifecycle()
    
    // Auto-lock settings
    val autoLockEnabled by viewModel.autoLockEnabled.collectAsStateWithLifecycle()
    val autoLockTimeout by viewModel.autoLockTimeout.collectAsStateWithLifecycle()
    
    // Biometric capability check
    val canUseBiometric = remember { biometricAuthManager.canAuthenticate() }
    val authType = remember { biometricAuthManager.getAuthType() }
    val isDeviceSecure = remember { biometricAuthManager.isDeviceSecure() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Biometric Section
            SecuritySection(
                title = "Biometric Authentication",
                icon = Icons.Default.Fingerprint
            ) {
                Text(
                    text = when {
                        !isDeviceSecure -> "Device lock not available. Enable PIN, pattern, or password."
                        authType == BiometricAuthManager.AuthType.BIOMETRIC -> "Device supports biometric authentication"
                        authType == BiometricAuthManager.AuthType.DEVICE_CREDENTIAL -> "Device secure with PIN/pattern/password"
                        else -> "Authentication not available"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                BiometricToggle(
                    title = "Require for Model Downloads",
                    description = "Authenticate before downloading AI models",
                    checked = requireBiometricForDownloads,
                    onCheckedChange = { viewModel.setRequireBiometricForDownloads(it) },
                    enabled = isDeviceSecure
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                BiometricToggle(
                    title = "Require for Chat History",
                    description = "Authenticate before accessing chat history",
                    checked = requireBiometricForHistory,
                    onCheckedChange = { viewModel.setRequireBiometricForHistory(it) },
                    enabled = isDeviceSecure
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                BiometricToggle(
                    title = "Require for Settings",
                    description = "Authenticate before accessing settings",
                    checked = requireBiometricForSettings,
                    onCheckedChange = { viewModel.setRequireBiometricForSettings(it) },
                    enabled = isDeviceSecure
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Screenshot Protection Section
            SecuritySection(
                title = "Screenshot Protection",
                icon = Icons.Default.Screenshot
            ) {
                SwitchWithDescription(
                    title = "Prevent Screenshots",
                    description = "Block screenshots and screen recordings in the app. When disabled, you can take legitimate screenshots for debugging or sharing.",
                    checked = preventScreenshots,
                    onCheckedChange = { viewModel.setPreventScreenshots(it) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Auto-lock Section
            SecuritySection(
                title = "Auto-Lock",
                icon = Icons.Default.Lock
            ) {
                SwitchWithDescription(
                    title = "Enable Auto-Lock",
                    description = "Automatically lock the app after inactivity",
                    checked = autoLockEnabled,
                    onCheckedChange = { viewModel.setAutoLockEnabled(it) }
                )
                
                if (autoLockEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    AutoLockTimeoutSelector(
                        selectedTimeout = autoLockTimeout,
                        onTimeoutSelected = { viewModel.setAutoLockTimeout(it) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Security Info
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Security Status",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = buildString {
                            append("• Biometric: ${if (isDeviceSecure) "Available" else "Not Configured"}\n")
                            append("• Screenshot Protection: ${if (preventScreenshots) "Enabled" else "Disabled"}\n")
                            append("• Auto-Lock: ${if (autoLockEnabled) "${autoLockTimeout.displayName}" else "Disabled"}")
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SecuritySection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun BiometricToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun SwitchWithDescription(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun AutoLockTimeoutSelector(
    selectedTimeout: SecuritySettingsViewModel.AutoLockTimeout,
    onTimeoutSelected: (SecuritySettingsViewModel.AutoLockTimeout) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Lock After Inactivity",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SecuritySettingsViewModel.AutoLockTimeout.entries.forEach { timeout ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeout.displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    RadioButton(
                        selected = selectedTimeout == timeout,
                        onClick = { onTimeoutSelected(timeout) }
                    )
                }
            }
        }
    }
}