package com.shadowai.app.device.implementation

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.shadowai.app.device.MessagingContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidMessaging @Inject constructor(
    @ApplicationContext private val context: Context
) : MessagingContract {

    private fun hasSmsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun sendSms(phoneNumber: String, message: String) {
        // Verify permission BEFORE proceeding
        if (!hasSmsPermission()) {
            throw SecurityException("SMS_SEND permission not granted")
        }
        
        // In Android 12+ (API 31+), we should use context.getSystemService(SmsManager::class.java)
        // compileSdk is 36, so we can use the modern API.
        val smsManager = context.getSystemService(SmsManager::class.java)
        smsManager.sendTextMessage(phoneNumber, null, message, null, null)
    }

    override fun getRecentMessages(): List<String> {
        // Check permission before reading messages
        if (!hasReadSmsPermission()) {
            return emptyList()
        }
        
        val messages = mutableListOf<String>()
        val contentResolver = context.contentResolver
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY)

        try {
            val cursor = contentResolver.query(uri, projection, null, null, "date DESC LIMIT 5")
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val address = cursor.getString(0)
                    val body = cursor.getString(1)
                    // Redact message content for PII protection
                    messages.add("From $address: [REDACTED]")
                }
                cursor.close()
            }
        } catch (e: SecurityException) {
            messages.add("Permission denied: READ_SMS")
        } catch (e: Exception) {
            messages.add("Error reading messages: ${e.message}")
        }
        return messages
    }

    private fun hasReadSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
