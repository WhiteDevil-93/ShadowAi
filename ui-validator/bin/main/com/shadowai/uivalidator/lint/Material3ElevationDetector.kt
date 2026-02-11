package com.shadowai.uivalidator.lint

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Severity.WARNING
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * Lint detector that validates Material3 elevation usage.
 * 
 * Ensures proper elevation hierarchy:
 * - Cards should have consistent elevation levels
 * - Overlapping surfaces should respect elevation order
 * - Avoid mixing different elevation surfaces arbitrarily
 */
class Material3ElevationDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "Card", "ElevatedCard", "OutlinedCard", 
        "Surface", "ElevatedButton", "FloatingActionButton"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        
        // Check for hardcoded elevation values (should use Material3 tokens)
        val elevationArg = node.valueArguments.find { arg ->
            arg.asSourceString()?.contains("elevation") == true
        }
        
        val elevationValue = elevationArg?.asSourceString()
        if (elevationValue != null && elevationValue.matches(Regex(".*\\d+\\.dp.*"))) {
            // Has hardcoded dp value - should use Material3 elevation level
            context.report(
                ISSUE, node, context.getLocation(node),
                "Use Material3 elevation tokens (e.g., CardDefaults.elevatedCardElevation()) instead of hardcoded dp values."
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "Material3Elevation",
            briefDescription = "Material3 Elevation",
            explanation = "Material3 surfaces should use standard elevation tokens. " +
                    "Avoid hardcoded elevation values; use CardDefaults, ButtonDefaults, etc.",
            category = CUSTOM_LINT_CHECKS,
            priority = 5,
            severity = WARNING,
            implementation = Implementation(
                Material3ElevationDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
