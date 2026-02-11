# WorkManager Model Downloads

This directory contains the implementation for background GGUF model downloads using Android's WorkManager.

## Features

- **Background Downloads**: Downloads continue even if the app is closed or the process is killed
- **Automatic Retry**: Exponential backoff retry logic (up to 3 attempts with 10-minute backoff)
- **Battery Awareness**: Only downloads when battery is not low
- **Network Awareness**: WiFi-only downloads to preserve mobile data
- **Progress Tracking**: Real-time progress updates via Flow
- **Notifications**: Progress notifications with percentage, speed, and file size
- **Resume Support**: Automatic resume of interrupted downloads

## Components

### 1. ModelDownloadWorker

A WorkManager worker that handles the actual model download in the background.

**Key Features:**
- Survives app closure and process death
- Automatic retry with exponential backoff
- Progress updates via `setProgressAsync`
- Optional progress notifications
- Wraps existing `ModelDownloader` functionality

### 2. ModelDownloadManager

A high-level manager that enqueues and tracks download work.

**Key Features:**
- Enqueue downloads with constraints (WiFi, battery)
- Track download progress via Flow
- Cancel downloads by UUID or unique name
- Get all active downloads
- Human-readable progress formatting

### 3. DownloadNotificationManager

Manages notifications for download progress and completion.

**Key Features:**
- Progress notifications with percentage and speed
- Success/failure notifications
- Click to open app
- Ongoing notification during active downloads
- Support for multiple concurrent downloads

### 4. DownloadViewModel

ViewModel for managing downloads in the UI.

**Key Features:**
- Start/cancel downloads
- Track progress via StateFlow
- Clean UI state management
- Compose integration ready

### 5. WorkManagerModule

Hilt module that provides WorkManager with HiltWorkerFactory integration.

## Usage

### Starting a Download

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val downloadManager: ModelDownloadManager
) : ViewModel() {

    fun downloadModel(modelUrl: String, fileName: String) {
        viewModelScope.launch {
            val downloadId = downloadManager.downloadModel(
                modelUrl = modelUrl,
                fileName = fileName
            )

            // Track progress
            downloadManager.getDownloadProgress(downloadId).collect { progress ->
                when {
                    progress.isRunning -> {
                        // Update UI with progress
                        Log.d("Download", "${progress.progressPercentInt}% downloaded")
                    }
                    progress.isSucceeded -> {
                        // Download complete
                        Log.d("Download", "Complete: ${progress.downloadedReadable}")
                    }
                    progress.isFailed -> {
                        // Download failed
                        Log.d("Download", "Failed, attempts: ${progress.runAttemptCount}")
                    }
                }
            }
        }
    }
}
```

### Using DownloadViewModel (Simpler Approach)

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val downloadViewModel: DownloadViewModel
) : ViewModel() {

    fun downloadModel(modelUrl: String, fileName: String) {
        downloadViewModel.startDownload(modelUrl, fileName)
    }
}
```

### In Compose UI

```kotlin
@Composable
fun ModelDownloadScreen(
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val downloadState by viewModel.downloadState.collectAsState()

    Column {
        if (!downloadState.isDownloading && !downloadState.isSuccess) {
            Button(
                onClick = {
                    viewModel.startDownload(
                        modelUrl = "https://example.com/model.gguf",
                        fileName = "my-model.gguf"
                    )
                }
            ) {
                Text("Download Model")
            }
        }

        if (downloadState.isDownloading) {
            LinearProgressIndicator(
                progress = downloadState.progress / 100f
            )
            Text(
                text = "${downloadState.progress}% - ${downloadState.downloadedReadable} / ${downloadState.totalReadable}"
            )
            Text(
                text = downloadState.speed,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (downloadState.isSuccess) {
            Text(
                text = "Download complete: ${downloadState.fileName}",
                color = Color.Green
            )
        }

        if (downloadState.isFailed) {
            Text(
                text = downloadState.statusText,
                color = Color.Red
            )
        }

        if (downloadState.isDownloading) {
            Button(
                onClick = { viewModel.cancelDownload() }
            ) {
                Text("Cancel")
            }
        }
    }
}
```

### Cancelling Downloads

```kotlin
// Cancel by UUID
downloadManager.cancelDownload(downloadId)

// Cancel by unique name
downloadManager.cancelDownload("my-model-download")

// Cancel all downloads
downloadManager.cancelAllDownloads()
```

### Tracking All Downloads

```kotlin
viewModelScope.launch {
    downloadManager.getAllDownloads().collect { downloads ->
        downloads.forEach { progress ->
            Log.d("Download", "${progress.fileName}: ${progress.progressPercentInt}%")
        }
    }
}
```

## Constraints

Download work is configured with the following constraints:

- **Network**: `NetworkType.UNMETERED` (WiFi only)
- **Battery**: `requiresBatteryNotLow = true`
- **Retry Policy**: Exponential backoff, 10 minutes, maximum 3 retries

## Notifications

Progress notifications are shown automatically when downloads are active. To disable notifications, don't inject `DownloadNotificationManager` into `ModelDownloadWorker` (set it to null in the module).

## Testing

### Manual Testing

1. Start a download
2. Close the app completely (swipe from recent apps)
3. Reopen the app after a few seconds
4. The download should either be:
   - Complete (if the download finished while app was closed)
   - In progress (if the download is still running)

### Expected Behavior

- Downloads survive app closure
- Downloads resume after network reconnection
- Downloads retry automatically on failure
- Only WiFi downloads (not on mobile data)
- Only when battery is not low

## Architecture

```
┌─────────────────────────────────────────┐
│         DownloadViewModel               │
│  (UI State Management & User Actions)   │
└──────────────────┬──────────────────────┘
                   │
                   │ Start/Cancel/Track
                   │
┌──────────────────▼──────────────────────┐
│       ModelDownloadManager              │
│  (High-level API for WorkManager)       │
└──────────────────┬──────────────────────┘
                   │
                   │ Enqueue Work
                   │
┌──────────────────▼──────────────────────┐
│         WorkManager                     │
│  (System Background Work Scheduling)    │
└──────────────────┬──────────────────────┘
                   │
                   │ Execute Worker
                   │
┌──────────────────▼──────────────────────┐
│      ModelDownloadWorker                │
│  (Coroutines + Retry + Progress)        │
└──────────────────┬──────────────────────┘
                   │
                   │ Download File
                   │
┌──────────────────▼──────────────────────┐
│        ModelDownloader                  │
│  (HTTP Download + Resume + Verify)      │
└─────────────────────────────────────────┘
```

## Troubleshooting

### Download Not Starting

- Check that you're connected to WiFi (not mobile data)
- Ensure battery is not low (< 15%)
- Check logs: `adb logcat | grep ModelDownloadWorker`

### Download Stuck at "Blocked"

- Check network connection
- Ensure you're not on metered network
- Wait for battery to charge if low

### Download Fails Repeatedly

- Check the URL is accessible
- Verify sufficient storage space (at least 100MB more than model size)
- Check logs for specific error messages
- Downloads will retry up to 3 times with exponential backoff

### Progress Not Updating in UI

- Ensure you're collecting the Flow from `getDownloadProgress()`
- Check that you're in a coroutine scope
- Use `collectAsState()` in Compose or `launch` to collect Flow

## License

Part of ShadowAi project.