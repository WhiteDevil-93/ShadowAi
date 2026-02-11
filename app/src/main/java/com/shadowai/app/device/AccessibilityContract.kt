package com.shadowai.app.device

/**
 * Defines the capabilities for Accessibility Service interactions.
 * This is an inert interface for Phase 4.
 */
interface AccessibilityContract {
    /**
     * Performs a global system action (e.g., HOME, BACK).
     * @param actionId The Android system action ID.
     */
    fun performGlobalAction(actionId: Int)

    /**
     * Inspects the current screen content.
     * @return A simplified representation of the UI hierarchy.
     */
    fun inspectScreen(): String
}
