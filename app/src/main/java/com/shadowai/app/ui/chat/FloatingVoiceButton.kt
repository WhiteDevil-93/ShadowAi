package com.shadowai.app.ui.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shadowai.app.ui.theme.bg_2
import com.shadowai.app.ui.theme.emerald_core
import com.shadowai.app.ui.theme.stroke_subtle
import com.shadowai.app.ui.theme.text_primary

@Composable
fun FloatingVoiceButton(
    isListening: Boolean,
    confidence: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voiceButtonPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voiceButtonPulse"
    )

    val confidenceAnimation by animateFloatAsState(
        targetValue = confidence,
        animationSpec = tween(150),
        label = "voiceButtonConfidence"
    )

    val buttonColor = if (isListening) emerald_core else text_primary

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(1f + confidenceAnimation * 0.3f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                emerald_core.copy(alpha = 0.3f * confidenceAnimation),
                                Color.Transparent
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                emerald_core.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(bg_2)
                .border(
                    width = 2.dp,
                    color = if (isListening) emerald_core else stroke_subtle,
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop listening" else "Voice input",
                tint = buttonColor,
                modifier = Modifier.size(28.dp)
            )
        }

        if (isListening) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DotIndicator(delay = 0)
                DotIndicator(delay = 100)
                DotIndicator(delay = 200)
            }
        }
    }
}

@Composable
private fun DotIndicator(delay: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "voiceDot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, delayMillis = delay, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voiceDot"
    )

    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(emerald_core.copy(alpha = alpha))
    )
}
