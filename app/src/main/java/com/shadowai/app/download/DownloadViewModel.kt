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
                    val progressValue = progress
                    _downloadState.value = DownloadState(
                        isDownloading = !progressValue.isSucceeded && !progressValue.isFailed && !progressValue.isCancelled,
                        progress = progressValue.progressPercentInt,
                        fileName = progressValue.fileName,
                        bytesDownloaded = progressValue.bytesDownloaded,
                        totalBytes = progressValue.totalBytes,
                        speed = progressValue.speedReadable,
                        downloadedReadable = progressValue.downloadedReadable,
                        totalReadable = progressValue.totalReadable,
                        status = progressValue.status,
                        runAttemptCount = progressValue.runAttemptCount
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
)
