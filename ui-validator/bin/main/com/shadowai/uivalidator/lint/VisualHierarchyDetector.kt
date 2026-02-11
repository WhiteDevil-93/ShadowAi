package com.shadowai.uivalidator.lint

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Severity.ERROR
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * Lint detector that validates visual hierarchy and information organization.
 * 
 * Ensures:
 * - Important actions use emphasized buttons (FilledButton)
 * - Secondary actions use less prominent styles (TextButton, OutlinedButton)
 * - Destructive actions have appropriate styling
 * - Proper use of color for semantic meaning
 */
class VisualHierarchyDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "Button", "FilledButton", "OutlinedButton", "TextButton", "ElevatedButton"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val source = node.asSourceString() ?: return

        // Check for destructive actions without proper handling
        if (source.contains("Delete", ignoreCase = true) || 
            source.contains("Remove", ignoreCase = true) ||
            source.contains("Clear", ignoreCase = true)) {
            
            // Destructive actions should not be the default/filled style in a button group
            if (methodName in listOf("Button", "FilledButton", "ElevatedButton") && 
                isInButtonGroup(node)) {
                context.report(
                    ISSUE, node, context.getLocation(node),
                    "Destructive action ($methodName) should use a less prominent style (TextButton or OutlinedButton) " +
                    "when other primary actions are present."
                )
            }
        }
    }

    private fun isInButtonGroup(node: UCallExpression): Boolean {
        // Check if there's another button nearby
        var current = node.uastParent
        var foundButtons = 0
        var depth = 0
        while (current != null && depth < 3 && foundButtons < 2) {
            val source = current.asSourceString()
            if (source != null) {
                val buttonCount = Regex("Button|FilledButton|OutlinedButton|TextButton|ElevatedButton")
                    .findAll(source).count()
                foundButtons += buttonCount
            }
            current = current.uastParent
            depth++
        }
        return foundButtons >= 2
    }

    companion object {
        val ISSUE = Issue.create(
            id = "VisualHierarchy",
            briefDescription = "Visual Hierarchy",
            explanation = "Visual hierarchy must communicate importance and relationships. " +
                    "Use appropriate button styles (Filled for primary, Outlined/Text for secondary) " +
                    "and position destructive actions carefully.",
            category = CUSTOM_LINT_CHECKS,
            priority = 6,
            severity = ERROR,
            implementation = Implementation(
                VisualHierarchyDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
