package com.shadowai.uivalidator.lint

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Severity.ERROR
import com.android.tools.lint.detector.api.Severity.WARNING
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression

class Material3ColorRoleDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("Color")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInClass(method, "androidx.compose.ui.graphics.Color")) {
            // Check if it's not using MaterialTheme.colorScheme
            val parent = node.uastParent
            if (parent !is UQualifiedReferenceExpression ||
                !isMaterialThemeColorScheme(parent.receiver)) {

                val fix = fix().replace().text(node.asSourceString()).with("MaterialTheme.colorScheme.primary").build()

                context.report(
                    ISSUE, node, context.getLocation(node),
                    "Use Material 3 color roles from MaterialTheme.colorScheme instead of hardcoded Color values",
                    fix
                )
            }
        }
    }

    private fun isMaterialThemeColorScheme(receiver: UElement?): Boolean {
        return receiver?.asSourceString()?.contains("MaterialTheme.colorScheme") == true
    }

    companion object {
        val ISSUE = Issue.create(
            id = "Material3ColorRole",
            briefDescription = "Material 3 Color Role Usage",
            explanation = "Material 3 requires using semantic color roles from MaterialTheme.colorScheme " +
                    "instead of hardcoded Color values for proper theming and accessibility.",
            category = CUSTOM_LINT_CHECKS,
            priority = 6,
            severity = WARNING,
            implementation = Implementation(
                Material3ColorRoleDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}