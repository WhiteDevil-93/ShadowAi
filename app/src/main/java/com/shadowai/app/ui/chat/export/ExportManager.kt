package com.shadowai.app.ui.chat.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.shadowai.app.db.ChatMessageEntity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Export format types supported by ShadowAI
 */
enum class ExportFormat {
    PDF,
    MARKDOWN,
    JSON,
    TXT,
    ZIP
}

/**
 * Metadata for exported conversation
 */
@Serializable
data class ConversationExportMetadata(
    val title: String,
    val exportDate: String,
    val messageCount: Int,
    val appVersion: String,
    val exportFormat: String,
    val conversationId: String? = null,
    val modelUsed: String? = null,
    val exportTimestamp: Long = System.currentTimeMillis()
)

/**
 * JSON export schema for conversation
 */
@Serializable
data class ConversationJsonExport(
    val metadata: ConversationExportMetadata,
    val messages: List<MessageJson>
)

@Serializable
data class MessageJson(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val timestampFormatted: String,
    val modelName: String? = null,
    val isError: Boolean = false,
    val hasImage: Boolean = false,
    val imageUri: String? = null
)

/**
 * Batch export manifest for ZIP archives
 */
@Serializable
data class BatchExportManifest(
    val exportDate: String,
    val totalConversations: Int,
    val format: String,
    val appVersion: String,
    val conversations: List<ConversationManifestEntry>
)

@Serializable
data class ConversationManifestEntry(
    val id: String,
    val title: String,
    val fileName: String,
    val messageCount: Int,
    val lastUpdated: Long
)

/**
 * Result of export operation
 */
sealed class ExportResult {
    data class Success(
        val uri: Uri,
        val fileName: String,
        val format: ExportFormat
    ) : ExportResult()

    data class Error(val message: String) : ExportResult()
}

/**
 * Manager class for exporting conversations in various formats.
 * Supports PDF, Markdown, JSON, TXT, and ZIP batch export.
 */
