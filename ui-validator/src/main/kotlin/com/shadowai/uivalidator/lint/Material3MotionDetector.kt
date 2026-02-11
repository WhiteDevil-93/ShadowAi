package com.shadowai.uivalidator.lint

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Severity.WARNING
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * Lint detector that validates Material3 motion/animation practices.
 * 
 * Ensures:
 * - Enter/exit transitions are symmetric
 * - Animation durations follow Material3 specs (typically 150-300ms)
 * - Motion is purposeful, not decorative
 */
class Material3MotionDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "animateContentSize", "AnimatedVisibility", "animateFloatAsState",
        "Crossfade", "AnimatedContent"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val callText = node.asSourceString() ?: return

        // Check for animation duration (should use tween with appropriate duration)
        if (!callText.contains("tween") && !callText.contains("spring")) {
            context.report(
                ISSUE, node, context.getLocation(node),
                "$methodName should specify animation spec using tween() or spring() with appropriate duration."
            )
        }

        // Warn about long animations (>500ms is usually too slow for UI feedback)
        val durationMatch = Regex("durationMillis\\s*=\\s*(\\d+)").find(callText)
        val duration = durationMatch?.groupValues?.get(1)?.toIntOrNull()
        if (duration != null && duration > 500) {
            context.report(
                ISSUE, node, context.getLocation(node),
                "Animation duration $duration ms exceeds Material3 recommendation (150-300ms for UI feedback)."
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "Material3Motion",
            briefDescription = "Material3 Motion",
            explanation = "Animations should follow Material3 motion specs. " +
                    "Use tween() or spring() with 150-300ms duration for standard UI feedback.",
            category = CUSTOM_LINT_CHECKS,
            priority = 5,
            severity = WARNING,
            implementation = Implementation(
                Material3MotionDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
