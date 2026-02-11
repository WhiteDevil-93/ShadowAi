package com.shadowai.uiparams

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders parameter UI controls based on ModelParameter types.
 * Creates Jetpack Compose components dynamically for each parameter.
 */
object ParameterRenderer {
    /**
     * Renders a single parameter as a UI control.
     * Returns a composition that updates a mutable state.
     */
    @Composable
    fun RenderParameter(
        parameter: ModelParameter,
        value: Any?,
        onValueChange: (Any?) -> Unit,
        modifier: Modifier = Modifier,
        isEnabled: Boolean = true,
        showError: Boolean = false,
        error: ParameterValidator.ValidationIssue? = null
    ) {
        Column(modifier = modifier.fillMaxWidth()) {
            // Parameter label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = parameter.name,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                if (parameter.isRequired) {
                    Text(
                        text = "*",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }

            // Parameter description
            if (parameter.description.isNotEmpty()) {
                Text(
                    text = parameter.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            // Control rendering based on parameter type
            when (parameter) {
                is ModelParameter.StringParameter ->
                    RenderStringControl(parameter, value as? String, onValueChange, isEnabled)
                is ModelParameter.NumberParameter ->
                    RenderNumberControl(parameter, value as? Number, onValueChange, isEnabled)
                is ModelParameter.BooleanParameter ->
                    RenderBooleanControl(parameter, value as? Boolean, onValueChange, isEnabled)
                is ModelParameter.EnumParameter ->
                    RenderEnumControl(parameter, value as? String, onValueChange, isEnabled)
                is ModelParameter.ArrayParameter ->
                    RenderArrayControl(parameter, value as? List<*>, onValueChange, isEnabled)
                is ModelParameter.FileParameter ->
                    RenderFileControl(parameter, isEnabled)
                is ModelParameter.ObjectParameter ->
                    RenderObjectControl(parameter, value as? Map<*, *>, isEnabled)
                is ModelParameter.RangeParameter ->
                    RenderRangeControl(parameter, value as? Pair<*, *>, onValueChange, isEnabled)
            }

            // Error display
            if (showError && error != null) {
                Text(
                    text = error.message,
                    color = when (error.severity) {
                        ParameterValidator.ValidationIssue.Severity.ERROR ->
                            MaterialTheme.colorScheme.error
                        ParameterValidator.ValidationIssue.Severity.WARNING ->
                            Color(0xFFFFA500)
                        ParameterValidator.ValidationIssue.Severity.INFO ->
                            MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }
    }

    /**
     * Renders multiple parameters from a schema.
     */
    @Composable
    fun RenderSchema(
        schema: ParameterSchema,
        values: Map<String, Any?>,
        onValueChange: (String, Any?) -> Unit,
        modifier: Modifier = Modifier,
        errors: Map<String, ParameterValidator.ValidationIssue>? = null
    ) {
        LazyColumn(modifier = modifier.fillMaxWidth()) {
            items(schema.getAllParameters()) { parameter ->
                RenderParameter(
                    parameter = parameter,
                    value = values[parameter.id],
                    onValueChange = { onValueChange(parameter.id, it) },
                    modifier = Modifier.padding(8.dp),
                    showError = errors != null,
                    error = errors?.get(parameter.id)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }

    // Private rendering methods

    @Composable
    private fun RenderStringControl(
        parameter: ModelParameter.StringParameter,
        value: String?,
        onValueChange: (Any?) -> Unit,
        isEnabled: Boolean
    ) {
        TextField(
            value = value ?: parameter.defaultValue,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            placeholder = { Text(parameter.hint ?: "Enter ${parameter.name.lowercase()}") },
            enabled = isEnabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
    }

    @Composable
    private fun RenderNumberControl(
        parameter: ModelParameter.NumberParameter,
        value: Number?,
        onValueChange: (Any?) -> Unit,
        isEnabled: Boolean
    ) {
        // If has range, use slider
        if (parameter.min != null && parameter.max != null) {
            val currentValue = (value as? Number)?.toDouble() ?: parameter.defaultValue.toDouble()
            Slider(
                value = currentValue.toFloat(),
                onValueChange = { onValueChange(it.toDouble()) },
                valueRange = parameter.min.toDouble().toFloat()..parameter.max.toDouble().toFloat(),
                modifier = Modifier.fillMaxWidth(),
                enabled = isEnabled,
                steps = if (parameter.step != null) ((parameter.max.toDouble() - parameter.min.toDouble()) / parameter.step.toDouble()).toInt() else 0
            )
            Text(
                text = currentValue.toString(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            // Otherwise use text input
            TextField(
                value = (value ?: parameter.defaultValue).toString(),
                onValueChange = { newValue ->
                    try {
                        onValueChange(newValue.toDoubleOrNull() ?: 0)
                    } catch (e: Exception) {
                        // Invalid number
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = isEnabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }

    @Composable
    private fun RenderBooleanControl(
        parameter: ModelParameter.BooleanParameter,
        value: Boolean?,
        onValueChange: (Any?) -> Unit,
        isEnabled: Boolean
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = parameter.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = value ?: parameter.defaultValue,
                onCheckedChange = onValueChange,
                enabled = isEnabled
            )
        }
    }

    @Composable
    private fun RenderEnumControl(
        parameter: ModelParameter.EnumParameter,
        value: String?,
        onValueChange: (Any?) -> Unit,
        isEnabled: Boolean
    ) {
        var expanded by remember { mutableStateOf(false) }
        val selectedValue = value ?: parameter.defaultValue

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Button(
                onClick = { expanded = !expanded },
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                enabled = isEnabled,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(parameter.getDisplayName(selectedValue))
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                parameter.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(parameter.getDisplayName(option)) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun RenderArrayControl(
        parameter: ModelParameter.ArrayParameter,
        value: List<*>?,
        onValueChange: (Any?) -> Unit,
        isEnabled: Boolean
    ) {
        val items = value ?: parameter.defaultValue
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${parameter.name} (${items.size}/${parameter.maxItems} items)",
                style = MaterialTheme.typography.bodySmall
            )
            if (items.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(items.size) { index ->
                        Text(
                            text = "- ${items[index]}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun RenderFileControl(
        parameter: ModelParameter.FileParameter,
        isEnabled: Boolean
    ) {
        Button(
            onClick = { /* TODO: File picker integration */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = isEnabled
        ) {
            Text("Select file (${parameter.acceptedMimeTypes.joinToString(", ")})")
        }
    }

    @Composable
    private fun RenderObjectControl(
        parameter: ModelParameter.ObjectParameter,
        value: Map<*, *>?,
        isEnabled: Boolean
    ) {
        Text(
            text = "${parameter.name}: ${value?.size ?: 0} properties",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(8.dp)
        )
    }

    @Composable
    private fun RenderRangeControl(
        parameter: ModelParameter.RangeParameter,
        value: Pair<*, *>?,
        onValueChange: (Any?) -> Unit,
        isEnabled: Boolean
    ) {
        val (minVal, maxVal) = value as? Pair<*, *>
            ?: (parameter.min to parameter.max)

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = minVal.toString(),
                    onValueChange = { },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    readOnly = true,
                    label = { Text("Min") }
                )
                Text("-", style = MaterialTheme.typography.bodyMedium)
                TextField(
                    value = maxVal.toString(),
                    onValueChange = { },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    readOnly = true,
                    label = { Text("Max") }
                )
            }
            RangeSlider(
                value = (minVal as Number).toFloat()..(maxVal as Number).toFloat(),
                onValueChange = { range ->
                    onValueChange(range.start.toDouble() to range.endInclusive.toDouble())
                },
                valueRange = parameter.min.toDouble().toFloat()..parameter.max.toDouble().toFloat(),
                modifier = Modifier.fillMaxWidth(),
                enabled = isEnabled
            )
        }
    }
}
