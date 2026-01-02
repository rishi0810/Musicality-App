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

/**
 * Swipe-up tutorial overlay that displays a Lottie animation
 * to guide first-time users on how to open the queue.
 * 
 * The animation plays infinitely until dismissed.
 * Includes a dark background for visibility and instructional text.
 */
@Composable
fun SwipeUpTutorialOverlay(
    modifier: Modifier = Modifier,
    animationSize: Int = 120
) {
    // Load the .lottie file from res/raw
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.swipe_up)
    )

    // Control the animation loop (Infinite)
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    // Container with dark background
    Box(
        modifier = modifier
//            .background(
//                color = Color.Black.copy(alpha = 0.4f),
//                shape = RoundedCornerShape(16.dp)
//            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Render the Animation
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.size(animationSize.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Instructional text
            Text(
                text = "Swipe up to open queue",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
