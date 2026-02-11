package com.shadowai.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Displays the progress of a multi-step agent task.
 * This component fulfills the "Progress through multi-step tasks" requirement.
 *
 * @param currentStep The current step number (1-based).
 * @param totalSteps The total number of steps expected (or a reasonable maximum). Must be >= 0.
 * @param statusText A descriptive text for the current status (e.g., "Reasoning on tool output", "Executing API call").
 */
@Composable
fun TaskProgress(
    currentStep: Int,
    totalSteps: Int,
    statusText: String,
    modifier: Modifier = Modifier
) {
    // Handle edge case where totalSteps is 0 or negative
    val safeTotalSteps = maxOf(1, totalSteps)
    val safeCurrentStep = currentStep.coerceIn(0, safeTotalSteps)
    val progress = safeCurrentStep.toFloat() / safeTotalSteps.toFloat()

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "taskProgressAnimation"
    )

    // Show indeterminate progress when total steps is unknown (0)
    val isIndeterminate = totalSteps <= 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = if (isIndeterminate) {
                "Task Progress: Step $currentStep"
            } else {
                "Task Progress: Step $safeCurrentStep of $safeTotalSteps"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (isIndeterminate) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
