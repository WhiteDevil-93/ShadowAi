package com.shadowai.uivalidator.processor

import com.shadowai.uivalidator.annotations.Material3Compliant
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

class Material3ComplianceProcessor : AbstractProcessor() {

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(Material3Compliant::class.java.canonicalName)
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latestSupported()
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        for (element in roundEnv.getElementsAnnotatedWith(Material3Compliant::class.java)) {
            if (element !is ExecutableElement) {
                processingEnv.messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "@Material3Compliant can only be applied to functions (Composables).",
                    element
                )
                continue
            }

            // 1. Check if it has @Composable annotation
            val isComposable = element.annotationMirrors.any {
                it.annotationType.toString() == "androidx.compose.runtime.Composable"
            }

            if (!isComposable) {
                processingEnv.messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "@Material3Compliant can only be applied to @Composable functions.",
                    element
                )
            }

            // 2. Check for Modifier parameter (Material 3 Best Practice)
            val hasModifier = element.parameters.any { param ->
                val paramType = param.asType().toString()
                paramType == "androidx.compose.ui.Modifier" ||
                paramType.contains("Modifier")
            }

            if (!hasModifier) {
                processingEnv.messager.printMessage(
                    Diagnostic.Kind.WARNING,
                    "Material 3 best practice: ${element.simpleName} should accept a 'modifier: Modifier' parameter.",
                    element
                )
            }

            processingEnv.messager.printMessage(
                Diagnostic.Kind.NOTE,
                "Material 3 compliance check complete for ${element.simpleName}",
                element
            )
        }
        return true
    }
}
