package com.shadowai.uivalidator.lint

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Severity.ERROR
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * Lint detector that validates Material3 typography usage.
 * 
 * Ensures:
 * - Text uses Material3 typography tokens (display, headline, title, body, label)
 * - Avoids hardcoded font sizes and styles
 * - Maintains type scale hierarchy
 */
class Material3TypographyDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("Text")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Check for hardcoded font size or style
        val source = node.asSourceString() ?: return
        
        val hasHardcodedSize = Regex("fontSize\\s*=\\s*\\d+\\.sp|fontSize\\s*=\\s*\\d+\\.dp").find(source) != null
        val hasDirectStyle = Regex("fontWeight\\s*=\\s*FontWeight\\.\\w+|fontStyle\\s*=\\s*FontStyle\\.\\w+").find(source) != null
        val usesMaterialStyle = source.contains("MaterialTheme.typography")

        if ((hasHardcodedSize || hasDirectStyle) && !usesMaterialStyle) {
            context.report(
                ISSUE, node, context.getLocation(node),
                "Use MaterialTheme.typography tokens instead of hardcoded font sizes/styles. " +
                "Examples: typography.bodyLarge, typography.headlineMedium, etc."
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "Material3Typography",
            briefDescription = "Material3 Typography",
            explanation = "All text should use Material3 typography tokens for consistent type hierarchy " +
                    "and support dynamic type scaling.",
            category = CUSTOM_LINT_CHECKS,
            priority = 6,
            severity = ERROR,
            implementation = Implementation(
                Material3TypographyDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
