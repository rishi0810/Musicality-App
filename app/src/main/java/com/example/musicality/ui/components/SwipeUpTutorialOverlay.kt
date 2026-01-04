package com.example.musicality.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.example.musicality.R
import kotlinx.coroutines.delay

/**
 * Swipe-up tutorial overlay that displays a Lottie animation
 * to guide first-time users on how to open the queue.
 * 
 * The animation plays for 5 seconds then auto-dismisses.
 * Includes a dark background for visibility and instructional text.
 * 
 * @param modifier Modifier for positioning
 * @param animationSize Size of the Lottie animation in dp
 * @param displayDurationMs How long to show the tutorial before auto-dismiss (default 5 seconds)
 * @param onAutoDismiss Callback when the tutorial auto-dismisses after the duration
 */
@Composable
fun SwipeUpTutorialOverlay(
    modifier: Modifier = Modifier,
    animationSize: Int = 160, // Increased from 120 to 160 for bigger animation
    displayDurationMs: Long = 5000L, // 5 seconds
    onAutoDismiss: () -> Unit = {}
) {
    // Load the .lottie file from res/raw
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.swipe_up)
    )

    // Control the animation loop (Infinite while displayed)
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )
    
    // Auto-dismiss after the specified duration
    LaunchedEffect(Unit) {
        delay(displayDurationMs)
        onAutoDismiss()
    }

    // Container with dark background
    Box(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Render the Animation - now bigger
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.size(animationSize.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Instructional text
            Text(
                text = "Swipe up to open queue",
                color = Color.White,
                fontSize = 16.sp, // Slightly larger
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
