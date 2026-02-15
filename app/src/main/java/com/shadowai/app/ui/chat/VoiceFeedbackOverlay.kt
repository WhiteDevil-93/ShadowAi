package com.shadowai.app.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shadowai.app.accessibility.VoiceRecognitionManager
import com.shadowai.app.ui.theme.*

/**
 * Voice feedback overlay showing confidence meter and partial text
 */
@Composable
fun VoiceFeedbackOverlay(
    isListening: Boolean,
    confidence: Float,
    partialText: String,
    modifier: Modifier = Modifier
) {
    val confidenceAnimation by animateFloatAsState(
        targetValue = confidence,
        animationSpec = tween(150),
        label = "confidenceOverlay"
    )

    AnimatedVisibility(
        visible = isListening,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier
    ) {
        Surface(
            tonalElevation = 4.dp,
            color = bg_2,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            emerald_core.copy(alpha = 0.1f * confidenceAnimation),
                            Color.Transparent
                        )
                    )
                ),
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = emerald_core,
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .size(20.dp)
                )

                Text(
                    text = partialText.ifEmpty { "Listening..." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = text_primary,
                    modifier = Modifier
                        .padding(horizontal = 48.dp)
                        .fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${(confidenceAnimation * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = emerald_core
                    )
                    LinearProgressIndicator(
                        progress = { confidenceAnimation },
                        modifier = Modifier.width(40.dp),
                        color = emerald_core,
                        trackColor = stroke_subtle
                    )
                }
            }
        }
    }
}

/**
 * Animated dot indicator for listening state
 */
@Composable
private fun DotIndicator(delay: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, delayMillis = delay, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot"
    )

    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(emerald_core.copy(alpha = alpha))
    )
}
