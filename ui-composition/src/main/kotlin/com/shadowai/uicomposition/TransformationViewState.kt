package com.shadowai.uicomposition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.shadowai.core.Artifact
import com.shadowai.uiparams.ParameterSchema
import com.shadowai.uiparams.ParameterViewState

/**
 * Holds UI state for a single transformation view.
 */
class TransformationViewState(
    val viewType: ViewType,
    val parameterState: ParameterViewState
) {
    var inputText by mutableStateOf("")
    var inputImageUri by mutableStateOf("")
    var inputVideoUri by mutableStateOf("")
    var inputAudioUri by mutableStateOf("")

    var outputArtifact by mutableStateOf<Artifact?>(null)
    var errorMessage by mutableStateOf<String?>(null)
    var isExecuting by mutableStateOf(false)

    fun clearOutput() {
        outputArtifact = null
        errorMessage = null
    }

    fun resolvedParameters(): Map<String, Any> {
        return parameterState.getCurrentValues()
            .mapNotNull { (key, value) -> value?.let { key to it } }
            .toMap()
    }
}

/**
 * Stores and reuses state across view switches.
 */
class TransformationViewStateStore {
    private val states = mutableMapOf<ViewType, TransformationViewState>()

    fun getOrCreate(viewType: ViewType, schema: ParameterSchema): TransformationViewState {
        return states.getOrPut(viewType) {
            val defaults = schema.getAllParameters()
                .associate { it.id to it.defaultValue }
            TransformationViewState(viewType, ParameterViewState(schema, defaults))
        }
    }
}

@Composable
fun rememberTransformationViewStateStore(): TransformationViewStateStore {
    return remember { TransformationViewStateStore() }
}
