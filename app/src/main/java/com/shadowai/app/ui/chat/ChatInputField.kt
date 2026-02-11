package com.shadowai.app.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
 */
@Composable
fun ChatInputField(
    onSendMessage: (String, Uri?) -> Unit,
    mode: ChatMode = ChatMode.CHAT,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }

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
            // Attached Image Preview
            AnimatedVisibility(
                visible = attachedImageUri != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)) {
                    attachedImageUri?.let { uri ->
                        Box {
                            Image(
                                painter = rememberAsyncImagePainter(uri),
                                contentDescription = "Attached Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            // Remove button
                            IconButton(
                                onClick = { attachedImageUri = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 8.dp, y = (-8).dp)
                                    .size(24.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove image",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
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
