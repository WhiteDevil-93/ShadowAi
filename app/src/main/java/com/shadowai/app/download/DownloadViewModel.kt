package com.shadowai.app.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for managing model downloads in the UI.
 *
 * Demonstrates proper usage of ModelDownloadManager:
 * - Starting downloads
 * - Tracking progress
 * - Canceling downloads
 *
 * Example usage in Compose:
 * ```kotlin
 * @Composable
 * fun DownloadScreen(viewModel: DownloadViewModel = hiltViewModel()) {
 *     val downloadState by viewModel.downloadState.collectAsState()
 *
 *     // UI to show download button...
 *
 *     fun onDownloadClick(url: String, fileName: String) {
 *         viewModel.startDownload(url, fileName)
 *     }
 *
 *     // Show progress...
 *     if (downloadState.isDownloading) {
 *         LinearProgressIndicator(progress = downloadState.progress)
 *         Text("${downloadState.progress}% - ${downloadState.speed}")
 *     }
 * }
 * ```
 */
@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadManager: ModelDownloadManager
) : ViewModel() {

    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var currentDownloadId: UUID? = null

    /**
     * Start a new model download.
     *
     * @param modelUrl Direct URL to the GGUF model file
     * @param fileName Target filename (will be stored in the models directory)
     * @param uniqueWorkName Optional unique name for the download (allows cancellation by name)
     */
    fun startDownload(
        modelUrl: String,
        fileName: String,
        uniqueWorkName: String? = null
    ) {
        viewModelScope.launch {
            try {
                _downloadState.value = _downloadState.value.copy(
                    isDownloading = true,
                    progress = 0,
                    status = ModelDownloadManager.DownloadStatus.ENQUEUED
                )

                val downloadId = if (uniqueWorkName != null) {
                    downloadManager.downloadModel(modelUrl, fileName, uniqueWorkName)
                } else {
                    downloadManager.downloadModel(modelUrl, fileName)
                }
                currentDownloadId = downloadId

                // Track progress
                downloadManager.getDownloadProgress(downloadId).collect { progress ->
                    _downloadState.value = DownloadState(
                        isDownloading = !progress.isSucceeded && !progress.isFailed && !progress.isCancelled,
                        progress = progress.progressPercentInt,
                        fileName = progress.fileName,
                        bytesDownloaded = progress.bytesDownloaded,
                        totalBytes = progress.totalBytes,
                        speed = progress.speedReadable,
                        downloadedReadable = progress.downloadedReadable,
                        totalReadable = progress.totalReadable,
                        status = progress.status,
                        runAttemptCount = progress.runAttemptCount
                    )
                }
            } catch (e: Exception) {
                _downloadState.value = _downloadState.value.copy(
                    isDownloading = false,
                    status = ModelDownloadManager.DownloadStatus.FAILED,
                    error = e.message
                )
            }
        }
    }

    /**
     * Cancel the current download.
     */
    fun cancelDownload() {
        viewModelScope.launch {
            currentDownloadId?.let { downloadManager.cancelDownload(it) }
            _downloadState.value = DownloadState()
        }
    }

    /**
     * Cancel a download by its unique work name.
     *
     * @param uniqueWorkName Unique identifier for the download
     */
    fun cancelDownload(uniqueWorkName: String) {
        viewModelScope.launch {
            downloadManager.cancelDownload(uniqueWorkName)
            if (_downloadState.value.isDownloading) {
                _downloadState.value = DownloadState()
            }
        }
    }

    /**
     * Get progress for a specific download by UUID.
     *
     * @param downloadId UUID of the work request
     */
    fun getDownloadProgress(downloadId: UUID) {
        viewModelScope.launch {
            downloadManager.getDownloadProgress(downloadId).collect { progress ->
                _downloadState.value = DownloadState(
                    isDownloading = !progress.isSucceeded && !progress.isFailed && !progress.isCancelled,
                    progress = progress.progressPercentInt,
                    fileName = progress.fileName,
                    bytesDownloaded = progress.bytesDownloaded,
                    totalBytes = progress.totalBytes,
                    speed = progress.speedReadable,
                    downloadedReadable = progress.downloadedReadable,
                    totalReadable = progress.totalReadable,
                    status = progress.status,
                    runAttemptCount = progress.runAttemptCount
                )
            }
        }
    }

    /**
     * Get progress for a specific download by unique work name.
     *
     * @param uniqueWorkName Unique identifier for the download
     */
    fun getDownloadProgress(uniqueWorkName: String) {
        viewModelScope.launch {
            downloadManager.getDownloadProgress(uniqueWorkName).collect { progress ->
                val resolvedProgress = progress ?: return@collect
                _downloadState.value = DownloadState(
                    isDownloading = !resolvedProgress.isSucceeded && !resolvedProgress.isFailed && !resolvedProgress.isCancelled,
                    progress = resolvedProgress.progressPercentInt,
                    fileName = resolvedProgress.fileName,
                    bytesDownloaded = resolvedProgress.bytesDownloaded,
                    totalBytes = resolvedProgress.totalBytes,
                    speed = resolvedProgress.speedReadable,
                    downloadedReadable = resolvedProgress.downloadedReadable,
                    totalReadable = resolvedProgress.totalReadable,
                    status = resolvedProgress.status,
                    runAttemptCount = resolvedProgress.runAttemptCount
                )
            }
        }
    }

    /**
     * Reset the download state.
     */
    fun resetState() {
        _downloadState.value = DownloadState()
    }
}

/**
 * UI state for download operations.
 */
data class DownloadState(
    val isDownloading: Boolean = false,
    val progress: Int = 0,
    val fileName: String = "",
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val speed: String = "",
    val downloadedReadable: String = "",
    val totalReadable: String = "",
    val status: ModelDownloadManager.DownloadStatus = ModelDownloadManager.DownloadStatus.UNKNOWN,
    val runAttemptCount: Int = 0,
    val error: String? = null
) {
    /**
     * True if the download succeeded.
     */
    val isSuccess: Boolean
        get() = status == ModelDownloadManager.DownloadStatus.SUCCESS

    /**
     * True if the download failed.
     */
    val isFailed: Boolean
        get() = status == ModelDownloadManager.DownloadStatus.FAILED

    /**
     * True if the download was cancelled.
     */
    val isCancelled: Boolean
        get() = status == ModelDownloadManager.DownloadStatus.CANCELLED

    /**
     * True if the download is enqueued (waiting to start).
     */
    val isEnqueued: Boolean
        get() = status == ModelDownloadManager.DownloadStatus.ENQUEUED

    /**
     * True if the download is blocked (constraints not met).
     */
    val isBlocked: Boolean
        get() = status == ModelDownloadManager.DownloadStatus.BLOCKED

    /**
     * Status text for display in UI.
     */
    val statusText: String
        get() = when (status) {
            ModelDownloadManager.DownloadStatus.ENQUEUED -> "Waiting..."
            ModelDownloadManager.DownloadStatus.RUNNING -> "Downloading..."
            ModelDownloadManager.DownloadStatus.SUCCESS -> "Complete"
            ModelDownloadManager.DownloadStatus.FAILED -> "Failed${error?.let { ": $it" } ?: ""}"
            ModelDownloadManager.DownloadStatus.CANCELLED -> "Cancelled"
            ModelDownloadManager.DownloadStatus.BLOCKED -> "Blocked - Check connection/battery"
            ModelDownloadManager.DownloadStatus.UNKNOWN -> ""
        }
}
