package com.shadowai.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadowai.app.ui.ChatMessage
import com.shadowai.app.ui.theme.*

/**
 * Chat Message Item - Displays a single message with proper visual hierarchy.
 * Updated to use Artifact-aware MessageContent.
 */
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Message content bubble
        val bubbleShape = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = if (message.isUser) 20.dp else 4.dp,
            bottomEnd = if (message.isUser) 4.dp else 20.dp
        )

        Surface(
            modifier = Modifier
                .widthIn(max = 340.dp) // Slightly wider to accommodate images
                .align(if (message.isUser) Alignment.End else Alignment.Start),
            shape = bubbleShape,
            color = if (message.isUser) Color.Transparent else bg_2.copy(alpha = 0.9f),
            border = if (message.isUser) null else androidx.compose.foundation.BorderStroke(1.dp, stroke_subtle),
            tonalElevation = if (message.isUser) 0.dp else 2.dp
        ) {
            val userGradient = androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(emerald_core, emerald_dim)
            )

            Box(
                modifier = if (message.isUser) {
                    Modifier.background(userGradient)
                } else {
                    Modifier
                }
            ) {
                // Content Padding
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    // Render rich content (Text/Markdown + Artifacts/Images)
                    MessageContent(
                        content = message.text,
                        artifact = message.artifact,
                        imageUrl = message.imageUri
                    )
                }
            }
        }

        // Error pill (if message has error)
        if (message.isError) {
            message.error?.let { error ->
                ErrorPill(
                    error = error,
                    onRetry = onRetry,
                    modifier = Modifier.align(if (message.isUser) Alignment.End else Alignment.Start)
                )
            }
        }

        // Timestamp (muted, 15% visual weight)
        Text(
            text = message.timestampString,
            modifier = Modifier
                .align(if (message.isUser) Alignment.End else Alignment.Start)
                .padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

/**
 * Error Pill - Displays errors as inline chips with retry action
 */
@Composable
fun ErrorPill(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AssistChip(
            onClick = { expanded = !expanded },
            label = {
                Text(
                    text = error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error",
                    modifier = Modifier.size(16.dp)
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        modifier = Modifier.size(16.dp)
                    )
                }
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                labelColor = MaterialTheme.colorScheme.onErrorContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        )

        if (expanded) {
            Text(
                text = error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
