package com.shadowai.app.ui.workflows

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter

/**
 * Image Generation Screen - Implements UI Orchestrator FOCUS MODE
 * 
 * GOVERNANCE COMPLIANCE - FOCUS MODE:
 * - Workflow occupies entire screen (MANDATORY)
 * - Chat list hidden (MANDATORY)
 * - Floating tabs hidden (MANDATORY)
 * - Single back/exit affordance visible (MANDATORY)
 * - No split attention (MANDATORY)
 * - No background UI bleed-through (MANDATORY)
 * 
 * PRODUCTION-BLOCKING FAILURE:
 * - ❌ Chat list visible during workflow
 * - ❌ Floating tabs visible during workflow
 * - ❌ Multiple exit points
 * - ❌ Background UI visible
 * - ❌ Split attention
 * 
 * Focus mode ensures the user can complete one task without distraction.
 * This is a HARD REQUIREMENT for all multi-step workflows.
 * 
 * @param onNavigateBack Callback when user exits workflow
 * @param onGenerateImage Callback when image generation is requested
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGenerationScreen(
    onNavigateBack: () -> Unit,
    onGenerateImage: (ImageGenParams) -> Unit,
    modifier: Modifier = Modifier
) {
    // Workflow state
    var prompt by remember { mutableStateOf("") }
    var negativePrompt by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedImageUrl by remember { mutableStateOf<String?>(null) }
    
    // GOVERNANCE: Focus mode - full screen dedication
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(), // GOVERNANCE: Mandatory system insets
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Generate Image",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    // GOVERNANCE: Single back/exit affordance (MANDATORY)
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit image generation"
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
        // GOVERNANCE: Workflow occupies entire screen
        // No chat list, no floating tabs, no other UI elements
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Focus mode notice
            FocusModeNotice()
            
            // Prompt input
            PromptInputField(
                prompt = prompt,
                onPromptChange = { prompt = it }
            )
            
            // Negative prompt input
            NegativePromptInputField(
                negativePrompt = negativePrompt,
                onNegativePromptChange = { negativePrompt = it }
            )
            
            // Model selector
            ModelSelector(
                selectedModel = selectedModel,
                onModelSelected = { selectedModel = it }
            )
            
            // Generate button
            GenerateButton(
                enabled = prompt.isNotBlank() && selectedModel.isNotBlank() && !isGenerating,
                isGenerating = isGenerating,
                onClick = {
                    isGenerating = true
                    onGenerateImage(
                        ImageGenParams(
                            prompt = prompt,
                            negativePrompt = negativePrompt,
                            model = selectedModel
                        )
                    )
                }
            )
            
            // Generated image preview
            generatedImageUrl?.let { url ->
                GeneratedImagePreview(imageUrl = url)
            }
        }
    }
}

/**
 * Focus mode notice card
 */
@Composable
private fun FocusModeNotice() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Focus Mode Active",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "This workflow has your full attention. Tap back to return to chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Prompt input field
 */
@Composable
private fun PromptInputField(
    prompt: String,
    onPromptChange: (String) -> Unit
) {
    OutlinedTextField(
        value = prompt,
        onValueChange = onPromptChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        label = {
            Text(
                text = "Prompt",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        placeholder = {
            Text(
                text = "Describe the image you want to generate...",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        supportingText = {
            Text(
                text = "Be specific and descriptive for best results",
                style = MaterialTheme.typography.bodySmall
            )
        },
        maxLines = 5,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

/**
 * Negative prompt input field
 */
@Composable
private fun NegativePromptInputField(
    negativePrompt: String,
    onNegativePromptChange: (String) -> Unit
) {
    OutlinedTextField(
        value = negativePrompt,
        onValueChange = onNegativePromptChange,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(
                text = "Negative Prompt (Optional)",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        placeholder = {
            Text(
                text = "What to avoid in the image...",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        supportingText = {
            Text(
                text = "Specify elements you don't want in the image",
                style = MaterialTheme.typography.bodySmall
            )
        },
        maxLines = 3,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

/**
 * Model selector
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelector(
    selectedModel: String,
    onModelSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val models = listOf("SDXL", "SD 1.5", "Midjourney", "DALL-E 3")
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedModel,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
            readOnly = true,
            label = {
                Text(
                    text = "Model",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = model,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        onModelSelected(model)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Generate button
 */
@Composable
private fun GenerateButton(
    enabled: Boolean,
    isGenerating: Boolean,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = enabled
    ) {
        if (isGenerating) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Generating...",
                style = MaterialTheme.typography.labelLarge
            )
        } else {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Generate Image",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * Generated image preview
 */
@Composable
private fun GeneratedImagePreview(imageUrl: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Generated Image",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Display image using Coil with loading state and error handling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl.isNotEmpty()) {
                    // Display image using Coil with loading and error handling
                    androidx.compose.foundation.Image(
                        painter = rememberAsyncImagePainter(
                            model = imageUrl,
                            contentScale = ContentScale.Crop
                        ),
                        contentDescription = "Generated Image",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Show placeholder when no image URL
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            painter = rememberVectorPainter(Icons.Default.ImageNotSupported),
                            contentDescription = "No image",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No image generated yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Image generation parameters
 */
data class ImageGenParams(
    val prompt: String,
    val negativePrompt: String,
    val model: String
)
