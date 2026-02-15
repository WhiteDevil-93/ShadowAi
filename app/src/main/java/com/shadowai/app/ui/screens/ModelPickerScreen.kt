package com.shadowai.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.app.R
import com.shadowai.app.ai.LocalInferenceManager
import com.shadowai.app.ai.MemoryConstants
import com.shadowai.app.ai.QuantizationHelper
import com.shadowai.core.ProviderId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Model Info with quantization details for UI display.
 */
data class ModelDisplayInfo(
    val name: String,
    val quantizationType: String,
    val fileSize: Long,
    val estimatedRamMB: Long,
    val isRecommended: Boolean,
    val warning: String?,
    val canFit: Boolean,
    val file: File? = null
)

/**
 * ViewModel for ModelPicker with QuantizationHelper integration.
 */
@HiltViewModel
class ModelPickerViewModel @Inject constructor(
    private val localInferenceManager: LocalInferenceManager
) : ViewModel() {

    private val _models = MutableStateFlow<List<ModelDisplayInfo>>(emptyList())
    val models: StateFlow<List<ModelDisplayInfo>> = _models.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _availableRamMb = MutableStateFlow(0L)
    val availableRamMb: StateFlow<Long> = _availableRamMb.asStateFlow()

    private val _selectedModel = MutableStateFlow<String?>(null)
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Load and filter models from the given directory.
     * If modelDir is blank, uses the default model directory from LocalInferenceManager.
     */
    fun loadModels(modelDir: String, systemRamMB: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _availableRamMb.value = systemRamMB

            // Use provided directory or default from LocalInferenceManager
            val dir = if (modelDir.isNotBlank()) {
                File(modelDir)
            } else {
                localInferenceManager.getModelDirectory()
            }

            if (dir.exists() && dir.isDirectory) {
                val ggufFiles = dir.listFiles { file ->
                    file.isFile && file.name.endsWith(".gguf", ignoreCase = true)
                } ?: emptyArray()

                val modelInfos = QuantizationHelper.prioritizeModels(ggufFiles.toList()).map { info ->
                    val canFit = QuantizationHelper.fitsInMemory(info, systemRamMB)

                    ModelDisplayInfo(
                        name = info.file.name,
                        quantizationType = info.quantization.displayName,
                        fileSize = info.fileSize,
                        estimatedRamMB = info.estimatedRamMB,
                        isRecommended = info.quantization.isRecommended,
                        warning = info.quantization.warning,
                        canFit = canFit,
                        file = info.file
                    )
                }

                _models.value = modelInfos
            } else {
                _models.value = emptyList()
            }

            _isLoading.value = false
        }
    }

    /**
     * Select a model.
     */
    fun selectModel(modelName: String) {
        _selectedModel.value = modelName
    }

    /**
     * Update search query.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Get filtered models based on search query.
     */
    fun getFilteredModels(): List<ModelDisplayInfo> {
        val query = _searchQuery.value.lowercase()
        return if (query.isBlank()) {
            _models.value
        } else {
            _models.value.filter { it.name.lowercase().contains(query) }
        }
    }

    /**
     * Get color for quantization badge.
     */
    fun getQuantizationColor(type: String): androidx.compose.ui.graphics.Color {
        return when (type.uppercase()) {
            "Q4_0", "Q4_K_S", "Q4_K_M" -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Green
            "Q5_0", "Q5_1", "Q5_K_S", "Q5_K_M" -> androidx.compose.ui.graphics.Color(0xFF2196F3) // Blue
            "Q3_K_S", "Q3_K_M", "Q3_K_L" -> androidx.compose.ui.graphics.Color(0xFFFF9800) // Orange
            "Q6_K" -> androidx.compose.ui.graphics.Color(0xFF9C27B0) // Purple
            "Q8_0", "Q8_K" -> androidx.compose.ui.graphics.Color(0xFFFF5722) // Deep orange
            "F16", "F32" -> androidx.compose.ui.graphics.Color(0xFFF44336) // Red
            "Q2_K" -> androidx.compose.ui.graphics.Color(0xFF795548) // Brown
            else -> androidx.compose.ui.graphics.Color.Gray
        }
    }
}

