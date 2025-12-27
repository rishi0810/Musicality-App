package com.example.musicality.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * A text component with scroll-to-end and snap-back marquee effect for long text.
 * Text scrolls to the end, pauses, then instantly snaps back to the start.
 * Includes fading edges for a polished look.
 * 
 * Used across the app for:
 * - Search results (song names)
 * - Player component (song title)
 * - Queue items (song names)
 */
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.White,
    fontWeight: FontWeight? = null
) {
    val scrollState = rememberScrollState()

    // LaunchedEffect triggers the scroll-and-reset animation loop
    LaunchedEffect(scrollState.maxValue) {
        // Only animate if the text is actually longer than the container
        if (scrollState.maxValue > 0) {
            while (true) {
                // 1. Initial pause at the start
                kotlinx.coroutines.delay(2000)

                // 2. Animate from start (0) to the end
                scrollState.animateScrollTo(
                    value = scrollState.maxValue,
                    animationSpec = tween(
                        durationMillis = (scrollState.maxValue * 15).coerceIn(3000, 10000),
                        easing = LinearEasing
                    )
                )

                // 3. Pause at the end so user can read the last word
                kotlinx.coroutines.delay(1500)

                // 4. INSTANTLY snap back to the start
                scrollState.scrollTo(0)
            }
        }
    }

    // The Text component with horizontal scroll and fading edges
    Text(
        text = text,
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = TextOverflow.Visible,
        softWrap = false,
        modifier = modifier
            .horizontalScroll(scrollState, enabled = false) // Disable manual touch scroll
//            .graphicsLayer { alpha = 0.99f } // Required for blend modes
//            .drawWithContent {
//                drawContent()
//                // Horizontal gradient for fading edges
//                val fadeColors = listOf(
//                    Color.Transparent,
//                    Color.Black,
//                    Color.Black,
//                    Color.Transparent
//                )
//                drawRect(
//                    brush = Brush.horizontalGradient(
//                        colors = fadeColors,
//                        startX = 0f,
//                        endX = size.width
//                    ),
//                    blendMode = BlendMode.DstIn
//                )
//            }
    )
}
