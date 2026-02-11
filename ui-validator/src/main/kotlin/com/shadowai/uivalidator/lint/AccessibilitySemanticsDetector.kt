package com.shadowai.uivalidator.lint

import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity.WARNING
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UCallExpression

/**
 * Ensures core visual components expose meaningful accessibility semantics.
 */
class AccessibilitySemanticsDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("Icon", "Image")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val owner = method.containingClass?.qualifiedName.orEmpty()
        val isComposeIcon = owner == "androidx.compose.material3.IconKt" ||
            owner == "androidx.compose.material.IconKt"
        val isComposeImage = owner == "androidx.compose.foundation.ImageKt"
        if (!isComposeIcon && !isComposeImage) {
            return
        }

        val contentDescriptionArg = node.valueArguments.getOrNull(1) ?: run {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Provide a non-empty contentDescription for accessibility."
            )
            return
        }

        val literal = contentDescriptionArg as? ULiteralExpression ?: return
        val value = literal.value
        if (value == null || (value is String && value.isBlank())) {
            context.report(
                ISSUE,
                node,
                context.getLocation(contentDescriptionArg),
                "Avoid null/blank contentDescription for user-facing visuals."
            )
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "AccessibilitySemantics",
            briefDescription = "Missing accessibility semantics in Compose visuals",
            explanation = "User-facing Icon/Image components should provide clear " +
                "content descriptions so TalkBack and switch access users can operate the UI.",
            category = CUSTOM_LINT_CHECKS,
            priority = 7,
            severity = WARNING,
            implementation = Implementation(
                AccessibilitySemanticsDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
