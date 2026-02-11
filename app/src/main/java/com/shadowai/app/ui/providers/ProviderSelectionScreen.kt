package com.shadowai.app.ui.providers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.res.stringResource
import com.shadowai.app.R
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shadowai.app.providers.ModelInfo
import com.shadowai.app.providers.Provider
import com.shadowai.core.ProviderId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelectionScreen(
    onNavigateBack: () -> Unit,
    onNavigateToConfig: (ProviderId) -> Unit, // Keeping for advanced config if needed
    modifier: Modifier = Modifier,
    viewModel: ProviderSelectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "AI Provider Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Text(
                        text = "Configure your AI providers by adding their API keys.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Group providers by type or just list them
                // Prioritize Cloud providers first as per screenshot
                val sortedProviders = uiState.providers.sortedBy { 
                    if (it.id.isLocal()) 1 else 0 
                }

                uiState.errorMessage?.let { message ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                items(sortedProviders) { provider ->
                    ProviderSettingsCard(
                        provider = provider,
                        inputValue = if (provider.id.isLocal()) provider.baseUrl else "",
                        hasSavedApiKey = uiState.hasApiKey[provider.id] == true,
                        availableModels = uiState.providerModels[provider.id] ?: emptyList(),
                        isLoadingModels = uiState.isLoadingModels[provider.id] == true,
                        isActive = provider.id == uiState.activeProviderId,
                        onApiKeyChange = { key ->
                             if (provider.id.isLocal()) {
                                 viewModel.saveLocalConfig(provider.id, key)
                             } else {
                                 viewModel.saveApiKey(provider.id, key)
                             }
                        },
                        onFetchModels = { viewModel.fetchModels(provider.id) },
                        onAddModel = { modelId -> viewModel.addModelToProvider(provider.id, modelId) },
                        onSelectActive = { viewModel.setActiveProvider(provider.id) },
                        onNavigateToConfig = { onNavigateToConfig(provider.id) },
                        onGetApiKey = {
                            uriHandler.openUri(provider.id.keyPortalUrl())
                        },
                        modifier = Modifier.animateItem()
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ProviderSettingsCard(
    provider: Provider,
    inputValue: String,
    hasSavedApiKey: Boolean,
    availableModels: List<ModelInfo>,
    isLoadingModels: Boolean,
    isActive: Boolean,
    onApiKeyChange: (String) -> Unit,
    onFetchModels: () -> Unit,
    onAddModel: (String) -> Unit,
    onSelectActive: () -> Unit,
    onNavigateToConfig: () -> Unit,
    onGetApiKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var currentInput by remember(inputValue) { mutableStateOf(inputValue) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.1f) 
                             else MaterialTheme.colorScheme.surface
        ),
        border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Name + Active Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioButton(
                        selected = isActive,
                        onClick = onSelectActive
                    )
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )
                }
                
                // Status/Test indicator could go here
            }

            // API Key / Base URL Field
            if (provider.id != ProviderId.LIQUID) {
                OutlinedTextField(
                    value = currentInput,
                    onValueChange = { currentInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (provider.id.isLocal()) "Base URL" else "API Key") },
                    placeholder = { 
                        val placeholderText = when (provider.id) {
                            ProviderId.OPENAI -> "sk-..."
                            ProviderId.ANTHROPIC -> "sk-ant-..."
                            ProviderId.GEMINI -> "AIza..."
                            ProviderId.MISTRAL -> "Key from console.mistral.ai"
                            ProviderId.GROQ -> "gsk_..."
                            ProviderId.NOVELAI -> "pst-..."
                            ProviderId.PIXAI -> "API Key"
                            else -> if (provider.id.isLocal()) "http://localhost:..." else "API Key"
                        }
                        Text(placeholderText) 
                    },
                    singleLine = true,
                    visualTransformation = if (isKeyVisible || provider.id.isLocal()) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        if (!provider.id.isLocal()) {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isKeyVisible) "Hide Key" else "Show Key"
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (isActive)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = if (isActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            onApiKeyChange(currentInput)
                            if (!provider.id.isLocal()) {
                                currentInput = ""
                            }
                        },
                        enabled = if (provider.id.isLocal()) {
                            currentInput.isNotBlank() && currentInput != inputValue
                        } else {
                            currentInput.isNotBlank()
                        }
                    ) {
                        Text("Save")
                    }
                }
            }

            if (provider.id == ProviderId.LIQUID) {
                Text(
                    text = "On-device inference using .gguf models",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onNavigateToConfig,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Configure Local Models")
                }
            }

            // "Get Key" Link
            if (!provider.id.isLocal() && !hasSavedApiKey && currentInput.isBlank()) {
                Text(
                    text = "Get ${provider.name} Key",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onGetApiKey() }
                )
            }
            
            // PixAI Specific Configuration
            if (provider.id == ProviderId.PIXAI) {
                HorizontalDivider()
                PixAiConfig()
            }

            // Model Selection
            HorizontalDivider()
            
            Text("Models", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            // Display currently selected models (from provider object)
            // Assuming provider.customModels or provider.selectedModels holds the list.
            // provider.models is the static list.
            val selectedModels = provider.selectedModels?.takeIf { it.isNotEmpty() }
                ?: provider.customModels
                ?: emptyList()
            if (selectedModels.isNotEmpty()) {
                selectedModels.forEach { modelId ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(modelId, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Model dropdown selector + refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.provider_settings_select_model))
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = isExpanded,
                        onDismissRequest = { isExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .heightIn(max = 300.dp)
                    ) {
                        if (availableModels.isEmpty() && !isLoadingModels) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.admin_no_models_found)) },
                                onClick = {
                                    onFetchModels()
                                    isExpanded = false
                                }
                            )
                        } else {
                            availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(model.id, style = MaterialTheme.typography.bodyMedium)
                                            if (model.displayName != null && model.displayName != model.id) {
                                                Text(model.displayName!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    },
                                    onClick = {
                                        onAddModel(model.id)
                                        isExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Refresh/Fetch Button
                IconButton(
                    onClick = onFetchModels,
                    enabled = !isLoadingModels && (hasSavedApiKey || currentInput.isNotBlank() || provider.id.isLocal())
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Fetch Models")
                }
            }
        }
    }
}

