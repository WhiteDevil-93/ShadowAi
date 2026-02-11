package com.shadowai.uivalidator.lint

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Severity.WARNING
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * Lint detector that validates execution context in UI interactions.
 * 
 * Detects potential issues with:
 * - Click handlers that might cause state inconsistency
 * - Missing accessibility actions
 */
class ExecutionContextDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("clickable", "combinedClickable")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Check if clickable has proper semantics for accessibility
        val hasOnClickLabel = node.valueArguments.any { arg ->
            arg.asSourceString()?.contains("onClickLabel") == true
        }

        if (!hasOnClickLabel) {
            context.report(
                ISSUE, node, context.getLocation(node),
                "clickable/combinedClickable should have onClickLabel for accessibility."
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "ExecutionContext",
            briefDescription = "Execution Context",
            explanation = "Interactive elements must have proper accessibility labels. " +
                    "Always provide onClickLabel for clickable elements.",
            category = CUSTOM_LINT_CHECKS,
            priority = 6,
            severity = WARNING,
            implementation = Implementation(
                ExecutionContextDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
