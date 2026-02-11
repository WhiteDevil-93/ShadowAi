package com.shadowai.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Debug overlay for runtime diagnostics.
 */
@Composable
fun DebugOverlay(
    collector: ErrorCollector,
    modifier: Modifier = Modifier
) {
    val errors by collector.errors.collectAsState()
    val analytics by collector.analyticsSnapshot.collectAsState()

    if (errors.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Diagnostics",
                style = MaterialTheme.typography.titleSmall
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Errors: ${analytics.totalErrors}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Top Types: ${analytics.topErrorTypes().joinToString { it.first }}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            errors.takeLast(3).forEach { error ->
                Text(
                    text = "• ${error.toLogMessage()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (error.severity) {
                        ErrorSeverity.INFO -> MaterialTheme.colorScheme.primary
                        ErrorSeverity.WARNING -> Color(0xFFFFA500)
                        ErrorSeverity.ERROR -> MaterialTheme.colorScheme.error
                        ErrorSeverity.CRITICAL -> Color(0xFFB00020)
                    }
                )
            }
        }
    }
}
