package com.shadowai.app.device.implementation

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidResourceMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Phase 5.2: Thermal & Resource Awareness
     */
    
    fun isBatteryLow(): Boolean {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = level / scale.toFloat()
        
        return batteryPct < 0.15f // 15% threshold
    }

    fun isPowerSaveMode(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isPowerSaveMode
    }

    fun isThermalRestricted(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        // Only available on API 29+
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        } else {
            false
        }
    }

    fun canExecuteLocal(): Boolean {
        return !isBatteryLow() && !isPowerSaveMode() && !isThermalRestricted()
    }
    
    fun getResourceStatus(): String {
        return when {
            isBatteryLow() -> "Low Battery"
            isPowerSaveMode() -> "Power Save Mode"
            isThermalRestricted() -> "Thermal Throttling"
            else -> "Optimal"
        }
    }
}
