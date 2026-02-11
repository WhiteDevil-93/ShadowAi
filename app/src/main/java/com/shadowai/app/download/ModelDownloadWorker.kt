package com.shadowai.app.download

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shadowai.app.ai.ModelDownloader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker for downloading GGUF models in the background.
 *
 * Features:
 * - Survives app closure and process death
 * - Automatic retry with exponential backoff
 * - Battery and network awareness (via WorkManager constraints)
 * - Progress tracking via setProgressAsync
 * - Progress notifications (optional)
 *
 * CRITICAL DESIGN:
 * - Uses HiltWorker for dependency injection
 * - Wraps existing ModelDownloader functionality
 * - Handles retry logic automatically in doWork()
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val modelDownloader: ModelDownloader,
    private val notificationManager: DownloadNotificationManager
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ModelDownloadWorker"

        const val KEY_MODEL_URL = "model_url"
        const val KEY_DESTINATION = "destination"
        const val KEY_DOWNLOAD_ID = "download_id"  // Optional unique ID for notifications
        const val MAX_RETRIES = 3

        const val PROGRESS_PERCENT = "progress_percent"
        const val BYTES_DOWNLOADED = "bytes_downloaded"
        const val TOTAL_BYTES = "total_bytes"
        const val SPEED_BYTES_PER_SECOND = "speed_bytes_per_second"
        const val FILE_NAME = "file_name"
    }

    override suspend fun doWork(): Result {
        val modelUrl = inputData.getString(KEY_MODEL_URL)
        val destination = inputData.getString(KEY_DESTINATION)
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: id.toString()

        if (modelUrl == null || destination == null) {
            Log.e(TAG, "Missing required parameters: modelUrl=$modelUrl, destination=$destination")
            return Result.failure()
        }

        Log.i(TAG, "Starting download attempt ${runAttemptCount + 1}/$MAX_RETRIES: $modelUrl")

        return try {
            downloadModel(modelUrl, destination, downloadId)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            notificationManager.showDownloadFailure(downloadId, destination, e.message)

            if (runAttemptCount < MAX_RETRIES) {
                Log.i(TAG, "Scheduling retry $(${runAttemptCount + 1}/$MAX_RETRIES)")
                Result.retry()
            } else {
                Log.e(TAG, "Max retries reached, giving up")
                Result.failure()
            }
        } finally {
            // Clean up notification
            notificationManager.cancelNotification(downloadId)
        }
    }

    /**
     * Perform the actual model download using ModelDownloader.
     *
     * Updates work progress via setProgressAsync for UI tracking.
     * Shows progress notifications if notificationManager is provided.
     */
    private suspend fun downloadModel(url: String, fileName: String, downloadId: String) {
        modelDownloader.downloadModel(url, fileName).collect { progress ->
            // Update progress for WorkManager to track
            val progressData = androidx.work.Data.Builder()
                .putFloat(PROGRESS_PERCENT, progress.progressPercent)
                .putLong(BYTES_DOWNLOADED, progress.bytesDownloaded)
                .putLong(TOTAL_BYTES, progress.totalBytes)
                .putFloat(SPEED_BYTES_PER_SECOND, progress.speedBytesPerSecond)
                .putString(FILE_NAME, progress.fileName)
                .build()

            setProgress(progressData)

            // Show progress notification
            notificationManager.showDownloadProgress(
                downloadId = downloadId,
                fileName = progress.fileName,
                progress = progress.progressPercentInt,
                bytesDownloaded = progress.bytesDownloaded,
                totalBytes = progress.totalBytes,
                speedBytesPerSecond = progress.speedBytesPerSecond
            )

            if (progress.isComplete) {
                Log.i(TAG, "Download complete: ${progress.fileName} (${progress.bytesDownloaded} bytes)")
                notificationManager.showDownloadSuccess(downloadId, progress.fileName, progress.bytesDownloaded)
            }
        }
    }
}
