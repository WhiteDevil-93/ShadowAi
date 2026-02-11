package com.shadowai.app.ui.providers

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.shadowai.core.ProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Validates the host input format.
 */
private fun isValidHost(host: String): Boolean {
    return host.isNotBlank() && !host.contains(" ") && (host.matches(Regex("^[a-zA-Z0-9.-]+$")) || host.matches(Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")))
}

/**
 * Validates the port input (must be 1-65535).
 */
private fun isValidPort(port: String): Boolean {
    val portInt = port.toIntOrNull()
    return portInt != null && portInt in 1..65535
}

private data class ImportResult(
    val importedCount: Int,
    val skippedCount: Int,
    val linkedCount: Int,
    val message: String
)

private enum class ImportMode {
    IMPORT_FOLDER,    // Folder picker (SAF) - for SD cards, app dirs, NOT Downloads
    IMPORT_FILES,     // Multi-file picker - works with Downloads on Android 11+
    LINK_SAF          // Link SAF tree URI for direct access (copy-on-load)
}

private enum class PickerMode {
    TREE,    // OpenDocumentTree - folders
    DOCUMENT // OpenDocument - single/multiple files
}

private fun resolveAppModelDir(context: Context): File {
    val external = context.getExternalFilesDir("models")
    val target = external ?: File(context.filesDir, "models")
    if (!target.exists()) {
        target.mkdirs()
    }
    return target
}

private fun importOrLinkGgufFromTree(
    context: Context,
    treeUri: android.net.Uri,
    targetDir: File,
    mode: ImportMode
): ImportResult {
    val contentResolver = context.contentResolver
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    try {
        contentResolver.takePersistableUriPermission(treeUri, flags)
    } catch (e: SecurityException) {
        return ImportResult(0, 0, 0, "Permission denied for selected folder.")
    }

    val root = DocumentFile.fromTreeUri(context, treeUri)
        ?: return ImportResult(0, 0, 0, "Unable to access selected folder.")

    val ggufFiles = root.listFiles()
        .filter { it.isFile && (it.name?.endsWith(".gguf", ignoreCase = true) == true) }

    if (ggufFiles.isEmpty()) {
        return ImportResult(0, 0, 0, "No .gguf files found in selected folder.")
    }

    // If linking, just return success - files stay in place
    if (mode == ImportMode.LINK_SAF) {
        val count = ggufFiles.size
        return ImportResult(
            0, 0, count,
            "Linked $count model(s) from ${root.name ?: "selected folder"}. " +
            "Models will be copied on-demand when loaded."
        )
    }

    // Import mode: copy files to app storage
    var imported = 0
    var skipped = 0

    ggufFiles.forEach { docFile ->
        val name = docFile.name ?: return@forEach
        val targetFile = File(targetDir, name)
        if (targetFile.exists()) {
            skipped += 1
            return@forEach
        }
        try {
            contentResolver.openInputStream(docFile.uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: run { skipped += 1 }
            imported += 1
        } catch (e: Exception) {
            skipped += 1
        }
    }

    val summary = "Imported $imported model(s) to ${targetDir.absolutePath}."
    val details = if (skipped > 0) " Skipped $skipped existing or unreadable file(s)." else ""
    return ImportResult(imported, skipped, 0, summary + details)
}

/**
 * Local Runtime Configuration - Implements UI Orchestrator governance
 *
 * GOVERNANCE COMPLIANCE:
 * - Runtime status indicator
 * - Host field
 * - Port field
 * - Test connection button
 * - Installed models list
 * - NO API key field (FORBIDDEN for local providers)
 * - NO OAuth (FORBIDDEN for local providers)
 * - NO credits display (FORBIDDEN for local providers)
 * - Material 3 compliance
 *
 * HARD SEPARATION RULES:
 * - Local providers MUST NOT have API key field
 * - Local providers MUST NOT have OAuth
 * - Local providers MUST NOT show credits
 * - Local providers MUST show runtime status
 * - Local providers MUST show installed models
 *
 * FORBIDDEN:
 * - ❌ API key field
 * - ❌ OAuth buttons
 * - ❌ Credits display
 * - ❌ Usage tracking
 *
 * @param providerId The local provider to configure
 * @param installedModels List of installed model names
 * @param onSaveConfig Callback when configuration is saved
 * @param modifier Optional modifier
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun LocalRuntimeConfig(
    providerId: ProviderId,
    initialConfig: String,
    installedModels: List<String>,
    onSaveConfig: (String) -> Unit,
    onTreeUriSet: (android.net.Uri?) -> Unit = {},  // New callback for SAF tree URI
    onModelsImported: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appModelDir = remember { resolveAppModelDir(context) }
    var host by remember { mutableStateOf("localhost") }
    var port by remember { mutableStateOf("11434") }
    var modelPath by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var importSuccess by remember { mutableStateOf(false) }
    var linkedTreeUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Parse initial config
    LaunchedEffect(initialConfig) {
        if (initialConfig.isNotBlank()) {
            if (providerId == ProviderId.LIQUID) {
                if (initialConfig != "liquid-inference") {
                    modelPath = initialConfig
                }
            } else if (initialConfig.startsWith("http")) {
                val uri = java.net.URI.create(initialConfig)
                host = uri.host ?: "localhost"
                port = if (uri.port != -1) uri.port.toString() else "11434"
            } else if (initialConfig.contains(":")) {
                val parts = initialConfig.split(":")
                host = parts[0]
                port = parts.getOrNull(1) ?: "11434"
            }
        }
    }

    var hostError by remember { mutableStateOf<String?>(null) }
    var portError by remember { mutableStateOf<String?>(null) }
    var pathError by remember { mutableStateOf<String?>(null) }
    var runtimeStatus by remember { mutableStateOf<RuntimeStatus>(RuntimeStatus.Unknown) }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // HTTP client for connection testing
    val httpClient = remember { OkHttpClient.Builder().build() }

    // Auto-update status based on models presence
    LaunchedEffect(installedModels) {
        if (installedModels.isNotEmpty()) {
            runtimeStatus = RuntimeStatus.Running
        }
    }

    // Function to test connection via HTTP
    fun testConnectionHttp() {
        coroutineScope.launch {
            isRefreshing = true
            val url = "http://$host:$port/api/version"

            val result = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .build()

                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        RuntimeStatus.Running
                    } else {
                        // Try alternative endpoints
                        testAlternativeEndpoints(httpClient, host, port)
                    }
                } catch (e: IOException) {
                    // Try alternative endpoints
                    testAlternativeEndpoints(httpClient, host, port)
                } catch (e: Exception) {
                    RuntimeStatus.Stopped
                }
            }

            runtimeStatus = result
            isRefreshing = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Information card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Local Runtime Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Configure connection to your ${providerId.name} runtime. Connect via LAN, Tailscale, or localhost.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Runtime status
        RuntimeStatusCard(
            status = runtimeStatus,
            onRefresh = {
                if (providerId == ProviderId.LIQUID) {
                    // For Liquid, refresh just triggers a recompose/re-scan which is handled by ViewModel
                } else {
                    testConnectionHttp()
                }
            },
            isRefreshing = isRefreshing
        )

        if (providerId == ProviderId.LIQUID) {
            var importMode by remember { mutableStateOf(ImportMode.IMPORT_FILES) }

            // Folder picker launcher (for IMPORT_FOLDER and LINK_SAF)
            val folderLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri != null) {
                    coroutineScope.launch {
                        isImporting = true
                        importMessage = null
                        val result = withContext(Dispatchers.IO) {
                            importOrLinkGgufFromTree(context, uri, appModelDir, importMode)
                        }
                        isImporting = false

                        when (importMode) {
                            ImportMode.IMPORT_FOLDER -> {
                                if (result.importedCount > 0) {
                                    modelPath = appModelDir.absolutePath
                                    pathError = null
                                    importSuccess = true
                                    onModelsImported()
                                }
                            }
                            ImportMode.LINK_SAF -> {
                                linkedTreeUri = uri
                                onTreeUriSet(uri)
                                modelPath = "SAF:${uri.toString().take(50)}..."
                                pathError = null
                                importSuccess = true
                            }
                            else -> { /* IMPORT_FILES uses different launcher */ }
                        }

                        importMessage = result.message
                    }
                }
            }

            // Multi-file picker launcher (for IMPORT_FILES - works with Downloads on Android 11+)
            val fileLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenMultipleDocuments()
            ) { uris ->
                if (uris.isNotEmpty()) {
                    coroutineScope.launch {
                        isImporting = true
                        importMessage = null
                        
                        val result = withContext(Dispatchers.IO) {
                            importMultipleFiles(context, uris, appModelDir)
                        }
                        
                        isImporting = false
                        if (result.importedCount > 0) {
                            modelPath = appModelDir.absolutePath
                            pathError = null
                            importSuccess = true
                            onModelsImported()
                        }
                        importMessage = result.message
                    }
                }
            }

            // Model Path field for local inference
            OutlinedTextField(
                value = modelPath,
                onValueChange = {
                    modelPath = it
                    pathError = if (it.isNotBlank() && !it.startsWith("/") && !it.startsWith("SAF:")) "Complete path required (starting with /)" else null
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = "Model Directory Path",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                placeholder = {
                    Text(
                        text = appModelDir.absolutePath,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                supportingText = {
                    Column {
                        Text(
                            text = "Absolute path to the folder containing your .gguf models",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Tip: Use 'Import Files' to pick models from Downloads (Android 11+)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                isError = pathError != null,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "App model storage: ${appModelDir.absolutePath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Import mode selector - 3 options
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = importMode == ImportMode.IMPORT_FILES,
                        onClick = { importMode = ImportMode.IMPORT_FILES },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) {
                        Text("Import Files")
                    }
                    SegmentedButton(
                        selected = importMode == ImportMode.IMPORT_FOLDER,
                        onClick = { importMode = ImportMode.IMPORT_FOLDER },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) {
                        Text("Import Folder")
                    }
                    SegmentedButton(
                        selected = importMode == ImportMode.LINK_SAF,
                        onClick = { importMode = ImportMode.LINK_SAF },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) {
                        Text("Link (SAF)")
                    }
                }

                // Mode description
                Text(
                    text = when (importMode) {
                        ImportMode.IMPORT_FILES -> "Pick individual .gguf files. Works with Downloads folder on Android 11+."
                        ImportMode.IMPORT_FOLDER -> "Pick entire folder. Best for SD cards, USB drives (NOT Downloads)."
                        ImportMode.LINK_SAF -> "Link folder via SAF. Keeps files in place. Copies on-demand when loading."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        when (importMode) {
                            ImportMode.IMPORT_FILES -> {
                                // Use MIME type filter for .gguf files
                                fileLauncher.launch(arrayOf("*/*"))
                            }
                            ImportMode.IMPORT_FOLDER, ImportMode.LINK_SAF -> {
                                folderLauncher.launch(null)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isImporting
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = when (importMode) {
                            ImportMode.IMPORT_FILES -> if (isImporting) "Importing..." else "Select .gguf File(s)"
                            ImportMode.IMPORT_FOLDER -> if (isImporting) "Importing..." else "Select Folder to Import"
                            ImportMode.LINK_SAF -> if (isImporting) "Linking..." else "Link Folder (SAF)"
                        }
                    )
                }
                importMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Dismissible import success status
                if (importSuccess) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Models Imported",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Rescan triggered to update catalog",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            TextButton(
                                onClick = { importSuccess = false }
                            ) {
                                Text(
                                    text = "Dismiss",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Save path button
            Button(
                onClick = { onSaveConfig(modelPath) },
                modifier = Modifier.fillMaxWidth(),
                enabled = pathError == null && modelPath.isNotBlank()
            ) {
                Text(
                    text = "Save Model Path",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        if (providerId != ProviderId.LIQUID) {
            // Host field
            OutlinedTextField(
                value = host,
                onValueChange = { newValue ->
                    host = newValue
                    hostError = if (isValidHost(newValue)) null else "Invalid hostname format"
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = "Host",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                placeholder = {
                    Text(
                        text = "e.g., 192.168.1.5 or 100.x.x.x (Tailscale)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                supportingText = {
                    Column {
                        Text(
                            text = "Hostname or IP address (LAN, Tailscale, or remote server)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (host == "localhost" || host == "127.0.0.1") {
                            Text(
                                text = "Note: 'localhost' refers to this Android device. Use your computer's LAN IP or Tailscale IP (100.x.x.x) instead.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                isError = hostError != null,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Port field
            OutlinedTextField(
                value = port,
                onValueChange = { newValue ->
                    // Filter to only digits
                    val filtered = newValue.filter { it.isDigit() }
                    port = filtered
                    portError = if (filtered.isEmpty()) "Port is required" else if (isValidPort(filtered)) null else "Port must be 1-65535"
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = "Port",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                placeholder = {
                    Text(
                        text = "11434",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                supportingText = {
                    Text(portError ?: "Port number the runtime is listening on (Default: 11434)")
                },
                isError = portError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Test connection button
            FilledTonalButton(
                onClick = { testConnectionHttp() },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    Text(
                        text = "Test Connection",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        if (providerId != ProviderId.LIQUID) {
            // Save configuration button
            Button(
                onClick = { onSaveConfig("$host:$port") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Save Configuration",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // Installed models (if runtime is running)
        if (runtimeStatus == RuntimeStatus.Running && installedModels.isNotEmpty()) {
             InstalledModelsCard(models = installedModels)
        }
    }
}

/**
 * Test alternative endpoints for runtime connectivity
 */
private suspend fun testAlternativeEndpoints(httpClient: OkHttpClient, host: String, port: String): RuntimeStatus {
    val endpoints = listOf(
        "http://$host:$port/api/tags",
        "http://$host:$port/v1/models",
        "http://$host:$port/models",
        "http://$host:$port/api/models"
    )

    for (endpoint in endpoints) {
        try {
            val request = Request.Builder()
                .url(endpoint)
                .get()
                .build()

            val response = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }

            if (response.isSuccessful || response.code == 200) {
                return RuntimeStatus.Running
            }
        } catch (_: Exception) {
            // Try next endpoint
        }
    }

    return RuntimeStatus.Stopped
}

/**
 * Runtime status card
 */
@Composable
private fun RuntimeStatusCard(
    status: RuntimeStatus,
    onRefresh: () -> Unit,
    isRefreshing: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                RuntimeStatus.Running -> MaterialTheme.colorScheme.tertiaryContainer
                RuntimeStatus.Stopped -> MaterialTheme.colorScheme.errorContainer
                RuntimeStatus.Unknown -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (status) {
                        RuntimeStatus.Running -> Icons.Default.Check
                        RuntimeStatus.Stopped -> Icons.Default.Error
                        RuntimeStatus.Unknown -> Icons.Default.Refresh
                    },
                    contentDescription = null,
                    tint = when (status) {
                        RuntimeStatus.Running -> MaterialTheme.colorScheme.onTertiaryContainer
                        RuntimeStatus.Stopped -> MaterialTheme.colorScheme.onErrorContainer
                        RuntimeStatus.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Column {
                    Text(
                        text = when (status) {
                            RuntimeStatus.Running -> "Runtime Running"
                            RuntimeStatus.Stopped -> "Runtime Stopped"
                            RuntimeStatus.Unknown -> "Status Unknown"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = when (status) {
                            RuntimeStatus.Running -> MaterialTheme.colorScheme.onTertiaryContainer
                            RuntimeStatus.Stopped -> MaterialTheme.colorScheme.onErrorContainer
                            RuntimeStatus.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = when (status) {
                            RuntimeStatus.Running -> "Local inference available"
                            RuntimeStatus.Stopped -> "Start the runtime to use local models"
                            RuntimeStatus.Unknown -> "Tap refresh to check status"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (status) {
                            RuntimeStatus.Running -> MaterialTheme.colorScheme.onTertiaryContainer
                            RuntimeStatus.Stopped -> MaterialTheme.colorScheme.onErrorContainer
                            RuntimeStatus.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh status"
                    )
                }
            }
        }
    }
}

/**
 * Installed models card
 */
@Composable
private fun InstalledModelsCard(models: List<String>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Installed Models (${models.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            models.forEach { model ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = model,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Import multiple individual files (for Downloads folder on Android 11+)
 */
private fun importMultipleFiles(
    context: Context,
    uris: List<android.net.Uri>,
    targetDir: File
): ImportResult {
    val contentResolver = context.contentResolver
    var imported = 0
    var skipped = 0

    uris.forEach { uri ->
        // Get file name from URI
        val name = getFileNameFromUri(context, uri)
        if (name == null || !name.endsWith(".gguf", ignoreCase = true)) {
            skipped += 1
            return@forEach
        }

        val targetFile = File(targetDir, name)
        if (targetFile.exists()) {
            skipped += 1
            return@forEach
        }

        try {
            contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: run { skipped += 1 }
            imported += 1
        } catch (e: Exception) {
            skipped += 1
        }
    }

    val summary = "Imported $imported file(s) to app storage."
    val details = if (skipped > 0) " Skipped $skipped existing or non-.gguf file(s)." else ""
    return ImportResult(imported, skipped, 0, summary + details)
}

/**
 * Extract filename from content URI
 */
private fun getFileNameFromUri(context: Context, uri: android.net.Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0) it.getString(index) else null
        } else null
    }
}

/**
 * Runtime status enum
 */
private sealed class RuntimeStatus {
    object Unknown : RuntimeStatus()
    object Running : RuntimeStatus()
    object Stopped : RuntimeStatus()
}
