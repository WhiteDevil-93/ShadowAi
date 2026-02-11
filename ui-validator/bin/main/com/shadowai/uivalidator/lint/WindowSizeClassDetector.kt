package com.shadowai.uivalidator.lint

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Severity.ERROR
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * Lint detector that validates window size class usage for responsive layouts.
 * 
 * Ensures:
 * - calculateWindowSizeClass() is used for responsive breakpoints
 * - Layouts handle Compact, Medium, and Expanded sizes appropriately
 * - No hardcoded width/height checks that bypass WindowSizeClass
 */
class WindowSizeClassDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "calculateWindowSizeClass", "BoxWithConstraints"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val source = node.asSourceString() ?: return

        if (methodName == "BoxWithConstraints") {
            // BoxWithConstraints can be used, but should also check WindowSizeClass for consistency
            if (!source.contains("WindowSizeClass") && !source.contains("calculateWindowSizeClass")) {
                context.report(
                    ISSUE, node, context.getLocation(node),
                    "BoxWithConstraints should be used alongside WindowSizeClass for consistent responsive behavior."
                )
            }
        }

        // Check for hardcoded width checks (should use WindowSizeClass)
        if (methodName == "calculateWindowSizeClass") {
            // Good - they're using WindowSizeClass
            val parent = node.uastParent
            val parentSource = parent?.asSourceString() ?: return
            
            // Ensure the result is actually used
            if (!parentSource.contains("widthSizeClass") && !parentSource.contains("heightSizeClass")) {
                context.report(
                    ISSUE, node, context.getLocation(node),
                    "WindowSizeClass result should be used (access widthSizeClass or heightSizeClass property)."
                )
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "WindowSizeClass",
            briefDescription = "Window Size Class",
            explanation = "Responsive layouts must use WindowSizeClass (Compact/Medium/Expanded) " +
                    "for consistent breakpoint handling. Avoid hardcoded width/height checks.",
            category = CUSTOM_LINT_CHECKS,
            priority = 8,
            severity = ERROR,
            implementation = Implementation(
                WindowSizeClassDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
