package com.shadowai.app.ui.providers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.shadowai.app.R
import com.shadowai.core.ProviderId

/**
 * Provider Configuration Screen - Implements UI Orchestrator governance
 *
 * GOVERNANCE COMPLIANCE:
 * - Cloud providers: API key field, Save, Test, Status, Model selection
 * - Local providers: Runtime status, Host, Port, Test, Models (NO API KEY)
 * - API keys use PasswordVisualTransformation
 * - Keys stored in EncryptedSharedPreferences only
 * - Material 3 compliance
 * - System insets handling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderConfigScreen(
    providerId: ProviderId,
    onNavigateBack: () -> Unit,
    onSaveConfig: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProviderConfigViewModel = hiltViewModel()
) {
    val isCloudProvider = providerId.isCloudProvider()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(providerId) {
        viewModel.loadConfig(providerId)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Configure ${providerId.getDisplayName()}",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isCloudProvider) {
                CloudProviderConfigSection(
                    providerId = providerId,
                    uiState = uiState,
                    hasSavedApiKey = uiState.hasSavedApiKey,
                    onApiKeyChange = { viewModel.updateApiKey(providerId, it) },
                    onSave = { apiKey ->
                        viewModel.saveConfig(providerId, apiKey)
                        onSaveConfig()
                    },
                    onTest = { apiKey -> viewModel.testApiKey(providerId, apiKey) }
                )
            } else {
                LocalRuntimeConfig(
                    providerId = providerId,
                    initialConfig = uiState.provider?.baseUrl ?: "",
                    installedModels = uiState.availableModels.map { it.displayName },
                    onSaveConfig = { config ->
                        viewModel.updateProviderBaseUrl(providerId, config)
                        onSaveConfig()
                    },
                    onTreeUriSet = { treeUri ->
                        viewModel.setModelTreeUri(providerId, treeUri)
                    },
                    onModelsImported = {
                        viewModel.discoverModels(providerId)
                    }
                )
            }

            // Model Selection section
            if (uiState.availableModels.isNotEmpty() || uiState.isLoadingModels) {
                ModelSelectionSection(
                    models = uiState.availableModels,
                    selectedModelIds = uiState.selectedModelIds,
                    isLoading = uiState.isLoadingModels,
                    onModelToggle = { modelId -> viewModel.toggleModelSelection(providerId, modelId) },
                    onRefresh = { viewModel.discoverModels(providerId) }
                )
            }
        }
    }
}

private fun ProviderId.isCloudProvider(): Boolean {
    return when (this) {
        ProviderId.LOCAL_IMAGE,
        ProviderId.LOCAL_TEXT,
        ProviderId.LIQUID,
        ProviderId.FLUX -> false
        else -> true
    }
}

// Using ProviderId.getDisplayName() method from enum directly

@Composable
private fun CloudProviderConfigSection(
    providerId: ProviderId,
    uiState: ProviderConfigUiState,
    hasSavedApiKey: Boolean,
    onApiKeyChange: (String) -> Unit,
    onSave: (String) -> Unit,
    onTest: (String) -> Unit
) {
    var apiKey by rememberSaveable(providerId) { mutableStateOf("") }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.wizard_title_api_config),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = stringResource(R.string.wizard_desc_api_config),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (hasSavedApiKey && apiKey.isBlank()) {
                Text(
                    text = "A key is already saved. Enter a new key only if you want to replace it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { newValue ->
                    apiKey = newValue
                    onApiKeyChange(newValue)
                },
                label = { Text(stringResource(R.string.provider_wizard_api_key_hint)) },
                placeholder = { Text("sk-...") },
                singleLine = true,
                visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                        Icon(
                            imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isApiKeyVisible) stringResource(R.string.content_desc_toggle_password) else stringResource(R.string.content_desc_toggle_password)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Test result indicator
            uiState.testSuccess?.let { success ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (success)
                            MaterialTheme.colorScheme.tertiaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (success)
                                MaterialTheme.colorScheme.onTertiaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = if (success) stringResource(R.string.wizard_message_connection_passed) else (uiState.errorMessage ?: stringResource(R.string.wizard_message_connection_failed)),
                            color = if (success)
                                MaterialTheme.colorScheme.onTertiaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                OutlinedButton(
                    onClick = { onTest(apiKey) },
                    enabled = apiKey.isNotBlank() && !uiState.isTesting
                ) {
                    if (uiState.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.provider_wizard_test_button))
                }

                Button(
                    onClick = { onSave(apiKey) },
                    enabled = apiKey.isNotBlank()
                ) {
                    Text(stringResource(R.string.admin_button_save_key))
                }
            }
        }
    }
}

@Composable
private fun ModelSelectionSection(
    models: List<com.shadowai.app.providers.ModelInfo>,
    selectedModelIds: List<String>,
    isLoading: Boolean,
    onModelToggle: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
            Text(
                text = stringResource(R.string.wizard_title_model_selection),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onRefresh, enabled = !isLoading) {
                Text(if (isLoading) stringResource(R.string.wizard_message_testing_connection) else stringResource(R.string.admin_button_scan_storage))
            }
            }

            Text(
                text = "Select the model to use with this provider.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isLoading) {
                LinearProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            models.forEach { model ->
                val isSelected = selectedModelIds.contains(model.id)
                Surface(
                    onClick = { onModelToggle(model.id) },
                    shape = MaterialTheme.shapes.small,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            val descriptionText = model.description
                            Text(
                                text = model.displayName ?: model.id,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (!descriptionText.isNullOrBlank()) {
                                Text(
                                    text = descriptionText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
