package com.proj.Musicality.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

// Picks black or white as the foreground for `container` based on WCAG contrast. Use this
// anywhere a container color is dynamic (album-derived palette, Material container fallback,
// etc.) so the label/icon stays readable across light and dark themes.
fun readableContentColor(container: Color): Color {
    val blackContrast = ColorUtils.calculateContrast(Color.Black.toArgb(), container.toArgb())
    val whiteContrast = ColorUtils.calculateContrast(Color.White.toArgb(), container.toArgb())
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}
