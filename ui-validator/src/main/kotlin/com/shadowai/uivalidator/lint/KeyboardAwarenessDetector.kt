package com.shadowai.uivalidator.lint

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Severity.ERROR
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class KeyboardAwarenessDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("TextField", "OutlinedTextField", "BasicTextField")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInClass(method, "androidx.compose.material3.TextField") ||
            context.evaluator.isMemberInClass(method, "androidx.compose.material3.OutlinedTextField") ||
            context.evaluator.isMemberInClass(method, "androidx.compose.foundation.text.BasicTextField")) {

            // Check if the TextField is wrapped with imePadding or similar
            val hasImePadding = hasImePaddingModifier(node)
            if (!hasImePadding) {
                context.report(
                    ISSUE, node, context.getLocation(node),
                    "Text fields must use imePadding modifier to ensure they are not overlapped by the keyboard."
                )
            }
        }
    }

    private fun hasImePaddingModifier(element: org.jetbrains.uast.UElement): Boolean {
        var current = element.uastParent
        while (current != null) {
            val source = current.asSourceString()
            if (source?.contains("imePadding") == true) {
                return true
            }
            current = current.uastParent
        }
        return false
    }

    companion object {
        val ISSUE = Issue.create(
            id = "KeyboardAwareness",
            briefDescription = "Keyboard Awareness",
            explanation = "UI components must be keyboard-aware. Text fields should use imePadding " +
                    "to avoid being overlapped by the soft keyboard.",
            category = CUSTOM_LINT_CHECKS,
            priority = 8,
            severity = ERROR,
            implementation = Implementation(
                KeyboardAwarenessDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}