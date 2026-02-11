package com.shadowai.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.shadowai.core.Artifact

/**
 * A composable that renders rich message content from typed artifacts.
 */
@Composable
fun MessageContent(
    content: String,
    artifact: Artifact? = null,
    imageUrl: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val artifactImageUri = (artifact as? Artifact.Image)?.uri?.toString()
        val imageToRender = artifactImageUri ?: imageUrl
        if (!imageToRender.isNullOrBlank()) {
            ArtifactImage(uri = imageToRender)
        }

        val textToRender = when (artifact) {
            is Artifact.Text -> artifact.content
            else -> content
        }
        if (textToRender.isNotBlank()) {
            RenderMarkdownText(textToRender)
        }
    }
}

@Composable
private fun ArtifactImage(uri: String) {
    Image(
        painter = rememberAsyncImagePainter(uri),
        contentDescription = "Attached image",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp))
    )
}

@Composable
private fun RenderMarkdownText(content: String) {
    val codeBlockRegex = "```([a-zA-Z]*)\\n([\\s\\S]*?)\\n```".toRegex()
    var lastIndex = 0

    codeBlockRegex.findAll(content).forEach { matchResult ->
        val (language, code) = matchResult.destructured
        val startIndex = matchResult.range.first
        val endIndex = matchResult.range.last + 1

        if (startIndex > lastIndex) {
            MarkdownText(content.substring(lastIndex, startIndex))
        }

        CodeBlock(code, language)
        lastIndex = endIndex
    }

    if (lastIndex < content.length) {
        MarkdownText(content.substring(lastIndex))
    }
}

@Composable
private fun MarkdownText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun CodeBlock(code: String, language: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
            .padding(8.dp)
    ) {
        if (language.isNotBlank()) {
            Text(
                text = language.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}
