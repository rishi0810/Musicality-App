package com.example.musicality.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Apple-style squircle shape with smooth, continuous curvature corners.
 * More visually pleasing than standard RoundedCornerShape.
 */
class SquircleShape(private val cornerRadius: Dp = 24.dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radiusPx = with(density) { cornerRadius.toPx() }
        val width = size.width
        val height = size.height
        
        val radius = min(radiusPx, min(width, height) / 2)
        val smoothing = 0.6f
        val controlOffset = radius * (1 - smoothing)
        
        return Outline.Generic(
            Path().apply {
                moveTo(0f, radius)
                cubicTo(0f, controlOffset, controlOffset, 0f, radius, 0f)
                lineTo(width - radius, 0f)
                cubicTo(width - controlOffset, 0f, width, controlOffset, width, radius)
                lineTo(width, height - radius)
                cubicTo(width, height - controlOffset, width - controlOffset, height, width - radius, height)
                lineTo(radius, height)
                cubicTo(controlOffset, height, 0f, height - controlOffset, 0f, height - radius)
                close()
            }
        )
    }
}

// Pre-defined squircle shapes for common use cases
val SquircleShape8 = SquircleShape(cornerRadius = 8.dp)
val SquircleShape12 = SquircleShape(cornerRadius = 12.dp)
val SquircleShape16 = SquircleShape(cornerRadius = 16.dp)
val SquircleShape24 = SquircleShape(cornerRadius = 24.dp)
