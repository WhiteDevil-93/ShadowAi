package com.shadowai.uivalidator.lint

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Severity.ERROR
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUClass

/**
 * Lint detector that validates proper handling of system insets.
 * 
 * Ensures:
 * - Scaffold and top-level containers use WindowInsets
 * - Content respects system bars (status bar, navigation bar)
 * - No overlapping with system UI
 */
class SystemInsetsDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("Scaffold", "Column", "Box", "Surface")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val source = node.asSourceString() ?: return

        // Check if Scaffold is using proper insets handling
        if (methodName == "Scaffold") {
            if (!source.contains("contentWindowInsets") && !source.contains("WindowInsets")) {
                // Scaffold has default insets handling, so this is just a warning
                context.report(
                    ISSUE, node, context.getLocation(node),
                    "Consider explicitly specifying contentWindowInsets on Scaffold for proper edge-to-edge handling."
                )
            }
        }

        // Check for content that might overlap with system bars
        if (methodName in listOf("Column", "Box", "Surface")) {
            // Check if in a top-level composable without proper padding
            if (isTopLevelComposable(node) && !hasInsetsPadding(source)) {
                context.report(
                    ISSUE, node, context.getLocation(node),
                    "$methodName at root level should handle system insets (use Modifier.windowInsetsPadding() or similar)."
                )
            }
        }
    }

    private fun isTopLevelComposable(node: UCallExpression): Boolean {
        // Check if this is at the top level of a composable function
        var parent = node.uastParent
        while (parent != null) {
            if (parent is UMethod) {
                return parent.annotations.any { it.qualifiedName?.contains("Composable") == true }
            }
            parent = parent.uastParent
        }
        return false
    }

    private fun hasInsetsPadding(source: String): Boolean {
        return source.contains("statusBarsPadding") ||
               source.contains("navigationBarsPadding") ||
               source.contains("systemBarsPadding") ||
               source.contains("windowInsetsPadding") ||
               source.contains("safeContentPadding") ||
               source.contains("imePadding")
    }

    companion object {
        val ISSUE = Issue.create(
            id = "SystemInsets",
            briefDescription = "System Insets",
            explanation = "Content must handle system insets (status bars, navigation bars, keyboard) " +
                    "to avoid overlapping with system UI and ensure edge-to-edge layouts work correctly.",
            category = CUSTOM_LINT_CHECKS,
            priority = 7,
            severity = ERROR,
            implementation = Implementation(
                SystemInsetsDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
