package com.shadowai.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.shadowai.app.ComposeMainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Manages all non-service-related notifications (e.g., proactive suggestions, reminders).
 * Fulfills the "Notification channel setup" quick win and "Action buttons in notifications" requirement.
 */
class ShadowNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID_SUGGESTIONS = "shadow_suggestions_channel"
        const val CHANNEL_ID_REMINDERS = "shadow_reminders_channel"
        const val NOTIFICATION_ID_SUGGESTION = 201
        const val NOTIFICATION_ID_REMINDER = 202
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel for Contextual Suggestions
            val suggestionChannel = NotificationChannel(
                CHANNEL_ID_SUGGESTIONS,
                "Contextual Suggestions",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for proactive, contextual suggestions."
            }

            // Channel for Follow-up Reminders
            val reminderChannel = NotificationChannel(
                CHANNEL_ID_REMINDERS,
                "Follow-up Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for important task follow-ups and reminders."
            }

            notificationManager.createNotificationChannel(suggestionChannel)
            notificationManager.createNotificationChannel(reminderChannel)
        }
    }

    /**
     * Displays a proactive suggestion notification with optional action buttons.
     * @param title The notification title.
     * @param content The notification content.
     * @param actionIntent Optional intent for the primary action (e.g., opening the chat).
     * @param actionButton Optional action button details (text and intent).
     */
    fun showSuggestion(
        title: String,
        content: String,
        actionIntent: PendingIntent? = null,
        actionButton: Pair<String, PendingIntent>? = null
    ) {
        val defaultIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, ComposeMainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_SUGGESTIONS)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(actionIntent ?: defaultIntent)
            .setAutoCancel(true)

        actionButton?.let { (text, intent) ->
            builder.addAction(
                android.R.drawable.ic_menu_send,
                text,
                intent
            )
        }

        notificationManager.notify(NOTIFICATION_ID_SUGGESTION, builder.build())
    }

    /**
     * Shows a high-priority reminder notification.
     *
     * @param title The notification title
     * @param content The notification content text
     * @param actionIntent Optional PendingIntent for notification tap action
     */
    fun showReminder(
        title: String,
        content: String,
        actionIntent: PendingIntent? = null
    ) {
        val defaultIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, ComposeMainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(actionIntent ?: defaultIntent)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Dismiss",
                defaultIntent
            )

        notificationManager.notify(NOTIFICATION_ID_REMINDER, builder.build())
    }
}
