package com.shadowai.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Error reporting UI for diagnostics.
 */
@Composable
fun ErrorReportScreen(
    collector: ErrorCollector,
    onExportReport: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val errors by collector.errors.collectAsState()
    val analytics by collector.analyticsSnapshot.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Diagnostics Report", style = MaterialTheme.typography.titleLarge)
        Text(text = "Total Errors: ${analytics.totalErrors}", style = MaterialTheme.typography.bodyMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onExportReport(collector.buildReport()) }) {
                Text("Export")
            }
            Button(onClick = onClear) {
                Text("Clear")
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(errors.reversed()) { error ->
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(text = error.toLogMessage(), style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "${error.severity} @ ${error.timestamp}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
