package com.shadowai.uivalidator.lint

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Severity.ERROR
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * Lint detector that ensures error states are presented consistently using Material3 components.
 * 
 * Errors should use:
 * - AssistChip or SuggestionChip for inline error indicators
 * - AlertDialog for blocking errors requiring user acknowledgment
 * - Snackbar for transient non-blocking errors
 */
class ErrorPresentationDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("Text", "Button")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val textContent = node.valueArguments.firstOrNull()?.asSourceString()

        // Detect inline error text presentation (should use Chip instead)
        if (methodName == "Text" && textContent?.contains("error", ignoreCase = true) == true) {
            val parentCall = findParentComposable(node)
            // Allow if inside AlertDialog or Snackbar
            if (!isInAllowedErrorContainer(parentCall)) {
                context.report(
                    ISSUE, node, context.getLocation(node),
                    "Error text should use AssistChip, SuggestionChip, AlertDialog, or Snackbar - not plain Text."
                )
            }
        }
    }

    private fun findParentComposable(node: UCallExpression): UCallExpression? {
        var current = node.uastParent
        while (current != null) {
            if (current is UCallExpression) {
                return current
            }
            current = current.uastParent
        }
        return null
    }

    private fun isInAllowedErrorContainer(parent: UCallExpression?): Boolean {
        val name = parent?.methodName ?: return false
        return name in listOf("AlertDialog", "Snackbar", "AssistChip", "SuggestionChip")
    }

    companion object {
        val ISSUE = Issue.create(
            id = "ErrorPresentation",
            briefDescription = "Error Presentation",
            explanation = "Error states must be presented consistently using Material3 components. " +
                    "Use Chips for inline errors, AlertDialog for blocking errors, Snackbar for transient errors.",
            category = CUSTOM_LINT_CHECKS,
            priority = 7,
            severity = ERROR,
            implementation = Implementation(
                ErrorPresentationDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
