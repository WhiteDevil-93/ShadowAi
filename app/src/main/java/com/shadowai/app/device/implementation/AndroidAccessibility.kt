package com.shadowai.app.device.implementation

import com.shadowai.app.device.AccessibilityContract
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter that connects the app's internal contract to the running AccessibilityService.
 */
@Singleton
class AndroidAccessibility @Inject constructor() : AccessibilityContract {

    private fun getService(): ShadowAccessibilityService? {
        return ShadowAccessibilityService.instance?.get()
    }

    override fun performGlobalAction(actionId: Int) {
        getService()?.performGlobalAction(actionId)
    }

    override fun inspectScreen(): String {
        val service = getService() ?: return "Accessibility Service not active"
        val root = service.rootInActiveWindow ?: return "No active window"

        // Simple traversal to string
        // Note: In a real agent, we would build a structured tree.
        return "Screen Content: " + root.packageName
    }
}
