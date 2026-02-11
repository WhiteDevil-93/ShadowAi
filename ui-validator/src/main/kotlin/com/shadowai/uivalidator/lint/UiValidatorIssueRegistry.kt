package com.shadowai.uivalidator.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/**
 * Registry of all UI validation lint issues for ShadowAi.
 * 
 * These checks enforce:
 * - Material3 design system compliance
 * - Accessibility requirements
 * - ShadowAi-specific UI patterns (Focus mode, Normal mode)
 * - Proper responsive layout practices
 */
class UiValidatorIssueRegistry : IssueRegistry() {
    override val issues: List<Issue>
        get() = listOf(
            // Material3 Compliance
            Material3ColorRoleDetector.ISSUE,
            Material3ElevationDetector.ISSUE,
            Material3MotionDetector.ISSUE,
            Material3TypographyDetector.ISSUE,

            // Layout & Responsiveness
            ResponsiveLayoutDetector.ISSUE,
            WindowSizeClassDetector.ISSUE,
            SystemInsetsDetector.ISSUE,

            // UI Patterns
            ModeSeparationDetector.ISSUE,
            ErrorPresentationDetector.ISSUE,
            VisualHierarchyDetector.ISSUE,
            MotionRulesDetector.ISSUE,

            // Accessibility
            AccessibilitySemanticsDetector.ISSUE,
            KeyboardAwarenessDetector.ISSUE,
            ExecutionContextDetector.ISSUE,
        )

    override val api: Int = CURRENT_API
}