/**
 * ModelPickerScreen - Displays models with quantization badges and memory warnings.
 *
 * Features:
 * - Quantization type badges for each model
 * - Memory usage estimation with warnings
 * - Color-coded quantization types
 * - Search functionality
 * - Memory availability indicators
 *
 * @param providerId The provider for model selection
 * @param modelDir Directory containing .gguf model files
 * @param systemRamMB Available system RAM in MB
 * @param onModelSelected Callback when a model is selected
 * @param onNavigateBack Callback to navigate back
 * @param viewModel ViewModel for model data
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerScreen(
    providerId: ProviderId,
    modelDir: String,
    systemRamMB: Long = 4096,
    onModelSelected: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    viewModel: ModelPickerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val models by viewModel.models.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val availableRam by viewModel.availableRamMb.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // Load models on composition
    LaunchedEffect(modelDir) {
        viewModel.loadModels(modelDir, systemRamMB)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Select Model")
                        Text(
                            text = "Available: ${formatMemorySize(availableRam * 1024 * 1024)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search models...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                singleLine = true
            )

            // Filtered models list
            val filteredModels = viewModel.getFilteredModels()

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredModels.isEmpty()) {
                EmptyModelsState(
                    showNoModelsFound = searchQuery.isNotBlank(),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredModels, key = { it.name }) { model ->
                        ModelCard(
                            model = model,
                            isSelected = selectedModel == model.name,
                            onClick = {
                                viewModel.selectModel(model.name)
                                onModelSelected(model.name)
                            },
                            quantizationColor = viewModel.getQuantizationColor(model.quantizationType)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelDisplayInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    quantizationColor: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row: Name and badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Quantization badge
                    QuantizationBadge(
                        type = model.quantizationType,
                        color = quantizationColor
                    )

                    // Recommended badge
                    if (model.isRecommended) {
                        RecommendedBadge()
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Memory info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "File: ${formatMemorySize(model.fileSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Est. RAM: ${formatMemorySize(model.estimatedRamMB * 1024 * 1024)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Memory warning
                MemoryIndicator(
                    canFit = model.canFit,
                    warning = model.warning
                )
            }

            // Warning text if present
            model.warning?.let { warning ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = warning,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun QuantizationBadge(
    type: String,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = type,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun RecommendedBadge() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Recommended",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun MemoryIndicator(
    canFit: Boolean,
    warning: String?
) {
    val hasWarning = warning != null || !canFit

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasWarning) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Memory warning",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (!canFit) "May not fit" else "High memory",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Fits in memory",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Fits",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun EmptyModelsState(
    showNoModelsFound: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (showNoModelsFound) "No models match your search" else "No Models Found",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (showNoModelsFound) {
                "Try a different search term"
            } else {
                "Add .gguf model files to your models directory"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/**
 * Format bytes as human-readable string.
 */
private fun formatMemorySize(bytes: Long): String {
    val mb = bytes / (1024 * 1024)
    val gb = mb / 1024.0
    return if (gb >= 1.0) {
        String.format("%.1f GB", gb)
    } else {
        String.format("%d MB", mb)
    }
}

/**
 * Model card preview for InstalledModelsCard integration.
 */
@Composable
fun ModelDetailRow(
    modelName: String,
    fileSizeBytes: Long,
    modifier: Modifier = Modifier
) {
    val quantType = remember(modelName) {
        QuantizationHelper.detectQuantization(modelName)
    }

    val color = remember(quantType) {
        when (quantType.name) {
            "Q4_0", "Q4_K_S", "Q4_K_M" -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
            "Q5_0", "Q5_1", "Q5_K_S", "Q5_K_M" -> androidx.compose.ui.graphics.Color(0xFF2196F3)
            "Q3_K_S", "Q3_K_M", "Q3_K_L" -> androidx.compose.ui.graphics.Color(0xFFFF9800)
            "Q6_K" -> androidx.compose.ui.graphics.Color(0xFF9C27B0)
            "Q8_0", "Q8_K" -> androidx.compose.ui.graphics.Color(0xFFFF5722)
            "F16", "F32" -> androidx.compose.ui.graphics.Color(0xFFF44336)
            "Q2_K" -> androidx.compose.ui.graphics.Color(0xFF795548)
            else -> androidx.compose.ui.graphics.Color.Gray
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quantization badge
                    if (quantType != QuantizationHelper.QuantizationType.UNKNOWN) {
                        Surface(
                            color = color.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = quantType.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // File size
                    Text(
                        text = formatMemorySize(fileSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Warning icon if applicable
        if (quantType.warning != null) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = quantType.warning,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
