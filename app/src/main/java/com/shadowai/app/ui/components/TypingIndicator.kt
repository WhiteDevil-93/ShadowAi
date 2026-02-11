package com.shadowai.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/**
 * A composable that displays an animated typing indicator with three pulsing dots.
 * This fulfills the "Typing indicator with animated dots" quick win.
 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val dotSize = 8.dp
    val dotSpacing = 4.dp
    val animationDuration = 900

    val infiniteTransition = rememberInfiniteTransition(label = "typingIndicator")

    @Composable
    fun AnimatedDot(delay: Int) {
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = animationDuration, delayMillis = delay, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotAlpha$delay"
        )

        Box(
            modifier = Modifier
                .size(dotSize)
                .alpha(alpha)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CircleShape
                )
        )
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedDot(delay = 0)
        AnimatedDot(delay = animationDuration / 3)
        AnimatedDot(delay = animationDuration * 2 / 3)
    }
}