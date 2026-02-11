package com.shadowai.uicomposition

import com.shadowai.core.Modality

/**
 * Factory for transformation views.
 */
object ViewFactory {
    fun createDefaultViews(): List<TransformationView> = listOf(
        ChatTransformationView(),
        ImageTransformationView(),
        VideoTransformationView(),
        AudioTransformationView(),
        EditTransformationView()
    )

    fun createForModalities(modalities: Set<Modality>): List<TransformationView> {
        val views = createDefaultViews()
        return ViewCompositionRules.selectViewsForModalities(modalities, views)
    }
}
