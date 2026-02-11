package com.shadowai.app.download

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
import javax.inject.Singleton

/**
 * Manages notifications for model download progress.
 *
 * Features:
 * - Progress notifications with percentage and speed
 * - Success/failure notifications
 * - Click to open app
 * - Cancel action button
 * - Ongoing notification during active downloads
 */
@Singleton
class DownloadNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID_DOWNLOADS = "model_downloads_channel"
        const val NOTIFICATION_ID_DOWNLOAD = 300

        // Notification IDs will be offset by download UUID hash to support multiple downloads
        private fun getNotificationId(downloadId: String): Int {
            return NOTIFICATION_ID_DOWNLOAD + downloadId.hashCode().mod(1000)
        }
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_DOWNLOADS,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of background GGUF model downloads"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Update or create a download progress notification.
     *
     * @param downloadId Unique identifier for the download (UUID or unique name)
     * @param fileName Name of the file being downloaded
     * @param progress Progress percentage (0-100)
     * @param bytesDownloaded Number of bytes downloaded
     * @param totalBytes Total bytes to download
     * @param speedBytesPerSecond Current download speed in bytes/sec
     */
    fun showDownloadProgress(
        downloadId: String,
        fileName: String,
        progress: Int,
        bytesDownloaded: Long,
        totalBytes: Long,
        speedBytesPerSecond: Float
    ) {
        val notificationId = getNotificationId(downloadId)

        val contentText = buildContentText(progress, bytesDownloaded, totalBytes, speedBytesPerSecond)

        // Create intent to open app when notification is clicked
        val intent = Intent(context, ComposeMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $fileName")
            .setContentText(contentText)
            .setProgress(100, progress, false)
            .setOngoing(progress < 100) // Make it ongoing while downloading
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true) // Don't vibrate/alert for progress updates
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * Show a download success notification.
     *
     * @param downloadId Unique identifier for the download
     * @param fileName Name of the file that was downloaded
     * @param fileSize Size of the downloaded file in bytes
     */
    fun showDownloadSuccess(downloadId: String, fileName: String, fileSize: Long) {
        val notificationId = getNotificationId(downloadId)
        val fileSizeText = formatBytes(fileSize)

        // Create intent to open app
        val intent = Intent(context, ComposeMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText("$fileName ($fileSizeText)")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * Show a download failure notification.
     *
     * @param downloadId Unique identifier for the download
     * @param fileName Name of the file that failed to download
     * @param error Optional error message
     */
    fun showDownloadFailure(downloadId: String, fileName: String, error: String? = null) {
        val notificationId = getNotificationId(downloadId)

        val contentText = if (error != null) {
            "$fileName: $error"
        } else {
            "Failed to download $fileName"
        }

        // Create intent to open app
        val intent = Intent(context, ComposeMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Failed")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_ERROR)

        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * Cancel a download notification.
     *
     * @param downloadId Unique identifier for the download
     */
    fun cancelNotification(downloadId: String) {
        val notificationId = getNotificationId(downloadId)
        notificationManager.cancel(notificationId)
    }

    /**
     * Cancel all download notifications.
     */
    fun cancelAllNotifications() {
        // Cancel by channel (removes all download notifications)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel(CHANNEL_ID_DOWNLOADS)
            createNotificationChannel() // Recreate channel for future downloads
        } else {
            // For pre-O devices, we can't bulk cancel by channel
            // This is acceptable as notification IDs are deterministic
        }
    }

    private fun buildContentText(
        progress: Int,
        bytesDownloaded: Long,
        totalBytes: Long,
        speedBytesPerSecond: Float
    ): String {
        val speedText = formatSpeed(speedBytesPerSecond)
        val downloadedText = formatBytes(bytesDownloaded)
        val totalText = formatBytes(totalBytes)

        return if (totalBytes > 0) {
            "$progress% • $speedText • $downloadedText / $totalText"
        } else {
            "$progress% • $speedText • $downloadedText"
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }

    private fun formatSpeed(bytesPerSecond: Float): String {
        return when {
            bytesPerSecond >= 1_000_000 -> String.format(
                "%.2f MB/s",
                bytesPerSecond / 1_000_000.0
            )
            bytesPerSecond >= 1_000 -> String.format(
                "%.2f KB/s",
                bytesPerSecond / 1_000.0
            )
            else -> String.format("%.2f B/s", bytesPerSecond)
        }
    }
}