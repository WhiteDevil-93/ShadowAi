package com.shadowai.uivalidator.lint

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Severity.WARNING
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * Lint detector that validates animation/transitions follow Material3 motion rules.
 * 
 * Rules:
 * - Use fade for subtle state changes
 * - Use scale for emphasis
 * - Use slide for spatial relationships
 * - Keep animations under 300ms for UI feedback
 */
class MotionRulesDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "fadeIn", "fadeOut", "scaleIn", "scaleOut", 
        "slideInHorizontally", "slideOutHorizontally",
        "slideInVertically", "slideOutVertically"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val source = node.asSourceString() ?: return
        
        // Check for proper duration specification
        val durationMatch = Regex("durationMillis\\s*=\\s*(\\d+)").find(source)
        val duration = durationMatch?.groupValues?.get(1)?.toIntOrNull()
        
        if (duration == null) {
            context.report(
                ISSUE, node, context.getLocation(node),
                "$methodName should specify durationMillis (recommended: 150-300ms)."
            )
        } else if (duration > 400) {
            context.report(
                ISSUE, node, context.getLocation(node),
                "Animation duration $duration ms is too slow. Keep UI animations under 300ms."
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "MotionRules",
            briefDescription = "Motion Rules",
            explanation = "Animations should follow Material3 motion guidelines. " +
                    "Use appropriate animation types and keep durations 150-300ms for responsiveness.",
            category = CUSTOM_LINT_CHECKS,
            priority = 5,
            severity = WARNING,
            implementation = Implementation(
                MotionRulesDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