private fun ProviderId.keyPortalUrl(): String {
    return when (this) {
        ProviderId.OPENAI -> "https://platform.openai.com/api-keys"
        ProviderId.OPENROUTER -> "https://openrouter.ai/keys"
        ProviderId.ANTHROPIC -> "https://console.anthropic.com/settings/keys"
        ProviderId.GEMINI -> "https://aistudio.google.com/app/apikey"
        ProviderId.GROQ -> "https://console.groq.com/keys"
        ProviderId.MISTRAL -> "https://console.mistral.ai/api-keys/"
        ProviderId.DEEPSEEK -> "https://platform.deepseek.com/api_keys"
        ProviderId.XAI -> "https://console.x.ai/"
        ProviderId.COHERE -> "https://dashboard.cohere.com/api-keys"
        ProviderId.SILICON_FLOW -> "https://cloud.siliconflow.cn/account/ak"
        ProviderId.NOVITA -> "https://novita.ai/console/api-keys"
        ProviderId.PIXAI -> "https://pixai.art/settings/api"
        ProviderId.NOVELAI -> "https://novelai.net/"
// ProviderId.HUGGING_FACE temporarily disabled
        // ProviderId.HUGGING_FACE -> "https://huggingface.co/settings/tokens"
        ProviderId.ATLASCLOUD, ProviderId.SIRAY -> "https://shadowai.app/providers"
        else -> "https://shadowai.app/providers"
    }
}

private fun ProviderId.isLocal(): Boolean {
    return this == ProviderId.LOCAL_TEXT || 
           this == ProviderId.LOCAL_IMAGE || 
           this == ProviderId.LIQUID || 
           this == ProviderId.OLLAMA_CLOUD
}

@Composable
private fun PixAiConfig() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = "PixAI Credits",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Syncing credits...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
