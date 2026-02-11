package com.shadowai.app.device.implementation

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

/**
 * Concrete implementation of the AccessibilityService.
 * This is the system entry point for Accessibility features.
 */
class ShadowAccessibilityService : AccessibilityService() {

    companion object {
        var instance: WeakReference<ShadowAccessibilityService>? = null
    }

    private fun hasRequiredPermissions(): Boolean {
        // Verify that necessary permissions are granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Only register if permissions are properly granted
        if (hasRequiredPermissions()) {
            instance = WeakReference(this)
        } else {
            android.util.Log.e("ShadowAccessibilityService", "Required permissions not granted")
            this.disableSelf()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Verify permissions before processing events
        if (!hasRequiredPermissions()) {
            this.disableSelf()
            return
        }
        // In the future, we can analyze events here.
    }

    override fun onInterrupt() {
        // Required method
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
