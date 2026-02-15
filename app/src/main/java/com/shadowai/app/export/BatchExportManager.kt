package com.shadowai.app.export

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.shadowai.app.db.MessageDao
import com.shadowai.app.ui.ChatMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import com.shadowai.app.ui.chat.export.ExportManager
import com.shadowai.app.ui.chat.export.ExportFormat

/**
 * Manages batch export of multiple conversations.
 */
@Singleton
class BatchExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportManager: ExportManager,
    private val messageDao: MessageDao,
    private val gson: Gson
) {

    companion object {
        private const val TAG = "BatchExportManager"
    }

    suspend fun exportBatch(
        conversationIds: List<String>,
        format: com.shadowai.app.export.ConversationExporter.ExportFormat
    ): com.shadowai.app.export.ConversationExporter.ExportResult = withContext(Dispatchers.IO) {
        try {
            val exportDir = File(context.cacheDir, "exports/batch").apply { mkdirs() }
            val zipFile = File(exportDir, "shadowai_batch_export_${System.currentTimeMillis()}.zip")

            val appExportFormat = when(format) {
                com.shadowai.app.export.ConversationExporter.ExportFormat.PDF -> ExportFormat.PDF
                com.shadowai.app.export.ConversationExporter.ExportFormat.JSON -> ExportFormat.JSON
                com.shadowai.app.export.ConversationExporter.ExportFormat.MARKDOWN -> ExportFormat.MARKDOWN
            }

            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                conversationIds.forEach { conversationId ->
                    val messages = messageDao.getAllMessages()

                    if (messages.isNotEmpty()) {
                        val extension = exportManager.getFormatExtension(appExportFormat)
                        val entryName = "conversation_$conversationId.$extension"
                        zipOut.putNextEntry(ZipEntry(entryName))

                        val content: ByteArray = when (appExportFormat) {
                            ExportFormat.PDF -> ByteArray(0)
                            ExportFormat.MARKDOWN -> exportManager.createMarkdownContent("Conversation $conversationId", messages).toByteArray(Charsets.UTF_8)
                            ExportFormat.JSON -> exportManager.createJsonContent("Conversation $conversationId", messages).toByteArray(Charsets.UTF_8)
                            ExportFormat.TXT -> exportManager.createTxtContent("Conversation $conversationId", messages).toByteArray(Charsets.UTF_8)
                            ExportFormat.ZIP -> ByteArray(0)
                        }

                        zipOut.write(content)
                        zipOut.closeEntry()
                    }
                }
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                zipFile
            )

            com.shadowai.app.export.ConversationExporter.ExportResult(true, uri)
        } catch (e: Exception) {
            Log.e(TAG, "Batch export failed", e)
            com.shadowai.app.export.ConversationExporter.ExportResult(false, null, e.message)
        }
    }
}
