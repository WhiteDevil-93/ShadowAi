package com.shadowai.app.download

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.shadowai.app.ai.ModelDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for background GGUF model downloads using WorkManager.
 *
 * Features:
 * - WiFi-only downloads to preserve mobile data
 * - Battery awareness (only downloads when battery not low)
 * - Automatic retry with exponential backoff (up to 10 minutes)
 * - Progress tracking via LiveData/Flow
 * - Survives app closure and process death
 *
 * Usage:
 * ```kotlin
 * val downloadId = modelDownloadManager.downloadModel(
 *     modelUrl = "https://example.com/model.gguf",
 *     fileName = "my-model.gguf"
 * )
 * ```
 *
 * To track progress:
 * ```kotlin
 * modelDownloadManager.getDownloadProgress(downloadId)
 *     .collect { progress ->
 *         // Update UI
 *     }
 * ```
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val modelDownloader: ModelDownloader
) {

    companion object {
        private const val TAG = "ModelDownloadManager"

        // Tag for identifying model download work
        private const val WORK_TAG = "model_download"

        private fun bytesToReadable(bytes: Long): String {
            return when {
                bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
                bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
                bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
                else -> "$bytes B"
            }
        }

        private fun bytesPerSecondToReadable(bytesPerSecond: Float): String {
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

    /**
     * Download a model in the background with WorkManager.
     *
     * @param modelUrl Direct URL to the GGUF model file
     * @param fileName Target filename (will be stored in the models directory)
     * @return UUID of the work request for tracking
     */
    fun downloadModel(
        modelUrl: String,
        fileName: String
    ): UUID {
        Log.i(TAG, "Enqueuing download: $modelUrl -> $fileName")

        // Constraints for when to run the download
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // WiFi only to preserve mobile data
            .setRequiresBatteryNotLow(true) // Don't download when battery is low
            .build()

        // Build the work request with retry policy
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .setInputData(
                workDataOf(
                    ModelDownloadWorker.KEY_MODEL_URL to modelUrl,
                    ModelDownloadWorker.KEY_DESTINATION to fileName
                )
            )
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                10, TimeUnit.MINUTES
            )
            .build()

        workManager.enqueue(request)
        return request.id
    }

    /**
     * Enqueue a download with a unique work name (allows cancelling by name).
     *
     * @param modelUrl Direct URL to the GGUF model file
     * @param fileName Target filename
     * @param uniqueWorkName Unique identifier for this download
     * @return UUID of the work request
     */
    fun downloadModel(
        modelUrl: String,
        fileName: String,
        uniqueWorkName: String
    ): UUID {
        Log.i(TAG, "Enqueuing download with unique name: $uniqueWorkName")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .addTag(uniqueWorkName)
            .setInputData(
                workDataOf(
                    ModelDownloadWorker.KEY_MODEL_URL to modelUrl,
                    ModelDownloadWorker.KEY_DESTINATION to fileName
                )
            )
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                10, TimeUnit.MINUTES
            )
            .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.KEEP, // Keep existing work with this name
            request
        )
        return request.id
    }

    /**
     * Cancel a download by its UUID.
     *
     * @param workId UUID of the work request to cancel
     */
    fun cancelDownload(workId: UUID) {
        Log.i(TAG, "Cancelling download: $workId")
        workManager.cancelWorkById(workId)
    }

    /**
     * Cancel a download by its unique work name.
     *
     * @param uniqueWorkName Unique identifier for the download
     */
    fun cancelDownload(uniqueWorkName: String) {
        Log.i(TAG, "Cancelling download by name: $uniqueWorkName")
        workManager.cancelUniqueWork(uniqueWorkName)
    }

    /**
     * Cancel all active model downloads.
     */
    fun cancelAllDownloads() {
        Log.i(TAG, "Cancelling all downloads")
        workManager.cancelAllWorkByTag(WORK_TAG)
    }

    /**
     * Get the progress of a download by its UUID.
     *
     * @param workId UUID of the work request
     * @return Flow of DownloadProgress objects
     */
    fun getDownloadProgress(workId: UUID): Flow<DownloadProgress> {
        return workManager.getWorkInfoByIdFlow(workId)
            .map { workInfo ->
                if (workInfo == null) {
                    return@map DownloadProgress(
                        progressPercent = 0f,
                        bytesDownloaded = 0L,
                        totalBytes = 0L,
                        speedBytesPerSecond = 0f,
                        fileName = "",
                        runAttemptCount = 0
                    )
                }

                val data = workInfo.progress
                DownloadProgress(
                    progressPercent = data.getFloat(ModelDownloadWorker.PROGRESS_PERCENT, 0f),
                    bytesDownloaded = data.getLong(ModelDownloadWorker.BYTES_DOWNLOADED, 0L),
                    totalBytes = data.getLong(ModelDownloadWorker.TOTAL_BYTES, 0L),
                    speedBytesPerSecond = data.getFloat(
                        ModelDownloadWorker.SPEED_BYTES_PER_SECOND,
                        0f
                    ),
                    fileName = data.getString(ModelDownloadWorker.FILE_NAME) ?: "",
                    isRunning = workInfo.state == WorkInfo.State.RUNNING,
                    isSucceeded = workInfo.state == WorkInfo.State.SUCCEEDED,
                    isFailed = workInfo.state == WorkInfo.State.FAILED,
                    isCancelled = workInfo.state == WorkInfo.State.CANCELLED,
                    isEnqueued = workInfo.state == WorkInfo.State.ENQUEUED,
                    isBlocked = workInfo.state == WorkInfo.State.BLOCKED,
                    runAttemptCount = workInfo.runAttemptCount
                )
            }
    }

    /**
     * Get the progress of a download by its unique work name.
     *
     * @param uniqueWorkName Unique identifier for the download
     * @return Flow of DownloadProgress objects
     */
    fun getDownloadProgress(uniqueWorkName: String): Flow<DownloadProgress?> {
        return workManager.getWorkInfosByTagFlow(uniqueWorkName)
            .map { workInfoList ->
                val workInfo = workInfoList.firstOrNull() ?: return@map null
                val data = workInfo.progress
                DownloadProgress(
                    progressPercent = data.getFloat(ModelDownloadWorker.PROGRESS_PERCENT, 0f),
                    bytesDownloaded = data.getLong(ModelDownloadWorker.BYTES_DOWNLOADED, 0L),
                    totalBytes = data.getLong(ModelDownloadWorker.TOTAL_BYTES, 0L),
                    speedBytesPerSecond = data.getFloat(
                        ModelDownloadWorker.SPEED_BYTES_PER_SECOND,
                        0f
                    ),
                    fileName = data.getString(ModelDownloadWorker.FILE_NAME) ?: "",
                    isRunning = workInfo.state == WorkInfo.State.RUNNING,
                    isSucceeded = workInfo.state == WorkInfo.State.SUCCEEDED,
                    isFailed = workInfo.state == WorkInfo.State.FAILED,
                    isCancelled = workInfo.state == WorkInfo.State.CANCELLED,
                    isEnqueued = workInfo.state == WorkInfo.State.ENQUEUED,
                    isBlocked = workInfo.state == WorkInfo.State.BLOCKED,
                    runAttemptCount = workInfo.runAttemptCount
                )
            }
    }

    /**
     * Get all active downloads.
     *
     * @return Flow of lists of DownloadProgress objects
     */
    fun getAllDownloads(): Flow<List<DownloadProgress>> {
        return workManager.getWorkInfosByTagFlow(WORK_TAG)
            .map { workInfoList ->
                workInfoList.filter { it.state != WorkInfo.State.SUCCEEDED }
                    .map { workInfo ->
                        val data = workInfo.progress
                        DownloadProgress(
                            progressPercent = data.getFloat(ModelDownloadWorker.PROGRESS_PERCENT, 0f),
                            bytesDownloaded = data.getLong(ModelDownloadWorker.BYTES_DOWNLOADED, 0L),
                            totalBytes = data.getLong(ModelDownloadWorker.TOTAL_BYTES, 0L),
                            speedBytesPerSecond = data.getFloat(
                                ModelDownloadWorker.SPEED_BYTES_PER_SECOND,
                                0f
                            ),
                            fileName = data.getString(ModelDownloadWorker.FILE_NAME) ?: "",
                            isRunning = workInfo.state == WorkInfo.State.RUNNING,
                            isSucceeded = workInfo.state == WorkInfo.State.SUCCEEDED,
                            isFailed = workInfo.state == WorkInfo.State.FAILED,
                            isCancelled = workInfo.state == WorkInfo.State.CANCELLED,
                            isEnqueued = workInfo.state == WorkInfo.State.ENQUEUED,
                            isBlocked = workInfo.state == WorkInfo.State.BLOCKED,
                            runAttemptCount = workInfo.runAttemptCount
                        )
                    }
            }
    }

    /**
     * Data class representing download progress.
     */
    data class DownloadProgress(
        val progressPercent: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBytesPerSecond: Float,
        val fileName: String,
        val isRunning: Boolean = false,
        val isSucceeded: Boolean = false,
        val isFailed: Boolean = false,
        val isCancelled: Boolean = false,
        val isEnqueued: Boolean = false,
        val isBlocked: Boolean = false,
        val runAttemptCount: Int = 0
    ) {
        /**
         * Progress as an integer percentage (0-100).
         */
        val progressPercentInt: Int
            get() = (progressPercent * 100f).toInt().coerceIn(0, 100)

        /**
         * Human-readable speed string.
         */
        val speedReadable: String
            get() = bytesPerSecondToReadable(speedBytesPerSecond)

        /**
         * Human-readable downloaded size string.
         */
        val downloadedReadable: String
            get() = bytesToReadable(bytesDownloaded)

        /**
         * Human-readable total size string.
         */
        val totalReadable: String
            get() = bytesToReadable(totalBytes)

        /**
         * Work state summary.
         */
        val status: DownloadStatus
            get() = when {
                isSucceeded -> DownloadStatus.SUCCESS
                isFailed -> DownloadStatus.FAILED
                isCancelled -> DownloadStatus.CANCELLED
                isRunning -> DownloadStatus.RUNNING
                isEnqueued -> DownloadStatus.ENQUEUED
                isBlocked -> DownloadStatus.BLOCKED
                else -> DownloadStatus.UNKNOWN
            }
    }

    /**
     * Status of a download.
     */
    enum class DownloadStatus {
        ENQUEUED,
        RUNNING,
        SUCCESS,
        FAILED,
        CANCELLED,
        BLOCKED,
        UNKNOWN
    }
}
