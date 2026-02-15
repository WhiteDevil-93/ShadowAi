package com.shadowai.app.download

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
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
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for background GGUF model downloads using WorkManager.
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val modelDownloader: ModelDownloader
) {

    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val WORK_TAG = "model_download"

        fun bytesToReadable(bytes: Long): String {
            return when {
                bytes >= 1_000_000_000 -> String.format(Locale.US, "%.2f GB", bytes / 1_000_000_000.0)
                bytes >= 1_000_000 -> String.format(Locale.US, "%.2f MB", bytes / 1_000_000.0)
                bytes >= 1_000 -> String.format(Locale.US, "%.2f KB", bytes / 1_000.0)
                else -> "$bytes B"
            }
        }

        fun bytesPerSecondToReadable(bytesPerSecond: Float): String {
            return when {
                bytesPerSecond >= 1_000_000 -> String.format(
                    Locale.US,
                    "%.2f MB/s",
                    bytesPerSecond / 1_000_000.0
                )
                bytesPerSecond >= 1_000 -> String.format(
                    Locale.US,
                    "%.2f KB/s",
                    bytesPerSecond / 1_000.0
                )
                else -> String.format(Locale.US, "%.2f B/s", bytesPerSecond)
            }
        }
    }

    fun downloadModel(
        modelUrl: String,
        fileName: String
    ): UUID {
        Log.i(TAG, "Enqueuing download: $modelUrl -> $fileName")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()

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
            ExistingWorkPolicy.KEEP,
            request
        )
        return request.id
    }

    fun cancelDownload(workId: UUID) {
        Log.i(TAG, "Cancelling download: $workId")
        workManager.cancelWorkById(workId)
    }

    fun cancelDownload(uniqueWorkName: String) {
        Log.i(TAG, "Cancelling download by name: $uniqueWorkName")
        workManager.cancelUniqueWork(uniqueWorkName)
    }

    fun cancelAllDownloads() {
        Log.i(TAG, "Cancelling all downloads")
        workManager.cancelAllWorkByTag(WORK_TAG)
    }

    fun getDownloadProgress(workId: UUID): Flow<DownloadProgress> {
        return workManager.getWorkInfoByIdFlow(workId)
            .map { workInfo ->
                if (workInfo == null) return@map DownloadProgress.empty()

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

    fun getDownloadProgress(uniqueWorkName: String): Flow<DownloadProgress?> {
        return workManager.getWorkInfosByTagFlow(uniqueWorkName)
            .map { workInfoList ->
                workInfoList.firstOrNull()?.let { workInfo ->
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
        val progressPercentInt: Int
            get() = (progressPercent * 100f).toInt().coerceIn(0, 100)

        val speedReadable: String
            get() = bytesPerSecondToReadable(speedBytesPerSecond)

        val downloadedReadable: String
            get() = bytesToReadable(bytesDownloaded)

        val totalReadable: String
            get() = bytesToReadable(totalBytes)

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

        companion object {
            fun empty() = DownloadProgress(0f, 0L, 0L, 0f, "")
        }
    }

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
