package com.shadowai.app.util

import android.util.Log

/**
 * Safely access hidden Android system properties via reflection.
 */
object SystemPropertyCompat {
    private const val CLASS_NAME = "android.os.SystemProperties"
    private const val TAG = "SystemPropertyCompat"

    fun get(key: String, defaultValue: String = ""): String {
        return try {
            val clazz = Class.forName(CLASS_NAME)
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            method.invoke(null, key, defaultValue) as? String ?: defaultValue
        } catch (t: Throwable) {
            Log.d(TAG, "Unable to read system property $key: ${t.message}")
            defaultValue
        }
    }
}
