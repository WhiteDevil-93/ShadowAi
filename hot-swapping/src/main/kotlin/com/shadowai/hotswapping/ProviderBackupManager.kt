package com.shadowai.hotswapping

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages backups for provider configuration snapshots.
 */
class ProviderBackupManager(
    private val baseDir: File,
    private val loader: ProviderConfigLoader
) {
    private val backupDir = File(baseDir, "backups")

    /**
     * Creates a backup file from the snapshot.
     */
    suspend fun createBackup(snapshot: ProviderConfigSnapshot, format: ConfigFormat): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!backupDir.exists()) {
                    backupDir.mkdirs()
                }
                val timestamp = System.currentTimeMillis()
                val extension = if (format == ConfigFormat.JSON) "json" else "yaml"
                val file = File(backupDir, "provider_backup_$timestamp.$extension")
                file.writeText(loader.serialize(snapshot, format))
                file
            }
        }

    /**
     * Restores the most recent backup.
     */
    suspend fun restoreLatest(): Result<ProviderConfigSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val latest = backupDir.listFiles()?.maxByOrNull { it.lastModified() }
                ?: throw IllegalStateException("No backups available")
            loader.parse(latest.readText(), detectFormat(latest))
        }
    }

    /**
     * Lists available backups.
     */
    fun listBackups(): List<File> {
        return backupDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    private fun detectFormat(file: File): ConfigFormat {
        return if (file.extension.lowercase() == "json") ConfigFormat.JSON else ConfigFormat.YAML
    }
}
