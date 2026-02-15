package com.shadowai.app.export

import android.content.Context
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF export manager using Apache PDFBox for Android
 */
class PdfExportManager(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    companion object {
        private const val TAG = "PdfExportManager"
        private const val MARGIN = 50f
        private const val MAX_LINE_WIDTH = 80
    }

    /**
     * Create a PDF file with conversation content
     *
     * @param title Conversation title
     * @param messages List of chat messages
     * @return Temporary file with PDF content
     */
    fun createPdfFile(
        title: String,
        messages: List<com.shadowai.app.ui.ChatMessage>
    ): File {
        try {
            PDFBoxResourceLoader.init(context)
            
            val document = PDDocument()
            val pageSize = PDRectangle.A4
            val page = PDPage(pageSize)
            document.addPage(page)
            
            val contentStream = PDPageContentStream(document, page)
            
            var yPosition = pageSize.height - MARGIN
            
            try {
                // Title
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 18f)
                contentStream.beginText()
                contentStream.newLineAtOffset(MARGIN, yPosition)
                contentStream.showText(truncateText(title, 60))
                contentStream.endText()
                yPosition -= 30f
                
                // Export date
                contentStream.setFont(PDType1Font.HELVETICA, 10f)
                contentStream.beginText()
                contentStream.newLineAtOffset(MARGIN, yPosition)
                contentStream.showText("Exported: ${dateFormat.format(Date())}")
                contentStream.endText()
                yPosition -= 40f
                
                // Messages
                messages.forEach { message ->
                    // Check if we need a new page
                    if (yPosition < 100f) {
                        contentStream.close()
                        val newPage = PDPage(pageSize)
                        document.addPage(newPage)
                        yPosition = pageSize.height - MARGIN
                    }
                    
                    // Role
                    contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12f)
                    contentStream.beginText()
                    contentStream.newLineAtOffset(MARGIN, yPosition)
                    val role = if (message.isUser) "User" else "ShadowAI"
                    contentStream.showText("[$role]")
                    contentStream.endText()
                    yPosition -= 20f
                    
                    // Timestamp
                    contentStream.setFont(PDType1Font.HELVETICA_OBLIQUE, 8f)
                    contentStream.beginText()
                    contentStream.newLineAtOffset(MARGIN + 10f, yPosition)
                    contentStream.showText(dateFormat.format(Date(message.timestamp)))
                    contentStream.endText()
                    yPosition -= 15f
                    
                    // Model info for AI messages
                    if (!message.isUser && message.modelName != null) {
                        contentStream.setFont(PDType1Font.HELVETICA_OBLIQUE, 8f)
                        contentStream.beginText()
                        contentStream.newLineAtOffset(MARGIN + 10f, yPosition)
                        contentStream.showText("Model: ${message.modelName}")
                        contentStream.endText()
                        yPosition -= 15f
                    }
                    
                    // Message content
                    contentStream.setFont(PDType1Font.HELVETICA, 10f)
                    val lines = wrapText(message.text, MAX_LINE_WIDTH)
                    
                    lines.forEach { line ->
                        // Check if we need a new page
                        if (yPosition < 50f) {
                            contentStream.close()
                            val newPage = PDPage(pageSize)
                            document.addPage(newPage)
                            yPosition = pageSize.height - MARGIN
                        }
                        
                        contentStream.beginText()
                        contentStream.newLineAtOffset(MARGIN + 15f, yPosition)
                        contentStream.showText(line)
                        contentStream.endText()
                        yPosition -= 12f
                    }
                    
                    // Error message if present
                    if (message.error != null) {
                        yPosition -= 5f
                        contentStream.setFont(PDType1Font.HELVETICA_OBLIQUE, 9f)
                        contentStream.beginText()
                        contentStream.newLineAtOffset(MARGIN + 15f, yPosition)
                        contentStream.showText("ERROR: ${truncateText(message.error, 60)}")
                        contentStream.endText()
                        yPosition -= 12f
                    }
                    
                    yPosition -= 20f // Space between messages
                }
                
                contentStream.close()
                
                // Save to temporary file
                val tempFile = File(context.cacheDir, "shadowai_export_${System.currentTimeMillis()}.pdf")
                document.save(tempFile)
                document.close()
                
                return tempFile
            } catch (e: Exception) {
                contentStream.close()
                document.close()
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create PDF", e)
            throw e
        }
    }
    
    /**
     * Truncate text to maximum length
     */
    private fun truncateText(text: String, maxLength: Int): String {
        return if (text.length > maxLength) {
            text.substring(0, maxLength - 3) + "..."
        } else {
            text
        }
    }
    
    /**
     * Wrap text to fit within specified width
     */
    private fun wrapText(text: String, maxWidth: Int): List<String> {
        val lines = mutableListOf<String>()
        val paragraphs = text.split("\n")
        
        paragraphs.forEach { paragraph ->
            if (paragraph.isBlank()) {
                lines.add("")
            } else {
                val words = paragraph.split(" ")
                var currentLine = ""
                
                words.forEach { word ->
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (testLine.length > maxWidth) {
                        if (currentLine.isNotEmpty()) {
                            lines.add(currentLine)
                            currentLine = word
                        } else {
                            // Word is longer than maxWidth, split it
                            lines.add(word.take(maxWidth))
                        }
                    } else {
                        currentLine = testLine
                    }
                }
                
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
            }
        }
        
        return lines.ifEmpty { listOf("") }
    }
}
