package com.shadowai.app.ai

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers GGUF models that users make available via SAF, MediaStore, or legacy storage and
 * migrates them into the application's private storage directory safely.
 */
@Singleton
class ModelMigrationManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver
    private val destinationDir = File(appContext.filesDir, "models").apply { if (!exists()) mkdirs() }

    companion object {
        private const val TAG = "ModelMigration"
        private const val GGUF_MAGIC = 0x46554747
        private const val BUFFER_SIZE = 128 * 1024
        private const val MAX_FILE_SIZE = 10L * 1024 * 1024 * 1024
    }

    /**
     * Progress emitted while a model is copied into app private storage.
     */
    data class MigrationProgress(
        val sourceUri: Uri,
        val fileName: String,
        val bytesProcessed: Long,
        val totalBytes: Long,
        val status: Status,
        val error: String? = null
    ) {
        enum class Status { DISCOVERING, VALIDATING, COPYING, CLEANING_UP, COMPLETED, FAILED }
        val percent: Int get() = if (totalBytes > 0) ((bytesProcessed * 100) / totalBytes).toInt() else 0
    }

    /**
     * Model descriptor returned by discovery flows.
     */
    data class DiscoveredModel(
        val uri: Uri,
        val displayName: String,
        val size: Long,
        val location: SourceLocation
    ) {
        enum class SourceLocation {
            SAF_TREE,
            MEDIA_STORE,
            LEGACY_FILE
        }
    }

    /**
     * Discovers GGUF models exposed through SAF, MediaStore, or legacy storage.
     */
    suspend fun discoverModels(grantedSafTreeUri: Uri? = null): List<DiscoveredModel> =
        withContext(Dispatchers.IO) {
            val discovered = mutableListOf<DiscoveredModel>()

            grantedSafTreeUri?.let { discovered.addAll(scanSafTree(it)) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                discovered.addAll(scanMediaStore())
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasAllFilesPermission()) {
                discovered.addAll(scanLegacyStorage())
            }

            discovered.distinctBy { it.displayName.lowercase() }
        }

    /**
     * Runs migration for each discovered model.
     */
    fun migrateModels(
        models: List<DiscoveredModel>,
        verifySignature: Boolean = true,
        deleteSourceAfterCopy: Boolean = false
    ): Flow<MigrationProgress> = flow {
        for (model in models) {
            try {
                emit(MigrationProgress(model.uri, model.displayName, 0, model.size, MigrationProgress.Status.DISCOVERING))

                if (verifySignature) {
                    emit(MigrationProgress(model.uri, model.displayName, 0, model.size, MigrationProgress.Status.VALIDATING))
                    if (!validateGgufSignature(model.uri)) {
                        throw SecurityException("File is not a valid GGUF model: ${model.displayName}")
                    }
                }

                atomicMigrate(model, deleteSourceAfterCopy) { bytesProcessed ->
                    emit(MigrationProgress(model.uri, model.displayName, bytesProcessed, model.size, MigrationProgress.Status.COPYING))
                }

                emit(MigrationProgress(model.uri, model.displayName, model.size, model.size, MigrationProgress.Status.COMPLETED))
            } catch (e: Exception) {
                Log.e(TAG, "Migration failed for ${model.displayName}", e)
                emit(MigrationProgress(model.uri, model.displayName, 0, model.size, MigrationProgress.Status.FAILED, e.message))
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun atomicMigrate(
        model: DiscoveredModel,
        deleteSource: Boolean,
        onProgress: suspend (Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val safeFileName = sanitizeFileName(model.displayName)
        val tempFile = File(destinationDir, ".$safeFileName.tmp")
        val finalFile = File(destinationDir, safeFileName)

        if (finalFile.exists()) {
            val sourceHash = computeHash(model.uri)
            val destHash = computeHash(finalFile)
            if (sourceHash == destHash) {
                Log.i(TAG, "Identical file already exists: $safeFileName")
                return@withContext finalFile
            }
            throw IOException("File conflict: $safeFileName exists with different content")
        }

        try {
            copyWithProgress(model.uri, tempFile, model.size, onProgress)

            if (tempFile.length() != model.size) {
                throw IOException("Size mismatch after copy: expected ${model.size}, got ${tempFile.length()}")
            }

            if (!tempFile.renameTo(finalFile)) {
                throw IOException("Failed to finalize model: atomic rename failed")
            }

            if (deleteSource) {
                deleteSourceFile(model)
            }

            finalFile
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun scanSafTree(treeUri: Uri): List<DiscoveredModel> {
        val models = mutableListOf<DiscoveredModel>()
        val tree = DocumentFile.fromTreeUri(appContext, treeUri) ?: return emptyList()

        fun scanDirectory(dir: DocumentFile) {
            dir.listFiles().forEach { file ->
                when {
                    file.isDirectory -> scanDirectory(file)
                    file.name?.lowercase()?.endsWith(".gguf") == true -> {
                        models.add(
                            DiscoveredModel(
                                uri = file.uri,
                                displayName = file.name ?: return@forEach,
                                size = file.length(),
                                location = DiscoveredModel.SourceLocation.SAF_TREE
                            )
                        )
                    }
                }
            }
        }

        scanDirectory(tree)
        return models
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun scanMediaStore(): List<DiscoveredModel> {
        val models = mutableListOf<DiscoveredModel>()
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE
        )
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%.gguf")

        contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val size = cursor.getLong(sizeCol)

                val contentUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL, id)
                models.add(
                    DiscoveredModel(
                        uri = contentUri,
                        displayName = name,
                        size = size,
                        location = DiscoveredModel.SourceLocation.MEDIA_STORE
                    )
                )
            }
        }

        return models
    }

    private fun scanLegacyStorage(): List<DiscoveredModel> {
        val models = mutableListOf<DiscoveredModel>()
        val scanRoots = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStorageDirectory(), "Models"),
            File(Environment.getExternalStorageDirectory(), "GGUF"),
            File(Environment.getExternalStorageDirectory(), "AI")
        ).filter { it.exists() && it.isDirectory }

        scanRoots.forEach { root ->
            root.walkTopDown().maxDepth(2).filter { it.isFile && it.extension.lowercase() == "gguf" }
                .forEach { file ->
                    val canonical = file.canonicalFile
                    if (scanRoots.any { canonical.startsWith(it) }) {
                        models.add(
                            DiscoveredModel(
                                uri = Uri.fromFile(file),
                                displayName = file.name,
                                size = file.length(),
                                location = DiscoveredModel.SourceLocation.LEGACY_FILE
                            )
                        )
                    }
                }
        }

        return models
    }

    private fun validateGgufSignature(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(4)
                if (stream.read(buffer) != 4) return false
                val magic = (buffer[0].toInt() and 0xFF) or
                    ((buffer[1].toInt() and 0xFF) shl 8) or
                    ((buffer[2].toInt() and 0xFF) shl 16) or
                    ((buffer[3].toInt() and 0xFF) shl 24)
                magic == GGUF_MAGIC
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Signature validation failed", e)
            false
        }
    }

    private suspend fun copyWithProgress(
        sourceUri: Uri,
        destFile: File,
        totalSize: Long,
        onProgress: suspend (Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (totalSize > MAX_FILE_SIZE) throw IOException("File exceeds maximum size limit")

        contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                val bufferedInput = BufferedInputStream(input, BUFFER_SIZE)
                val bufferedOutput = BufferedOutputStream(output, BUFFER_SIZE)
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                var totalRead = 0L
                var lastProgressUpdate = 0L

                while (bufferedInput.read(buffer).also { bytesRead = it } != -1) {
                    bufferedOutput.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalRead - lastProgressUpdate > 256 * 1024) {
                        onProgress(totalRead)
                        lastProgressUpdate = totalRead
                    }
                }
                bufferedOutput.flush()
            }
        } ?: throw IOException("Cannot open input stream for $sourceUri")
    }

    private fun computeHash(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun computeHash(file: File): String = computeHash(Uri.fromFile(file))

    private fun deleteSourceFile(model: DiscoveredModel) {
        when (model.location) {
            DiscoveredModel.SourceLocation.SAF_TREE -> DocumentFile.fromSingleUri(appContext, model.uri)?.delete()
            DiscoveredModel.SourceLocation.MEDIA_STORE -> try {
                contentResolver.delete(model.uri, null, null)
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot delete MediaStore entry without write permission", e)
            }
            DiscoveredModel.SourceLocation.LEGACY_FILE -> File(model.uri.path!!).delete()
        }
    }

    private fun sanitizeFileName(name: String): String {
        var clean = name.substringAfterLast('/').substringAfterLast('\\')
        clean = clean.replace(Regex("[\\x00-\\x1F\\x7F]"), "")
        if (!clean.lowercase().endsWith(".gguf")) {
            clean += ".gguf"
        }
        val testFile = File(destinationDir, clean)
        if (!testFile.canonicalPath.startsWith(destinationDir.canonicalPath)) {
            throw SecurityException("Path traversal detected in filename: $name")
        }
        return clean
    }

    private fun hasAllFilesPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
    }

    /**
     * Human friendly description for the UI.
     */
    fun getSourceDescription(model: DiscoveredModel): String {
        return when (model.location) {
            DiscoveredModel.SourceLocation.SAF_TREE -> "Selected folder"
            DiscoveredModel.SourceLocation.MEDIA_STORE -> "Downloads"
            DiscoveredModel.SourceLocation.LEGACY_FILE -> "External Storage"
        }
    }
}
