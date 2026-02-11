package com.shadowai.app.device.implementation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import com.shadowai.app.device.TelephonyContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidTelephony @Inject constructor(
    @ApplicationContext private val context: Context
) : TelephonyContract {

    override fun initiateCall(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL)
        intent.data = Uri.parse("tel:$phoneNumber")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // Basic check, though we expect permissions to be handled by caller/user logic
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            context.startActivity(intent)
        } else {
            // Fallback to DIAL which doesn't need runtime permission (only starts dialer)
            val dialIntent = Intent(Intent.ACTION_DIAL)
            dialIntent.data = Uri.parse("tel:$phoneNumber")
            dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(dialIntent)
        }
    }

    override fun endCall() {
        // Only available on API 28+; guard to avoid lint/new API violations.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

        val telecomManager = context.getSystemService(TelecomManager::class.java) ?: return

        if (context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
             // API 28+ endCall
            try {
                @Suppress("DEPRECATION")
                telecomManager.endCall()
            } catch (e: Exception) {
                // Ignore, might not be default dialer
            }
        }
    }
}