class ExportManager(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MMMM d, yyyy 'at' HH:mm", Locale.US)
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    companion object {
        private const val APP_NAME = "ShadowAI"
        private const val MAX_LINE_WIDTH = 65
        private const val MARGIN = 50f
        private const val APP_VERSION = "2.5" // Update as needed
    }

    /**
     * Export a single conversation in the specified format
     */
    suspend fun exportConversation(
        title: String,
        messages: List<ChatMessageEntity>,
        format: ExportFormat,
        conversationId: String? = null,
        modelUsed: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            when (format) {
                ExportFormat.PDF -> createPdfExport(title, messages, conversationId, modelUsed)
                ExportFormat.MARKDOWN -> createMarkdownExport(title, messages, conversationId, modelUsed)
                ExportFormat.JSON -> createJsonExport(title, messages, conversationId, modelUsed)
                ExportFormat.TXT -> createTxtExport(title, messages, conversationId, modelUsed)
                ExportFormat.ZIP -> ExportResult.Error("ZIP format not supported for single conversation export")
            }
        } catch (e: Exception) {
            ExportResult.Error("Export failed: ${e.message}")
        }
    }

    /**
     * Export multiple conversations as a ZIP archive
     */
    suspend fun exportConversationsBatch(
        conversations: List<Triple<String, String, List<ChatMessageEntity>>>, // (id, title, messages)
        format: ExportFormat
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            createBatchZipExport(conversations, format)
        } catch (e: Exception) {
            ExportResult.Error("Batch export failed: ${e.message}")
        }
    }

    /**
     * Share exported file via Android Intent
     */
    fun shareExport(uri: Uri, format: ExportFormat) {
        val mimeType = when (format) {
            ExportFormat.PDF -> "application/pdf"
            ExportFormat.MARKDOWN -> "text/markdown"
            ExportFormat.JSON -> "application/json"
            ExportFormat.TXT -> "text/plain"
            ExportFormat.ZIP -> "application/zip"
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ShadowAI Conversation Export")
            putExtra(Intent.EXTRA_TEXT, "Exported conversation from ShadowAI")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooserIntent = Intent.createChooser(shareIntent, "Share conversation via")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    }

    /**
     * Create PDF export with proper formatting
     */
    private suspend fun createPdfExport(
        title: String,
        messages: List<ChatMessageEntity>,
        conversationId: String? = null,
        modelUsed: String? = null
    ): ExportResult {
        PDFBoxResourceLoader.init(context)

        val document = PDDocument()
        val pageSize = PDRectangle.A4
        var page = PDPage(pageSize)
        document.addPage(page)
        var contentStream = PDPageContentStream(document, page)

        var yPosition = pageSize.height - MARGIN - 30f
        val leftMargin = MARGIN
        val rightMargin = pageSize.width - MARGIN
        val contentWidth = rightMargin - leftMargin

        try {
            // Title
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 18f)
            contentStream.beginText()
            contentStream.newLineAtOffset(leftMargin, yPosition)
            contentStream.showText(truncateText(title, 40))
            contentStream.endText()
            yPosition -= 25f

            // Date and metadata
            contentStream.setFont(PDType1Font.HELVETICA, 10f)
            contentStream.beginText()
            contentStream.newLineAtOffset(leftMargin, yPosition)
            contentStream.showText("Exported: ${displayDateFormat.format(Date())}")
            contentStream.endText()
            yPosition -= 15f

            // Conversation ID if available
            conversationId?.let {
                contentStream.beginText()
                contentStream.newLineAtOffset(leftMargin, yPosition)
                contentStream.showText("Conversation ID: ${truncateText(it, 50)}")
                contentStream.endText()
                yPosition -= 15f
            }

            // Model info
            modelUsed?.let {
                contentStream.beginText()
                contentStream.newLineAtOffset(leftMargin, yPosition)
                contentStream.showText("Model: $it")
                contentStream.endText()
                yPosition -= 15f
            }

            yPosition -= 20f

            // Separator line
            contentStream.moveTo(leftMargin, yPosition + 10f)
            contentStream.lineTo(rightMargin, yPosition + 10f)
            contentStream.stroke()
            yPosition -= 20f

            // Messages
            messages.forEach { message ->
                // Check if we need a new page
                if (yPosition < 100f) {
                    contentStream.close()
                    page = PDPage(pageSize)
                    document.addPage(page)
                    contentStream = PDPageContentStream(document, page)
                    yPosition = pageSize.height - MARGIN
                }

                // Role label with styling
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 11f)
                contentStream.beginText()
                contentStream.newLineAtOffset(leftMargin, yPosition)

                val roleLabel = if (message.isUser) "You" else "ShadowAI"
                contentStream.showText(roleLabel)
                contentStream.endText()
                yPosition -= 15f

                // Timestamp
                contentStream.setFont(PDType1Font.HELVETICA_OBLIQUE, 8f)
                contentStream.beginText()
                contentStream.newLineAtOffset(leftMargin, yPosition)
                contentStream.showText(formatTimestamp(message.timestamp))
                contentStream.endText()
                yPosition -= 20f

                // Model name for AI messages
                if (!message.isUser && !message.modelName.isNullOrBlank()) {
                    contentStream.setFont(PDType1Font.HELVETICA_OBLIQUE, 8f)
                    contentStream.beginText()
                    contentStream.newLineAtOffset(leftMargin, yPosition)
                    contentStream.showText("Model: ${message.modelName}")
                    contentStream.endText()
                    yPosition -= 15f
                }

                // Message content with word wrapping
                contentStream.setFont(PDType1Font.HELVETICA, 10f)
                val lines = wrapText(message.text, MAX_LINE_WIDTH)

                lines.forEach { line ->
                    if (yPosition < 60f) {
                        contentStream.close()
                        page = PDPage(pageSize)
                        document.addPage(page)
                        contentStream = PDPageContentStream(document, page)
                        yPosition = pageSize.height - MARGIN
                    }

                    contentStream.beginText()
                    contentStream.newLineAtOffset(leftMargin + 10f, yPosition)
                    contentStream.showText(line.take(100)) // Limit chars per line for safety
                    contentStream.endText()
                    yPosition -= 12f
                }

                // Error indicator if message has error
                if (!message.error.isNullOrBlank()) {
                    yPosition -= 5f
                    contentStream.setFont(PDType1Font.HELVETICA_OBLIQUE, 9f)
                    contentStream.beginText()
                    contentStream.newLineAtOffset(leftMargin + 10f, yPosition)
                    contentStream.showText("Error: ${truncateText(message.error, 60)}")
                    contentStream.endText()
                    yPosition -= 12f
                }

                yPosition -= 15f // Space between messages

                // Separator line between messages
                if (yPosition > 60f) {
                    contentStream.moveTo(leftMargin, yPosition + 8f)
                    contentStream.lineTo(rightMargin, yPosition + 8f)
                    contentStream.stroke()
                    yPosition -= 10f
                }
            }

            contentStream.close()

            // Save document
            val fileName = "ShadowAI_${sanitizeFileName(title)}_${dateFormat.format(Date())}.pdf"
            val uri = saveToDownloads(fileName, "application/pdf") { outputStream ->
                document.save(outputStream)
            }

            document.close()

            return if (uri != null) {
                ExportResult.Success(uri, fileName, ExportFormat.PDF)
            } else {
                ExportResult.Error("Failed to save PDF file")
            }
        } catch (e: Exception) {
            try {
                contentStream.close()
                document.close()
            } catch (_: Exception) { }
            throw e
        }
    }

    /**
     * Create Markdown export with formatting preserved
     */
    private suspend fun createMarkdownExport(
        title: String,
        messages: List<ChatMessageEntity>,
        conversationId: String? = null,
        modelUsed: String? = null
    ): ExportResult {
        val fileName = "ShadowAI_${sanitizeFileName(title)}_${dateFormat.format(Date())}.md"
        val content = createMarkdownContent(title, messages, conversationId, modelUsed).toByteArray(Charsets.UTF_8)

        val uri = saveToDownloads(fileName, "text/markdown") { outputStream ->
            outputStream.write(content)
        }

        return if (uri != null) {
            ExportResult.Success(uri, fileName, ExportFormat.MARKDOWN)
        } else {
            ExportResult.Error("Failed to save Markdown file")
        }
    }

    /**
     * Create JSON export with full conversation schema
     */
    private suspend fun createJsonExport(
        title: String,
        messages: List<ChatMessageEntity>,
        conversationId: String? = null,
        modelUsed: String? = null
    ): ExportResult {
        val jsonString = createJsonContent(title, messages, conversationId, modelUsed)
        val fileName = "ShadowAI_${sanitizeFileName(title)}_${dateFormat.format(Date())}.json"
        val content = jsonString.toByteArray(Charsets.UTF_8)

        val uri = saveToDownloads(fileName, "application/json") { outputStream ->
            outputStream.write(content)
        }

        return if (uri != null) {
            ExportResult.Success(uri, fileName, ExportFormat.JSON)
        } else {
            ExportResult.Error("Failed to save JSON file")
        }
    }

    /**
     * Create plain text export
     */
    private suspend fun createTxtExport(
        title: String,
        messages: List<ChatMessageEntity>,
        conversationId: String? = null,
        modelUsed: String? = null
    ): ExportResult {
        val fileName = "ShadowAI_${sanitizeFileName(title)}_${dateFormat.format(Date())}.txt"
        val content = createTxtContent(title, messages, conversationId, modelUsed).toByteArray(Charsets.UTF_8)

        val uri = saveToDownloads(fileName, "text/plain") { outputStream ->
            outputStream.write(content)
        }

        return if (uri != null) {
            ExportResult.Success(uri, fileName, ExportFormat.TXT)
        } else {
            ExportResult.Error("Failed to save text file")
        }
    }

    /**
     * Create batch ZIP export with manifest
     */
    private suspend fun createBatchZipExport(
        conversations: List<Triple<String, String, List<ChatMessageEntity>>>, // (id, title, messages)
        format: ExportFormat
    ): ExportResult {
        val timestamp = dateFormat.format(Date())
        val zipFileName = "ShadowAI_Conversations_$timestamp.zip"

        // Create manifest
        val manifest = BatchExportManifest(
            exportDate = displayDateFormat.format(Date()),
            totalConversations = conversations.size,
            format = format.name,
            appVersion = APP_VERSION,
            conversations = conversations.map { (id, title, messages) ->
                ConversationManifestEntry(
                    id = id,
                    title = title,
                    fileName = "${sanitizeFileName(title)}.${getFormatExtension(format)}",
                    messageCount = messages.size,
                    lastUpdated = messages.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()
                )
            }
        )

        val uri = saveToDownloads(zipFileName, "application/zip") { outputStream ->
            ZipOutputStream(outputStream).use { zipOut ->
                // Add manifest.json
                val manifestEntry = ZipEntry("manifest.json")
                zipOut.putNextEntry(manifestEntry)
                zipOut.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // Add each conversation
                conversations.forEach { (id, title, messages) ->
                    val entryName = "${sanitizeFileName(title)}.${getFormatExtension(format)}"

                    val content = when (format) {
                        ExportFormat.PDF -> {
                            val tempPdf = createTempPdf(title, messages, id)
                            tempPdf.readBytes().also { tempPdf.delete() }
                        }
                        ExportFormat.MARKDOWN -> createMarkdownContent(title, messages, id).toByteArray(Charsets.UTF_8)
                        ExportFormat.JSON -> createJsonContent(title, messages, id).toByteArray(Charsets.UTF_8)
                        ExportFormat.TXT -> createTxtContent(title, messages, id).toByteArray(Charsets.UTF_8)
                        ExportFormat.ZIP -> ByteArray(0)
                    }

                    zipOut.putNextEntry(ZipEntry(entryName))
                    zipOut.write(content)
                    zipOut.closeEntry()
                }
            }
        }

        return if (uri != null) {
            ExportResult.Success(uri, zipFileName, ExportFormat.ZIP)
        } else {
            ExportResult.Error("Failed to create ZIP archive")
        }
    }

    /**
     * Helper to get extension for format
     */
    fun getFormatExtension(format: ExportFormat): String = when (format) {
        ExportFormat.PDF -> "pdf"
        ExportFormat.MARKDOWN -> "md"
        ExportFormat.JSON -> "json"
        ExportFormat.TXT -> "txt"
        ExportFormat.ZIP -> "zip"
    }

    /**
     * Create temporary PDF file for batch export
     */
    private fun createTempPdf(
        title: String,
        messages: List<ChatMessageEntity>,
        conversationId: String? = null
    ): File {
        PDFBoxResourceLoader.init(context)
        val document = PDDocument()
        val page = PDPage(PDRectangle.A4)
        document.addPage(page)

        PDPageContentStream(document, page).use { cs ->
            cs.setFont(PDType1Font.HELVETICA_BOLD, 16f)
            cs.beginText()
            cs.newLineAtOffset(MARGIN, page.mediaBox.height - MARGIN)
            cs.showText(truncateText(title, 40))
            cs.endText()

            cs.setFont(PDType1Font.HELVETICA, 10f)
            cs.beginText()
            cs.newLineAtOffset(MARGIN, page.mediaBox.height - MARGIN - 20f)
            cs.showText("Exported: ${displayDateFormat.format(Date())}")
            cs.endText()
        }

        val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.pdf")
        document.save(tempFile)
        document.close()
        return tempFile
    }

    /**
     * Create Markdown content string
     */
    fun createMarkdownContent(title: String, messages: List<ChatMessageEntity>, conversationId: String? = null, modelUsed: String? = null): String {
        val sb = StringBuilder()
        // Header
        sb.append("# ").append(escapeMarkdown(title)).append("\n\n")

        // Metadata block
        sb.append("> **Exported:** ").append(displayDateFormat.format(Date())).append("\n")
        sb.append("> **Messages:** ").append(messages.size).append("\n")
        sb.append("> **App:** ShadowAI v").append(APP_VERSION).append("\n")
        conversationId?.let {
            sb.append("> **Conversation ID:** ").append(it).append("\n")
        }
        modelUsed?.let {
            sb.append("> **Model:** ").append(it).append("\n")
        }
        sb.append("\n---\n\n")

        // Messages
        messages.forEach { message ->
            val role = if (message.isUser) "**You**" else "**ShadowAI**"
            val time = formatTimestamp(message.timestamp)

            sb.append("### ").append(role).append(" — ").append(time).append("\n\n")

            if (!message.isUser && !message.modelName.isNullOrBlank()) {
                sb.append("*Model: `").append(message.modelName).append("`*\n\n")
            }

            val content = formatMarkdownContent(message.text)
            sb.append(content).append("\n\n")

            if (!message.error.isNullOrBlank()) {
                sb.append("> ⚠️ **Error:** ").append(escapeMarkdown(message.error)).append("\n\n")
            }

            if (!message.imageUri.isNullOrBlank()) {
                sb.append("*[Image attachment]*\n\n")
            }

            sb.append("---\n\n")
        }

        sb.append("\n*Generated by ShadowAI — ").append(displayDateFormat.format(Date())).append("*")
        return sb.toString()
    }

    /**
     * Create JSON content string
     */
    fun createJsonContent(title: String, messages: List<ChatMessageEntity>, conversationId: String? = null, modelUsed: String? = null): String {
        val metadata = ConversationExportMetadata(
            title = title,
            exportDate = displayDateFormat.format(Date()),
            messageCount = messages.size,
            appVersion = APP_VERSION,
            exportFormat = "JSON",
            conversationId = conversationId,
            modelUsed = modelUsed ?: messages.lastOrNull { !it.isUser }?.modelName
        )
        val jsonMessages = messages.map { entity ->
            MessageJson(
                id = entity.id,
                role = if (entity.isUser) "user" else "assistant",
                content = entity.text,
                timestamp = entity.timestamp,
                timestampFormatted = formatTimestamp(entity.timestamp),
                modelName = entity.modelName,
                isError = !entity.error.isNullOrBlank(),
                hasImage = !entity.imageUri.isNullOrBlank(),
                imageUri = entity.imageUri
            )
        }
        return json.encodeToString(ConversationJsonExport(metadata, jsonMessages))
    }

    /**
     * Create TXT content string
     */
    fun createTxtContent(title: String, messages: List<ChatMessageEntity>, conversationId: String? = null, modelUsed: String? = null): String {
        val sb = StringBuilder()
        sb.append("=" .repeat(60)).append("\n")
        sb.append("  ").append(title).append("\n")
        sb.append("  Exported: ").append(displayDateFormat.format(Date())).append("\n")
        sb.append("  ShadowAI v").append(APP_VERSION).append("\n")
        conversationId?.let {
            sb.append("  Conversation ID: ").append(it).append("\n")
        }
        sb.append("=" .repeat(60)).append("\n\n")

        messages.forEach { message ->
            val role = if (message.isUser) "[You]" else "[AI]"
            sb.append(role).append(" ")
                .append(formatTimestamp(message.timestamp)).append("\n")

            if (!message.isUser && !message.modelName.isNullOrBlank()) {
                sb.append("Model: ").append(message.modelName).append("\n")
            }

            sb.append("-".repeat(40)).append("\n")
            sb.append(message.text).append("\n\n")

            if (!message.error.isNullOrBlank()) {
                sb.append("ERROR: ").append(message.error).append("\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    /**
     * Save content to Downloads folder using MediaStore (Android 10+) or direct file access
     */
    private suspend fun saveToDownloads(
        fileName: String,
        mimeType: String,
        writer: (java.io.OutputStream) -> Unit
    ): Uri? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ShadowAI")
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext null

            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    writer(outputStream)
                }

                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                null
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val shadowDir = File(downloadsDir, "ShadowAI").apply { mkdirs() }
            val file = File(shadowDir, fileName)

            file.outputStream().use { outputStream ->
                writer(outputStream)
            }

            val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            scanIntent.data = Uri.fromFile(file)
            context.sendBroadcast(scanIntent)

            Uri.fromFile(file)
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return SimpleDateFormat("HH:mm, MMM d, yyyy", Locale.US).format(Date(timestamp))
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(50)
    }

    private fun truncateText(text: String, maxLength: Int): String {
        return if (text.length > maxLength) text.take(maxLength) + "..." else text
    }

    private fun wrapText(text: String, maxLineWidth: Int): List<String> {
        val lines = mutableListOf<String>()
        val paragraphs = text.split("\n")

        paragraphs.forEach { paragraph ->
            if (paragraph.isBlank()) {
                lines.add("")
            } else {
                var currentLine = StringBuilder()
                val words = paragraph.split(" ")

                words.forEach { word ->
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (testLine.length > maxLineWidth) {
                        lines.add(currentLine.toString())
                        currentLine = StringBuilder(word)
                    } else {
                        currentLine.clear()
                        currentLine.append(testLine)
                    }
                }

                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
            }
        }

        return lines.ifEmpty { listOf("") }
    }

    private fun formatMarkdownContent(text: String): String {
        val codeBlockRegex = "```[\\s\\S]*?```".toRegex()
        val inlineCodeRegex = "`[^`]+`".toRegex()

        val codeBlocks = mutableListOf<String>()
        val inlineCodes = mutableListOf<String>()

        var processed = codeBlockRegex.replace(text) { match ->
            codeBlocks.add(match.value)
            "\u0000CODEBLOCK${codeBlocks.size - 1}\u0000"
        }

        processed = inlineCodeRegex.replace(processed) { match ->
            inlineCodes.add(match.value)
            "\u0000INLINECODE${inlineCodes.size - 1}\u0000"
        }

        processed = processed
            .replace("\\", "\\\\")
            .replace("*", "\\*")
            .replace("_", "\\_")
            .replace("#", "\\#")
            .replace("<", "\\<")
            .replace(">", "\\>")

        codeBlocks.forEachIndexed { index, block ->
            processed = processed.replace("\u0000CODEBLOCK$index\u0000", block)
        }

        inlineCodes.forEachIndexed { index, code ->
            processed = processed.replace("\u0000INLINECODE$index\u0000", code)
        }

        return processed.trim()
    }

    private fun escapeMarkdown(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("*", "\\*")
            .replace("_", "\\_")
            .replace("#", "\\#")
            .replace("<", "\\<")
            .replace(">", "\\>")
            .replace("[", "\\[")
            .replace("]", "\\]")
    }
}
