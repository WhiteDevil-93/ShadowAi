package com.shadowai.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shadowai.app.R
import com.shadowai.app.ai.ConversationSummarizer

/**
 * Display indicator for a summarized conversation section.
 */
@Composable
fun SummaryIndicator(
    summary: ConversationSummarizer.StoredSummary,
    onRestore: () -> Unit,
    onView: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // M-14: Remember callbacks to prevent memory leaks
    val currentOnRestore by rememberUpdatedState(onRestore)
    val currentOnView by rememberUpdatedState(onView)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = currentOnView),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Description icon
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Summary info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = context.getString(R.string.summary_messages_summarized, summary.metadata.messageCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = formatSummaryDuration(summary, context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = context.getString(R.string.summary_tap_to_view),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }

            // Restore button
            IconButton(onClick = currentOnRestore) {
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = context.getString(R.string.summary_restore_messages)
                )
            }
        }
    }
}

/**
 * Display the detailed content of a summary.
 */
@Composable
fun SummaryDetail(
    summary: ConversationSummarizer.StoredSummary,
    onRestore: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // M-14: Remember callbacks to prevent memory leaks
    val currentOnRestore by rememberUpdatedState(onRestore)
    val currentOnCopy by rememberUpdatedState(onCopy)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.summary_conversation_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = currentOnDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = context.getString(R.string.summary_dismiss)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata
            Text(
                text = context.getString(
                    R.string.summary_duration_format,
                    summary.metadata.messageCount,
                    formatSummaryDuration(summary, context)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Content
            Text(
                text = summary.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryActionButton(
                    onClick = onRestore,
                    text = context.getString(R.string.summary_restore_messages)
                )

                Spacer(modifier = Modifier.width(8.dp))

                SecondaryActionButton(
                    onClick = onCopy,
                    text = context.getString(R.string.summary_copy)
                )
            }
        }
    }
}

/**
 * Secondary action button for summary.
 *
 * M-14: Fixed memory leak by remembering callback to prevent recreation.
 */
@Composable
private fun SecondaryActionButton(
    onClick: () -> Unit,
    text: String
) {
    // M-14: Remember callback to prevent memory leaks
    val currentOnClick by rememberUpdatedState(onClick)

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(onClick = currentOnClick)
    )
}

/**
 * Format summary metadata as human-readable string.
 */
private fun formatSummaryDuration(summary: ConversationSummarizer.StoredSummary, context: android.content.Context): String {
    val start = summary.metadata.timestampBegin
    val end = summary.metadata.timestampEnd
    val duration = end - start

    return when {
        duration < 60_000 -> context.getString(R.string.duration_less_than_minute)
        duration < 3_600_000 -> context.getString(R.string.duration_minutes, (duration / 60_000))
        duration < 86_400_000 -> context.getString(R.string.duration_hours, (duration / 3_600_000))
        else -> context.getString(R.string.duration_days, (duration / 86_400_000))
    }
}

/**
 * Compact summary badge for use in message list.
 */
@Composable
fun SummaryBadge(
    messageCount: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = context.getString(R.string.summary_badge, messageCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
