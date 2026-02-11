package com.shadowai.uivalidator.lint

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Severity.ERROR
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

/**
 * Lint detector that ensures proper mode separation (Normal vs Focus mode).
 * 
 * Normal mode: Chat list, floating tabs - chat and tabs coexist
 * Focus mode: Full-screen chat, no tabs - single task focus
 * 
 * This detector catches mode-specific UI patterns being used incorrectly.
 */
class ModeSeparationDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "WindowSizeClass", "calculateWindowSizeClass"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Check that Focus mode (ImageGeneration) doesn't show tabs or navigation
        // This is checked by ensuring proper screen hierarchy
        val callSource = node.asSourceString() ?: return
        
        // In Focus mode, we shouldn't see tab references
        if (callSource.contains("ImageGeneration") && hasTabsInScope(node)) {
            context.report(
                ISSUE, node, context.getLocation(node),
                "Focus mode screens should not include tabs or navigation elements."
            )
        }
    }

    private fun hasTabsInScope(node: UCallExpression): Boolean {
        var current: UElement? = node
        var depth = 0
        while (current != null && depth < 10) {
            val source = current.asSourceString()
            if (source?.contains("TabRow") == true || 
                source?.contains("BottomNavigation") == true ||
                source?.contains("NavigationBar") == true) {
                return true
            }
            current = current.uastParent
            depth++
        }
        return false
    }

    companion object {
        val ISSUE = Issue.create(
            id = "ModeSeparation",
            briefDescription = "Mode Separation",
            explanation = "Normal and Focus modes have distinct UI patterns. " +
                    "Focus mode should not show tabs or bottom navigation.",
            category = CUSTOM_LINT_CHECKS,
            priority = 8,
            severity = ERROR,
            implementation = Implementation(
                ModeSeparationDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
