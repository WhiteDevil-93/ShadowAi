package com.shadowai.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.shadowai.app.ComposeMainActivity
import com.shadowai.app.R
import com.shadowai.app.db.ShadowDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home screen widget provider for ShadowAi.
 *
 * Features:
 * - Quick action button for voice input
 * - Displays last message preview with timestamp
 * - Tap to open app to current conversation
 * - Multiple widget sizes (2x2, 4x2)
 * - Auto-updates when new messages arrive
 *
 * Widget Layouts:
 * - 2x2 (small): Voice button + last message preview (truncated)
 * - 4x2 (wide): Voice button + full last message + timestamp
 */
class ShadowAiWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "ShadowAiWidget"
        internal const val ACTION_VOICE_INPUT = "com.shadowai.app.action.VOICE_INPUT"
        internal const val ACTION_OPEN_CHAT = "com.shadowai.app.action.OPEN_CHAT"
        internal const val ACTION_REFRESH_WIDGET = "com.shadowai.app.action.REFRESH_WIDGET"

        /**
         * Update all widget instances.
         * Call this when last message changes.
         */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, ShadowAiWidgetProvider::class.java)
            )
            
            // Trigger update for each widget
            val intent = Intent(context, ShadowAiWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "Updating widgets: ${appWidgetIds.toList()}")
        
        // Fetch last message from database and update widgets
        CoroutineScope(Dispatchers.IO).launch {
            val lastMessage = getLastMessageFromDb(context)
            
            withContext(Dispatchers.Main) {
                appWidgetIds.forEach { appWidgetId ->
                    updateAppWidget(context, appWidgetManager, appWidgetId, lastMessage)
                }
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?
    ) {
        // Re-update widget when size changes
        CoroutineScope(Dispatchers.IO).launch {
            val lastMessage = getLastMessageFromDb(context)
            withContext(Dispatchers.Main) {
                updateAppWidget(context, appWidgetManager, appWidgetId, lastMessage)
            }
        }
    }

    override fun onEnabled(context: Context) {
        Log.d(TAG, "Widget enabled - first widget added")
    }

    override fun onDisabled(context: Context) {
        Log.d(TAG, "Widget disabled - last widget removed")
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Log.d(TAG, "Widgets deleted: ${appWidgetIds.toList()}")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_VOICE_INPUT -> {
                Log.d(TAG, "Voice input action received")
                val launchIntent = Intent(context, ComposeMainActivity::class.java).apply {
                    action = ComposeMainActivity.ACTION_LAUNCH_VOICE_INPUT
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(launchIntent)
            }
            ACTION_OPEN_CHAT -> {
                Log.d(TAG, "Open chat action received")
                val launchIntent = Intent(context, ComposeMainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(launchIntent)
            }
            ACTION_REFRESH_WIDGET -> {
                Log.d(TAG, "Refresh widget action received")
                // Refresh all widgets
                updateAllWidgets(context)
            }
        }
    }

    /**
     * Fetch the last message from database.
     */
    private suspend fun getLastMessageFromDb(context: Context): LastMessageInfo? {
        return try {
            val db = ShadowDatabase.getDatabase(context)
            val lastEntity = db.messageDao().getLastMessage()
            
            lastEntity?.let { entity ->
                LastMessageInfo(
                    text = entity.text,
                    isUser = entity.isUser,
                    timestamp = entity.timestamp,
                    sender = if (entity.isUser) "You" else (entity.modelName ?: "Shadow AI")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch last message from database", e)
            null
        }
    }
}

/**
 * Data class holding last message info for widget display.
 */
data class LastMessageInfo(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long,
    val sender: String
)

/**
 * Update a single widget instance.
 */
internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    lastMessage: LastMessageInfo? = null
) {
    val widgetOptions = appWidgetManager.getAppWidgetOptions(appWidgetId)
    val minWidth = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    val minHeight = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

    // Determine which layout to use based on size
    // 4x2 widgets are typically >= 250dp wide and >= 110dp tall
    val layoutRes = when {
        minWidth >= 250 && minHeight >= 110 -> R.layout.widget_shadow_ai_wide
        else -> R.layout.widget_shadow_ai_small
    }

    val views = RemoteViews(context.packageName, layoutRes)

    // Update message display
    if (lastMessage != null) {
        val displayText = if (lastMessage.isUser) {
            "${lastMessage.sender}: ${lastMessage.text}"
        } else {
            lastMessage.text
        }
        
        views.setTextViewText(R.id.widget_message_text, displayText)
        views.setViewVisibility(R.id.widget_message_text, View.VISIBLE)
        views.setViewVisibility(R.id.widget_empty_text, View.GONE)
        
        // For wide layout, update timestamp
        if (layoutRes == R.layout.widget_shadow_ai_wide) {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val timestampStr = timeFormat.format(Date(lastMessage.timestamp))
            views.setTextViewText(R.id.widget_timestamp, timestampStr)
            views.setViewVisibility(R.id.widget_timestamp, View.VISIBLE)
        }
    } else {
        views.setViewVisibility(R.id.widget_message_text, View.GONE)
        views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
        
        if (layoutRes == R.layout.widget_shadow_ai_wide) {
            views.setViewVisibility(R.id.widget_timestamp, View.GONE)
        }
    }

    // Set up voice input button click
    val voiceIntent = Intent(context, ShadowAiWidgetProvider::class.java).apply {
        action = ShadowAiWidgetProvider.ACTION_VOICE_INPUT
    }
    val voicePendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        voiceIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_voice_button, voicePendingIntent)

    // Set up open chat click (entire widget on wide layout, title area on small)
    val openIntent = Intent(context, ShadowAiWidgetProvider::class.java).apply {
        action = ShadowAiWidgetProvider.ACTION_OPEN_CHAT
    }
    val openPendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    if (layoutRes == R.layout.widget_shadow_ai_wide) {
        // Wide layout: entire widget opens chat, except voice button
        views.setOnClickPendingIntent(R.id.widget_root_layout, openPendingIntent)
    } else {
        // Small layout: tap on message area opens chat
        views.setOnClickPendingIntent(R.id.widget_message_text, openPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_empty_text, openPendingIntent)
    }

    // Update widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}
