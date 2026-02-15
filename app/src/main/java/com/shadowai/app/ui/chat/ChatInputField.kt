package com.shadowai.app.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.shadowai.app.ui.theme.*

/**
 * Chat Input Field - Implements UI Orchestrator governance
 * Supports multimodal input (Text + Image) via Photo Picker.
 * Also handles content shared from other apps via Intent Share Sheet.
 *
 * @param initialText Pre-filled text from Intent Share Sheet
 * @param initialImageUris Pre-filled images from Intent Share Sheet
 * @param onSharedContentConsumed Called when shared content has been used
 */
@Composable
fun ChatInputField(
    onSendMessage: (String, Uri?) -> Unit,
    mode: ChatMode = ChatMode.CHAT,
    initialText: String? = null,
    initialImageUris: List<Uri> = emptyList(),
    onSharedContentConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(initialText ?: "") }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var sharedImageUris by remember { mutableStateOf<List<Uri>>(initialImageUris) }
    
    // Update text when initialText changes (from share sheet)
    LaunchedEffect(initialText) {
        if (initialText != null && text.isBlank()) {
            text = initialText
            onSharedContentConsumed()
        }
    }
    
    // Update images when initialImageUris changes (from share sheet)
    LaunchedEffect(initialImageUris) {
        if (initialImageUris.isNotEmpty() && sharedImageUris.isEmpty()) {
            // Set the first image as attached and rest as shared
            attachedImageUri = initialImageUris.firstOrNull()
            sharedImageUris = if (initialImageUris.size > 1) {
                initialImageUris.drop(1)
            } else {
                emptyList()
            }
            onSharedContentConsumed()
        }
    }

    // Photo Picker Launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            attachedImageUri = uri
        }
    }

    val placeholderText = when (mode) {
        ChatMode.CHAT -> "Ask ShadowAi..."
        ChatMode.WRITE -> "Describe what you want to write..."
        ChatMode.CALL -> "Who should be called and why?"
        ChatMode.IMAGE -> "Describe the image to generate..."
    }

    Surface(
        modifier = modifier,
        color = bg_3.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, stroke_subtle),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column {
            // Attached Images Preview Row (supports multiple from share sheet)
            val hasAttachedImages = attachedImageUri != null || sharedImageUris.isNotEmpty()
            AnimatedVisibility(
                visible = hasAttachedImages,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 12.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Primary attached image
                    attachedImageUri?.let { uri ->
                        ImagePreviewItem(
                            uri = uri,
                            onRemove = { attachedImageUri = null },
                            label = "Attached"
                        )
                    }
                    // Additional shared images
                    sharedImageUris.take(3).forEachIndexed { index, uri ->
                        ImagePreviewItem(
                            uri = uri,
                            onRemove = {
                                sharedImageUris = sharedImageUris.toMutableList().apply {
                                    removeAt(index)
                                }
                            },
                            label = if (sharedImageUris.size > 1) "${index + 2}" else null
                        )
                    }
                    // Show count badge if more than 4 images
                    if (sharedImageUris.size > 3) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg_2),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+${sharedImageUris.size - 3}",
                                style = MaterialTheme.typography.titleMedium,
                                color = emerald_core
                            )
                        }
                    }
                }
            }
            
            // Shared content indicator
            if (initialText != null && text.isNotBlank()) {
                Text(
                    text = "📋 Shared from another app",
                    style = MaterialTheme.typography.bodySmall,
                    color = emerald_core,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Attach Button (Only in Chat Mode)
                if (mode == ChatMode.CHAT) {
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = emerald_core)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Attach Image"
                        )
                    }
                }

                // Text input field
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = placeholderText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = text_muted
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = text_primary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = emerald_core,
                        unfocusedBorderColor = stroke_normal,
                        cursorColor = emerald_core,
                        focusedContainerColor = bg_1,
                        unfocusedContainerColor = bg_1
                    ),
                    shape = RoundedCornerShape(16.dp),
                    maxLines = 5
                )

                // Send button
                FilledIconButton(
                    onClick = {
                        if (text.isNotBlank() || attachedImageUri != null) {
                            onSendMessage(text, attachedImageUri)
                            text = ""
                            attachedImageUri = null
                        }
                    },
                    enabled = text.isNotBlank() || attachedImageUri != null,
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = emerald_core,
                        contentColor = Color.Black,
                        disabledContainerColor = bg_2,
                        disabledContentColor = text_muted
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Preview item for attached/shared images
 */
@Composable
private fun ImagePreviewItem(
    uri: Uri,
    onRemove: () -> Unit,
    label: String? = null
) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg_2)
    ) {
        Image(
            painter = rememberAsyncImagePainter(uri),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Remove button
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }

        // Label badge (if provided)
        label?.let {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(emerald_core, CircleShape)
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black
                )
            }
        }
    }
}
