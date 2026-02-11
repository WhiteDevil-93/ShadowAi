package com.shadowai.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.shadowai.core.ProviderId
import com.shadowai.hotswapping.ProviderConfig

/**
 * UI editor for provider hot-swapping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotSwapScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HotSwapViewModel = hiltViewModel()
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var editorState by remember { mutableStateOf<ProviderConfigEditorState?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Provider Hot Swap") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { editorState = ProviderConfigEditorState.createNew() }) {
                        Icon(Icons.Default.Add, contentDescription = "Add provider")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::createBackup) {
                    Text("Backup")
                }
                Button(onClick = viewModel::restoreLatestBackup) {
                    Text("Restore Latest")
                }
            }

            statusMessage?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodySmall)
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(snapshot.providers, key = { it.providerId.name }) { provider ->
                    ProviderConfigRow(
                        config = provider,
                        onToggle = { enabled -> viewModel.toggleProvider(provider.providerId, enabled) },
                        onEdit = { editorState = ProviderConfigEditorState.fromConfig(provider) },
                        onDelete = { viewModel.removeProvider(provider.providerId) }
                    )
                }
            }
        }
    }

    editorState?.let { state ->
        ProviderConfigEditorDialog(
            state = state,
            onDismiss = { editorState = null },
            onSave = { config ->
                viewModel.saveProvider(config)
                editorState = null
            }
        )
    }
}

@Composable
private fun ProviderConfigRow(
    config: ProviderConfig,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = config.name, style = MaterialTheme.typography.titleSmall)
                Text(text = config.providerId.getDisplayName(), style = MaterialTheme.typography.bodySmall)
                if (config.baseUrl.isNotBlank()) {
                    Text(text = config.baseUrl, style = MaterialTheme.typography.bodySmall)
                }
            }
            Switch(checked = config.isEnabled, onCheckedChange = onToggle)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Edit")
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete")
            }
        }
    }
}

private data class ProviderConfigEditorState(
    val providerId: ProviderId,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val modelId: String,
    val isEnabled: Boolean,
    val isLocal: Boolean
) {
    companion object {
        fun createNew(): ProviderConfigEditorState {
            return ProviderConfigEditorState(
                providerId = ProviderId.OPENAI,
                name = "",
                baseUrl = "https://api.openai.com/v1",
                apiKey = "",
                modelId = "",
                isEnabled = true,
                isLocal = false
            )
        }

        fun fromConfig(config: ProviderConfig): ProviderConfigEditorState {
            return ProviderConfigEditorState(
                providerId = config.providerId,
                name = config.name,
                baseUrl = config.baseUrl,
                apiKey = config.apiKeySecret.orEmpty(),
                modelId = config.modelId.orEmpty(),
                isEnabled = config.isEnabled,
                isLocal = config.isLocal
            )
        }
    }
}

@Composable
private fun ProviderConfigEditorDialog(
    state: ProviderConfigEditorState,
    onDismiss: () -> Unit,
    onSave: (ProviderConfig) -> Unit
) {
    var providerId by remember { mutableStateOf(state.providerId) }
    var name by remember { mutableStateOf(state.name) }
    var baseUrl by remember { mutableStateOf(state.baseUrl) }
    var apiKey by remember { mutableStateOf(state.apiKey) }
    var modelId by remember { mutableStateOf(state.modelId) }
    var isEnabled by remember { mutableStateOf(state.isEnabled) }
    var isLocal by remember { mutableStateOf(state.isLocal) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                onSave(
                    ProviderConfig(
                        providerId = providerId,
                        name = name.trim(),
                        baseUrl = baseUrl.trim(),
                        apiKeySecret = apiKey.trim().ifBlank { null },
                        modelId = modelId.trim().ifBlank { null },
                        isEnabled = isEnabled,
                        isLocal = isLocal
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Provider Configuration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProviderIdDropdown(
                    selected = providerId,
                    onSelected = { providerId = it }
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("Model ID") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Enabled")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = isLocal, onCheckedChange = { isLocal = it })
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Local")
                    }
                }
            }
        }
    )
}

@Composable
private fun ProviderIdDropdown(
    selected: ProviderId,
    onSelected: (ProviderId) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(text = "Provider")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(12.dp)
        ) {
            Text(text = selected.getDisplayName())
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ProviderId.values().forEach { providerId ->
                DropdownMenuItem(
                    text = { Text(providerId.getDisplayName()) },
                    onClick = {
                        onSelected(providerId)
                        expanded = false
                    }
                )
            }
        }
    }
}
