package com.proj.Musicality.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.MaterialTheme

@Immutable
data class PlaybackUiPalette(
    val accent: Color,
    val onAccent: Color,
    val navIndicator: Color,
    val currentItemContainer: Color,
    val currentItemSecondary: Color
)

val LocalPlaybackUiPalette = staticCompositionLocalOf<PlaybackUiPalette?> { null }

@Composable
fun rememberPlaybackUiPalette(
    artworkUrl: String?
): PlaybackUiPalette? {
    if (artworkUrl.isNullOrBlank()) return null

    val fallbackSurface = MaterialTheme.colorScheme.surface
    val palette = rememberMediaBackdropPalette(
        imageUrl = artworkUrl,
        fallbackSurface = fallbackSurface
    )

    return remember(palette) {
        PlaybackUiPalette(
            accent = palette.accent,
            onAccent = palette.onAccent,
            navIndicator = lerp(palette.middle, palette.accent, 0.18f).copy(alpha = 0.95f),
            currentItemContainer = lerp(palette.bottom, palette.accent, 0.14f).copy(alpha = 0.92f),
            currentItemSecondary = lerp(palette.body, palette.accent, 0.28f).copy(alpha = 0.9f)
        )
    }
}
