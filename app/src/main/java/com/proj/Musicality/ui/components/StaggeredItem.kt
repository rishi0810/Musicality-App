package com.proj.Musicality.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StaggeredItem(
    index: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Start invisible, flip on first frame — triggers spring animation with no blocking delay
    var triggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { triggered = true }

    // Effects spec: damping 1.0 (no overshoot), stiffness 3800 — snappy fade-in
    val alpha by animateFloatAsState(
        targetValue = if (triggered) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "item-alpha"
    )
    // Spatial spec: damping 0.6 (bouncy overshoot), stiffness 800 — expressive slide-up
    val translationY by animateFloatAsState(
        targetValue = if (triggered) 0f else 24f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "item-translate"
    )

    // graphicsLayer lambda: GPU-composited, zero recomposition per animation frame
    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = translationY
        }
    ) {
        content()
    }
}
