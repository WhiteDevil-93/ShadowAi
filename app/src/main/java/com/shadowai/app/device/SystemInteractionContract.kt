package com.shadowai.app.device

/**
 * Defines the capabilities for general system interactions.
 * This is an inert interface for Phase 4.
 */
interface SystemInteractionContract {
    /**
     * Toggles a system setting (e.g., WiFi, Bluetooth).
     * @param settingId The identifier of the setting.
     * @param enable True to enable, false to disable.
     */
    fun toggleSetting(settingId: String, enable: Boolean)

    /**
     * Launches an application by package name.
     * @param packageName The package name of the app to launch.
     */
    fun launchApp(packageName: String)
}
