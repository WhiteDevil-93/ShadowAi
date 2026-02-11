package com.shadowai.uicomposition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shadowai.core.Artifact
import com.shadowai.core.Modality
import com.shadowai.uiparams.ParameterRenderer
import kotlinx.coroutines.launch

/**
 * Text editing view (Text + Text -> Text).
 */
class EditTransformationView : TransformationView {
    override val viewType: ViewType = ViewType.EDIT
    override val title: String = "Edit"
    override val sourceModality: Modality = Modality.Text
    override val targetModality: Modality = Modality.Text
    override val parameterSchema = ViewParameterSchemas.editSchema

    @Composable
    override fun Render(
        state: TransformationViewState,
        executor: TransformationExecutor,
        modifier: Modifier
    ) {
        val scope = rememberCoroutineScope()
        val values by state.parameterState.values.collectAsState()
        val errors by state.parameterState.errors.collectAsState()

        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Base Text", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.inputText,
                onValueChange = { state.inputText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text("Enter text to edit") }
            )

            Text(text = "Parameters", style = MaterialTheme.typography.titleMedium)
            ParameterRenderer.RenderSchema(
                schema = parameterSchema,
                values = values,
                onValueChange = { id, value -> state.parameterState.updateParameter(id, value) },
                errors = errors
            )

            Button(
                onClick = {
                    val input = state.inputText.trim()
                    if (input.isEmpty()) {
                        state.errorMessage = "Input text cannot be empty."
                        return@Button
                    }
                    state.isExecuting = true
                    state.errorMessage = null
                    scope.launch {
                        val result = executor.execute(
                            input = Artifact.Text.create(content = input),
                            targetModality = targetModality,
                            parameters = state.resolvedParameters()
                        )
                        state.isExecuting = false
                        result.fold(
                            onSuccess = { state.outputArtifact = it },
                            onFailure = { state.errorMessage = it.message ?: "Execution failed." }
                        )
                    }
                },
                enabled = !state.isExecuting
            ) {
                Text(if (state.isExecuting) "Running..." else "Apply Edit")
            }

            state.errorMessage?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }

            val output = state.outputArtifact
            if (output is Artifact.Text) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Result", style = MaterialTheme.typography.titleMedium)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = output.content,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (output != null) {
                val content = when (output) {
                    is Artifact.Json -> output.jsonString
                    else -> "Output of type ${output::class.simpleName}"
                }
                Text(text = content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
