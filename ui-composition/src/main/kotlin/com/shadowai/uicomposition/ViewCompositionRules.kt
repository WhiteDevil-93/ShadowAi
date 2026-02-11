package com.shadowai.uicomposition

import com.shadowai.core.Modality

/**
 * Rules for mapping modalities to transformation views.
 */
object ViewCompositionRules {
    fun viewTypeForModality(modality: Modality): ViewType {
        return when (modality) {
            is Modality.Text -> ViewType.CHAT
            is Modality.Image -> ViewType.IMAGE
            is Modality.Video -> ViewType.VIDEO
            is Modality.Audio -> ViewType.AUDIO
            is Modality.Mixed -> ViewType.EDIT
        }
    }

    fun selectViewsForModalities(
        modalities: Set<Modality>,
        availableViews: List<TransformationView>
    ): List<TransformationView> {
        val types = modalities.map { viewTypeForModality(it) }.toSet()
        return availableViews.filter { it.viewType in types }
    }
}
