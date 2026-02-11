package com.shadowai.uicomposition

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shadowai.core.Modality
import com.shadowai.uiparams.ParameterSchema

/**
 * Contract for capability-driven transformation views.
 */
interface TransformationView {
    val viewType: ViewType
    val title: String
    val sourceModality: Modality
    val targetModality: Modality
    val parameterSchema: ParameterSchema

    @Composable
    fun Render(
        state: TransformationViewState,
        executor: TransformationExecutor,
        modifier: Modifier
    )
}

/**
 * View types used by the UI host.
 */
enum class ViewType {
    CHAT,
    IMAGE,
    VIDEO,
    AUDIO,
    EDIT
}
