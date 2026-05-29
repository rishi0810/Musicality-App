package com.proj.Musicality.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.proj.Musicality.ui.theme.AppColors

@Composable
fun rememberScrimGradient(): Brush = remember {
    Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to Color.Transparent,
            0.35f to AppColors.ScrimMedium,
            1.0f to AppColors.ScrimHeavy
        )
    )
}
