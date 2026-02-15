package com.shadowai.app.settings

/**
 * M-13: Settings bounds validation utilities.
 *
 * Provides standardized validation for float settings in the 0.0-1.0 range.
 * Ensures user settings stay within safe operational bounds.
 */
object SettingsBounds {

    /**
     * Validates that a float value is within the 0.0-1.0 bound.
     *
     * @param value The value to validate
     * @param default The default value to use if out of bounds
     * @param settingName Name of the setting for logging purposes
     * @return The validated value (clamped to [0.0, 1.0])
     */
    fun validateNormalized(value: Float, default: Float = 0.5f, settingName: String = "setting"): Float {
        return when {
            value < 0.0f -> {
                android.util.Log.w("SettingsBounds", "$settingName value $value < 0.0, using default $default")
                default
            }
            value > 1.0f -> {
                android.util.Log.w("SettingsBounds", "$settingName value $value > 1.0, clamping to 1.0")
                1.0f
            }
            else -> value
        }
    }

    /**
     * Validates a double value is within the 0.0-1.0 bound.
     *
     * @param value The value to validate
     * @param default The default value to use if out of bounds
     * @param settingName Name of the setting for logging purposes
     * @return The validated value (clamped to [0.0, 1.0])
     */
    fun validateNormalized(value: Double, default: Double = 0.5, settingName: String = "setting"): Double {
        return when {
            value < 0.0 -> {
                android.util.Log.w("SettingsBounds", "$settingName value $value < 0.0, using default $default")
                default
            }
            value > 1.0 -> {
                android.util.Log.w("SettingsBounds", "$settingName value $value > 1.0, clamping to 1.0")
                1.0
            }
            else -> value
        }
    }

    /**
     * Validates and clamps the value, returning a Result.
     *
     * @param value The value to validate
     * @return Result containing either the valid value or error
     */
    fun tryValidateNormalized(value: Float): Result<Float> {
        return when {
            value < 0.0f -> Result.failure(IllegalArgumentException("Value $value is below minimum 0.0"))
            value > 1.0f -> Result.failure(IllegalArgumentException("Value $value exceeds maximum 1.0"))
            else -> Result.success(value)
        }
    }

    /**
     * Validates a range of values [min, max] are within 0.0-1.0.
     */
    fun validateRange(min: Float, max: Float): Pair<Float, Float> {
        val validMin = validateNormalized(min)
        val validMax = validateNormalized(max)
        return if (validMin <= validMax) {
            validMin to validMax
        } else {
            android.util.Log.w("SettingsBounds", "Min ($min) > Max ($max), swapping")
            validMax to validMin
        }
    }

    /**
     * Common predefined bounds for settings.
     */
    object Presets {
        const val TEMP_MIN = 0.0f
        const val TEMP_MAX = 2.0f  // Temperature can exceed 1.0
        const val TOP_P_MIN = 0.0f
        const val TOP_P_MAX = 1.0f
        const val TOP_K_MIN = 1
        const val TOP_K_MAX = 100
        const val FREQUENCY_PENALTY_MIN = 0.0f
        const val FREQUENCY_PENALTY_MAX = 2.0f
        const val PRESENCE_PENALTY_MIN = 0.0f
        const val PRESENCE_PENALTY_MAX = 2.0f
    }
}