package com.shadowai.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.shadowai.app.ui.ChatMessage
import com.shadowai.core.Artifact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Handles exporting conversations to various formats.
 *
 * Supported formats:
 * - PDF: formatted document with proper styling
 * - Markdown: preserves formatting and code blocks
 * - JSON: structured data for programmatic access
 * - Batch: export multiple conversations at once
 */
class ConversationExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ConversationExporter"
        private const val EXPORTS_DIR = "ShadowAi Exports"

        // PDF export using Apache PDFBox
        private const val PDF_FONT_SIZE = 12f
        private const val PDF_LINE_HEIGHT = 18f
        private const val PDF_MARGIN = 50f
        private const val PDF_MAX_WIDTH = 500f
    }

    data class ExportResult(
        val success: Boolean,
        val uri: Uri?,
        val error: String? = null
    )

    enum class ExportFormat {
        PDF,
        MARKDOWN,
        JSON
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * Export a single conversation to the specified format.
     *
     * @param messages List of chat messages
     * @param format Export format (PDF, MARKDOWN, JSON)
     * @param filename Optional custom filename (without extension)
     * @return ExportResult with success status and file URI
     */
    suspend fun exportConversation(
        messages: List<ChatMessage>,
        format: ExportFormat,
        filename: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val exportDir = getOrCreateExportDirectory()
            if (exportDir == null) {
                return@withContext ExportResult(
                    success = false,
                    uri = null,
                    error = "Failed to create export directory"
                )
            }

            val baseFilename = filename ?: "conversation_${fileDateFormat.format(Date())}"
            val file = when (format) {
                ExportFormat.PDF -> exportToPDF(messages, exportDir, baseFilename)
                ExportFormat.MARKDOWN -> exportToMarkdown(messages, exportDir, baseFilename)
                ExportFormat.JSON -> exportToJSON(messages, exportDir, baseFilename)
            }

            return@withContext ExportResult(
                success = true,
                uri = Uri.fromFile(file)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            return@withContext ExportResult(
                success = false,
                uri = null,
                error = e.message ?: "Export failed: ${e.javaClass.simpleName}"
            )
        }
    }

    /**
     * Export multiple conversations (batch export).
     *
     * @param conversations Map of conversationId to messages
     * @param format Export format
     * @return List of export results for each conversation
     */
    suspend fun exportBatch(
        conversations: Map<String, List<ChatMessage>>,
        format: ExportFormat
    ): List<ExportResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ExportResult>()

        conversations.forEach { (conversationId, messages) ->
            val filename = "conversation_${conversationId}_${fileDateFormat.format(Date())}"
            val result = exportConversation(messages, format, filename)
            results.add(result)
        }

        results
    }

    /**
     * Export conversation to Markdown format.
     * Preserves formatting, code blocks, and clean structure.
     */
    private fun exportToMarkdown(
        messages: List<ChatMessage>,
        dir: File,
        baseFilename: String
    ): File {
        val file = File(dir, "${baseFilename}.md")

        val markdown = buildString {
            appendLine("# Conversation Export\n")
            appendLine("**Generated:** ${dateFormat.format(Date())}\n")
            appendLine("---\n\n")

            messages.forEach { message ->
                val role = if (message.isUser) "**User**" else "**Shadow AI**"
                val timestamp = dateFormat.format(Date(message.timestamp))

                appendLine("### $role")
                appendLine("*$timestamp*\n")
                appendLine(formatContentForMarkdown(message.text))
                appendLine("\n---\n")
            }
        }

        file.writeText(markdown)
        Log.d(TAG, "Exported to Markdown: ${file.absolutePath}")
        return file
    }

    /**
     * Format content for Markdown export.
     * Handles code blocks, links, and special characters.
     */
    private fun formatContentForMarkdown(content: String): String {
        var formatted = content

        // Escape special characters that aren't Markdown syntax
        // We preserve formatting by not escaping markdown-specific chars

        // Ensure code blocks are properly formatted
        // If content has code fences, preserve them
        if (!formatted.contains("```")) {
            // Code blocks not already formatted, try to detect them
            formatted = formatUnformattedCode(formatted)
        }

        return formatted
    }

    /**
     * Auto-detect and format code blocks in unformatted content.
     */
    private fun formatUnformattedCode(content: String): String {
        // Simple heuristic: lines with 4+ spaces are code
        val lines = content.split("\n")
        val result = StringBuilder()
        var inCodeBlock = false

        lines.forEach { line ->
            val isCodeLine = line.startsWith("    ") || line.startsWith("\t")

            if (isCodeLine && !inCodeBlock) {
                result.appendLine("```")
                inCodeBlock = true
            } else if (!isCodeLine && inCodeBlock) {
                result.appendLine("```")
                inCodeBlock = false
            }

            result.appendLine(line)
        }

        if (inCodeBlock) {
            result.appendLine("```")
        }

        return result.toString()
    }

    /**
     * Export conversation to JSON format.
     * Structured, reliable serialization for programmatic access.
     */
    private fun exportToJSON(
        messages: List<ChatMessage>,
        dir: File,
        baseFilename: String
    ): File {
        val file = File(dir, "${baseFilename}.json")

        val jsonObject = JSONObject().apply {
            put("export_date", dateFormat.format(Date()))
            put("message_count", messages.size)
            put("format_version", "1.0")

            val messagesArray = JSONArray()
            messages.forEach { message ->
                messagesArray.put(JSONObject().apply {
                    put("id", message.id)
                    put("text", message.text)
                    put("is_user", message.isUser)
                    put("timestamp", message.timestamp)
                    put("timestamp_formatted", dateFormat.format(Date(message.timestamp)))

                    // Optional fields
                    message.modelName?.let { put("model_name", it) }
                    message.error?.let { put("error", it) }
                    message.imageUri?.let { put("image_uri", it) }

                    // Artifact data if present
                    message.artifact?.let { artifact ->
                        put("artifact", JSONObject().apply {
                            put("id", artifact.id)
                            put("created_at", artifact.createdAt)
                            put("integrity_hash", artifact.integrityHash)
                            put("modality", artifact.getModality().toString())
                            put("metadata", JSONObject(artifact.metadata.mapValues { (_, value) -> value.toString() }))

                            when (artifact) {
                                is Artifact.Text -> {
                                    put("kind", "text")
                                    put("content", artifact.content)
                                }
                                is Artifact.Image -> {
                                    put("kind", "image")
                                    put("uri", artifact.uri.toString())
                                    put("mime_type", artifact.mimeType)
                                    put("width", artifact.width)
                                    put("height", artifact.height)
                                }
                                is Artifact.Audio -> {
                                    put("kind", "audio")
                                    put("uri", artifact.uri.toString())
                                    put("mime_type", artifact.mimeType)
                                    put("duration_ms", artifact.durationMs)
                                    put("transcript", artifact.transcript)
                                }
                                is Artifact.Video -> {
                                    put("kind", "video")
                                    put("uri", artifact.uri.toString())
                                    put("mime_type", artifact.mimeType)
                                    put("duration_ms", artifact.durationMs)
                                }
                                is Artifact.Binary -> {
                                    put("kind", "binary")
                                    put("size_bytes", artifact.data.size)
                                    put("mime_type", artifact.mimeType)
                                }
                                is Artifact.Json -> {
                                    put("kind", "json")
                                    put("json", artifact.jsonString)
                                }
                                is Artifact.Error -> {
                                    put("kind", "error")
                                    put("message", artifact.message)
                                    put("recoverable", artifact.recoverable)
                                    put("error_code", artifact.errorCode)
                                    put("cause", artifact.cause)
                                }
                                is Artifact.Empty -> {
                                    put("kind", "empty")
                                }
                            }
                        })
                    }
                })
            }
            put("messages", messagesArray)
        }

        file.writeText(jsonObject.toString(2)) // Pretty-print with 2-space indentation
        Log.d(TAG, "Exported to JSON: ${file.absolutePath}")
        return file
    }

    /**
     * Export conversation to PDF format.
     * Uses Apache PDFBox for PDF generation.
     */
    private fun exportToPDF(
        messages: List<ChatMessage>,
        dir: File,
        baseFilename: String
    ): File {
        return try {
            val pdfManager = PdfExportManager(context)
            val tempPdf = pdfManager.createPdfFile(baseFilename, messages)
            
            // Move temp file to final location
            val finalFile = File(dir, "${baseFilename}.pdf")
            tempPdf.copyTo(finalFile, overwrite = true)
            tempPdf.delete()
            
            Log.d(TAG, "Exported to PDF: ${finalFile.absolutePath}")
            finalFile
        } catch (e: Exception) {
            Log.e(TAG, "PDF export failed", e)
            // PDF export failed - throw error instead of silently returning wrong format
            throw RuntimeException("PDF export failed: ${e.message}", e)
        }
    }

    /**
     * Get or create the export directory in app-specific storage.
     */
    private fun getOrCreateExportDirectory(): File? {
        return try {
            val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir

            val exportDir = File(externalDir, EXPORTS_DIR)
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            if (exportDir.canWrite()) {
                exportDir
            } else {
                Log.e(TAG, "Export directory is not writable")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create export directory", e)
            null
        }
    }

    /**
     * Share an exported file using Android share sheet.
     */
    fun shareExportedFile(file: File, mimeType: String = "text/plain") {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // FLAG_ACTIVITY_NEW_TASK is required when starting an activity from Application context
        val chooserIntent = Intent.createChooser(intent, "Share exported conversation").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooserIntent)
    }

    /**
     * Get export statistics for debugging/monitoring.
     */
    fun getExportStats(): ExportStats {
        val exportDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?.let { File(it, EXPORTS_DIR) }

        return if (exportDir?.exists() == true) {
            val files = exportDir.listFiles() ?: emptyArray()
            val totalSize = files.sumOf { it.length() }
            val byType = files.groupBy { it.extension }.mapValues { it.value.size }

            ExportStats(
                totalExports = files.size,
                totalSizeBytes = totalSize,
                exportsByType = byType,
                lastExportTime = files.maxOfOrNull { it.lastModified() } ?: 0L
            )
        } else {
            ExportStats(0, 0, emptyMap(), 0L)
        }
    }

    data class ExportStats(
        val totalExports: Int,
        val totalSizeBytes: Long,
        val exportsByType: Map<String, Int>,
        val lastExportTime: Long
    )
}
