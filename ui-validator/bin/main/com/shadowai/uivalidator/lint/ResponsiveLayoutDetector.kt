package com.shadowai.uivalidator.lint

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Severity.WARNING
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class ResponsiveLayoutDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("Row", "Column", "LazyColumn", "LazyRow")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInClass(method, "androidx.compose.foundation.layout.Row") ||
            context.evaluator.isMemberInClass(method, "androidx.compose.foundation.layout.Column") ||
            context.evaluator.isMemberInClass(method, "androidx.compose.foundation.lazy.LazyColumn") ||
            context.evaluator.isMemberInClass(method, "androidx.compose.foundation.lazy.LazyRow")) {

            // Check if there are any adaptive modifiers or window size checks
            val hasAdaptiveModifier = node.valueArguments.any { arg ->
                val argSource = arg.asSourceString()
                argSource?.contains("fillMaxWidth") == true ||
                argSource?.contains("fillMaxHeight") == true ||
                argSource?.contains("weight") == true ||
                argSource?.contains("adaptive") == true
            }

            if (!hasAdaptiveModifier) {
                context.report(
                    ISSUE, node, context.getLocation(node),
                    "Use responsive layout modifiers like fillMaxWidth, weight, or check window size classes " +
                    "for adaptive behavior across different screen sizes."
                )
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "ResponsiveLayout",
            briefDescription = "Responsive Layout Implementation",
            explanation = "Layouts should be responsive and adapt to different screen sizes. " +
                    "Use appropriate modifiers and consider window size classes.",
            category = CUSTOM_LINT_CHECKS,
            priority = 6,
            severity = WARNING,
            implementation = Implementation(
                ResponsiveLayoutDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}