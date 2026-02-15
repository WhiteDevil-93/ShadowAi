package com.shadowai.app.ui.chat.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.shadowai.app.ui.theme.bg_0
import com.shadowai.app.ui.theme.bg_1
import com.shadowai.app.ui.theme.emerald_core
import com.shadowai.app.ui.theme.text_primary
import com.shadowai.app.ui.theme.text_secondary

/**
 * Export format option with display info
 */
private data class ExportOptionData(
    val format: ExportFormat,
    val title: String,
    val description: String,
    val color: Color
)

/**
 * Dialog for selecting export format and initiating conversation export.
 */
@Composable
fun ExportDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onExport: (format: ExportFormat, shareAfter: Boolean) -> Unit,
    isLoading: Boolean = false,
    exportResult: ExportResult? = null
) {
    if (!isVisible) return

    var selectedFormat by remember { mutableStateOf<ExportFormat?>(null) }
    var shareAfterExport by remember { mutableStateOf(true) }
    var showSuccessMessage by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        selectedFormat = null
        showSuccessMessage = false
    }

    LaunchedEffect(exportResult) {
        if (exportResult is ExportResult.Success) {
            showSuccessMessage = true
        }
    }

    Dialog(onDismissRequest = {
        if (!isLoading) onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = bg_1
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = "Export Conversation",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = text_primary
                )

                Text(
                    text = "Choose a format to export your conversation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = text_secondary
                )

                if (!showSuccessMessage) {
                    ExportOptionEntries.forEach { option ->
                        ExportOptionCard(
                            option = option,
                            isSelected = selectedFormat == option.format,
                            onClick = { selectedFormat = option.format },
                            enabled = !isLoading
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = !isLoading) {
                            shareAfterExport = !shareAfterExport
                        }
                    ) {
                        Checkbox(
                            checked = shareAfterExport,
                            onCheckedChange = { shareAfterExport = it },
                            enabled = !isLoading,
                            colors = CheckboxDefaults.colors(
                                checkedColor = emerald_core
                            )
                        )
                        Text(
                            text = "Share after export",
                            style = MaterialTheme.typography.bodyMedium,
                            color = text_primary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                // Status / Success message
                AnimatedVisibility(
                    visible = showSuccessMessage && exportResult is ExportResult.Success,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ExportSuccessCard(
                        fileName = (exportResult as? ExportResult.Success)?.fileName ?: "",
                        format = (exportResult as? ExportResult.Success)?.format,
                        onShare = {
                            selectedFormat?.let { format ->
                                onExport(format, true)
                            }
                        },
                        onDismiss = onDismiss
                    )
                }

                // Error message
                if (exportResult is ExportResult.Error) {
                    ErrorCard(message = exportResult.message)
                }

                // Action buttons
                if (!showSuccessMessage) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            enabled = !isLoading
                        ) {
                            Text("Cancel")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                selectedFormat?.let { format ->
                                    onExport(format, shareAfterExport)
                                }
                            },
                            enabled = selectedFormat != null && !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = emerald_core,
                                disabledContainerColor = bg_0
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = text_primary
                                )
                            } else {
                                Text("Export")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportOptionCard(
    option: ExportOptionData,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean
) {
    val backgroundColor = if (isSelected) {
        emerald_core.copy(alpha = 0.15f)
    } else {
        bg_0
    }

    val borderColor = if (isSelected) {
        emerald_core
    } else {
        Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 0.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                FormatIcon(color = option.color, text = getFormatIconText(option.format))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = text_primary
                )
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = text_secondary
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = emerald_core,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                RadioButton(
                    selected = false,
                    onClick = null,
                    enabled = enabled,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = emerald_core,
                        unselectedColor = text_secondary
                    )
                )
            }
        }
    }
}

private fun getFormatIconText(format: ExportFormat): String {
    return when (format) {
        ExportFormat.PDF -> "PDF"
        ExportFormat.MARKDOWN -> "MD"
        ExportFormat.JSON -> "{ }"
        ExportFormat.TXT -> "TXT"
        ExportFormat.ZIP -> "ZIP"
    }
}

@Composable
private fun FormatIcon(color: Color, text: String) {
    Surface(
        color = color,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
private fun ExportSuccessCard(
    fileName: String,
    format: ExportFormat?,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = emerald_core.copy(alpha = 0.1f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = emerald_core.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = emerald_core
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Export Successful!",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = emerald_core
                )
            }

            Text(
                text = "Saved to Downloads/ShadowAI as:",
                style = MaterialTheme.typography.bodyMedium,
                color = text_secondary
            )

            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = text_primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onShare,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = emerald_core
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

// Export options data
private val ExportOptionEntries = listOf(
    ExportOptionData(
        format = ExportFormat.PDF,
        title = "PDF Document",
        description = "Formatted document for sharing and printing",
        color = Color(0xFFDC2626)
    ),
    ExportOptionData(
        format = ExportFormat.MARKDOWN,
        title = "Markdown",
        description = "Plain text with formatting preserved",
        color = Color(0xFF2563EB)
    ),
    ExportOptionData(
        format = ExportFormat.JSON,
        title = "JSON",
        description = "Structured data for backup or import",
        color = Color(0xFF16A34A)
    ),
    ExportOptionData(
        format = ExportFormat.TXT,
        title = "Plain Text",
        description = "Simple text format for quick sharing",
        color = Color(0xFF6B7280)
    ),
    ExportOptionData(
        format = ExportFormat.ZIP,
        title = "ZIP Archive",
        description = "Bundle multiple conversations into one file",
        color = Color(0xFFF59E0B)
    )
)
