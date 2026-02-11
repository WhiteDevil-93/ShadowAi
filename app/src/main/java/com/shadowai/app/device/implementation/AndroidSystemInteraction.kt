package com.shadowai.app.device.implementation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.shadowai.app.device.SystemInteractionContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSystemInteraction @Inject constructor(
    @ApplicationContext private val context: Context
) : SystemInteractionContract {
    override fun toggleSetting(settingId: String, enable: Boolean) {
        // Direct toggling is often restricted. We launch the settings panel or activity as a fallback.
        // For known IDs, we map to specific Intents.
        val intent = when (settingId) {
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun launchApp(packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            // Log or handle app not found
            // For now, we fail silently or could throw, but the interface doesn't declare exceptions.
            // In a real agent, we might return a result.
        }
    }
}
