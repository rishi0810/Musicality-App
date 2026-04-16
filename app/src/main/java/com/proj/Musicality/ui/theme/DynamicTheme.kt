package com.proj.Musicality.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.proj.Musicality.cache.AppCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Immutable
data class AlbumColors(
    val primary: Color = Color.Transparent,
    val secondary: Color = Color.Transparent
)

@Immutable
data class MediaBackdropPalette(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    val accent: Color,
    val onAccent: Color,
    val title: Color,
    val body: Color,
    val outline: Color
)

@Composable
fun rememberAlbumColors(
    imageUrl: String?,
    fallbackPrimary: Color = Color(0xFF2C2C2C),
    fallbackSecondary: Color = Color(0xFF444444),
    allowNetworkFetch: Boolean = true
): AlbumColors {
    if (imageUrl.isNullOrBlank()) {
        return remember(fallbackPrimary, fallbackSecondary) {
            AlbumColors(
                primary = fallbackPrimary,
                secondary = fallbackSecondary
            )
        }
    }

    val palette = rememberMediaBackdropPalette(
        imageUrl = imageUrl,
        fallbackSurface = fallbackSecondary,
        allowNetworkFetch = allowNetworkFetch
    )
    return remember(palette.top, palette.middle) {
        AlbumColors(
            primary = palette.middle,
            secondary = palette.top
        )
    }
}

@Composable
fun rememberMediaBackdropPalette(
    imageUrl: String?,
    fallbackSurface: Color = Color(0xFF121212),
    allowNetworkFetch: Boolean = true,
    animateTransitions: Boolean = true
): MediaBackdropPalette {
    val context = LocalContext.current
    var palette by remember(imageUrl, fallbackSurface) {
        mutableStateOf(defaultMediaBackdropPalette(fallbackSurface))
    }

    LaunchedEffect(imageUrl, fallbackSurface, allowNetworkFetch) {
        if (imageUrl.isNullOrBlank()) {
            palette = defaultMediaBackdropPalette(fallbackSurface)
            return@LaunchedEffect
        }

        val cacheKey = "media-palette:v2:$imageUrl:${fallbackSurface.toArgb()}"
        (AppCache.paletteColors.get(cacheKey) as? MediaBackdropPalette)?.let {
            palette = it
            return@LaunchedEffect
        }

        val loader = coil3.SingletonImageLoader.get(context)
        val request = ImageRequest.Builder(context).apply {
            if (!allowNetworkFetch) {
                networkCachePolicy(CachePolicy.DISABLED)
            }
        }
            .data(imageUrl)
            .size(Size(160, 160))
            .allowHardware(false)
            .build()

        val result = withContext(Dispatchers.IO) { loader.execute(request) }
        val bitmap = (result as? SuccessResult)?.image?.toBitmap()
        if (bitmap == null) {
            palette = defaultMediaBackdropPalette(fallbackSurface)
            return@LaunchedEffect
        }

        val extracted = withContext(Dispatchers.Default) {
            val insetX = (bitmap.width * 0.08f).toInt().coerceAtLeast(0)
            val insetY = (bitmap.height * 0.08f).toInt().coerceAtLeast(0)
            val left = insetX.coerceAtMost((bitmap.width - 1).coerceAtLeast(0))
            val top = insetY.coerceAtMost((bitmap.height - 1).coerceAtLeast(0))
            val right = (bitmap.width - insetX).coerceAtLeast(left + 1)
            val bottom = (bitmap.height - insetY).coerceAtLeast(top + 1)
            val generatedPalette = Palette.from(bitmap)
                .setRegion(left, top, right, bottom)
                .maximumColorCount(16)
                .generate()
            buildMediaBackdropPalette(
                palette = generatedPalette,
                fallbackSurface = fallbackSurface
            )
        }
        AppCache.paletteColors.put(cacheKey, extracted)
        palette = extracted
    }

    if (!animateTransitions) {
        return palette
    }

    val animatedTop by animateColorAsState(
        targetValue = palette.top,
        animationSpec = tween(820),
        label = "mediaPaletteTop"
    )
    val animatedMiddle by animateColorAsState(
        targetValue = palette.middle,
        animationSpec = tween(920),
        label = "mediaPaletteMiddle"
    )
    val animatedBottom by animateColorAsState(
        targetValue = palette.bottom,
        animationSpec = tween(1080),
        label = "mediaPaletteBottom"
    )
    val animatedAccent by animateColorAsState(
        targetValue = palette.accent,
        animationSpec = tween(760),
        label = "mediaPaletteAccent"
    )
    val animatedOutline by animateColorAsState(
        targetValue = palette.outline,
        animationSpec = tween(760),
        label = "mediaPaletteOutline"
    )
    val animatedTitle by animateColorAsState(
        targetValue = palette.title,
        animationSpec = tween(450),
        label = "mediaPaletteTitle"
    )
    val animatedBody by animateColorAsState(
        targetValue = palette.body,
        animationSpec = tween(450),
        label = "mediaPaletteBody"
    )
    val animatedOnAccent by animateColorAsState(
        targetValue = palette.onAccent,
        animationSpec = tween(450),
        label = "mediaPaletteOnAccent"
    )

    return remember(
        animatedTop,
        animatedMiddle,
        animatedBottom,
        animatedAccent,
        animatedOnAccent,
        animatedTitle,
        animatedBody,
        animatedOutline
    ) {
        MediaBackdropPalette(
            top = animatedTop,
            middle = animatedMiddle,
            bottom = animatedBottom,
            accent = animatedAccent,
            onAccent = animatedOnAccent,
            title = animatedTitle,
            body = animatedBody,
            outline = animatedOutline
        )
    }
}

private fun buildMediaBackdropPalette(
    palette: Palette,
    fallbackSurface: Color
): MediaBackdropPalette {
    val surface = fallbackSurface.copy(alpha = 1f)
    val allSwatches = palette.swatches
    val expressiveSwatches = allSwatches.filter { saturationOf(Color(it.rgb)) >= 0.12f }
    val swatches = (if (expressiveSwatches.isNotEmpty()) expressiveSwatches else allSwatches)
    val maxPopulation = swatches.maxOfOrNull { it.population }?.coerceAtLeast(1) ?: 1
    val sortedSwatches = swatches
        .sortedByDescending { scoreSwatch(it, maxPopulation) }

    val dominantSeed = sortedSwatches
        .firstOrNull()
        ?.let { Color(it.rgb) }

    val dominant = dominantSeed
        ?.let { normalizeBackdropColor(it, surface, targetLightness = 0.38f, minSaturation = 0.22f) }
        ?: darkenColor(surface, 0.08f)

    val supportiveSeed = sortedSwatches
        .asSequence()
        .drop(1)
        .mapNotNull { swatch ->
            val candidate = Color(swatch.rgb)
            val population = (swatch.population.toFloat() / maxPopulation.toFloat()).coerceIn(0f, 1f)
            val saturation = saturationOf(candidate)
            val lightness = lightnessOf(candidate)
            if (population < 0.06f || saturation < 0.10f || lightness !in 0.12f..0.62f) return@mapNotNull null

            val hueGap = hueDistance(candidate, dominant)
            val hueHarmony = 1f - (abs(hueGap - 24f) / 24f).coerceIn(0f, 1f)
            val score = scoreSwatch(swatch, maxPopulation) * 0.65f + hueHarmony * 0.35f
            swatch to score
        }
        .maxByOrNull { it.second }
        ?.first
        ?.let { Color(it.rgb) }
        ?: sortedSwatches
            .firstOrNull { swatch -> hueDistance(Color(swatch.rgb), dominant) >= 10f }
            ?.let { Color(it.rgb) }
        ?: dominantSeed

    val supportiveBase = supportiveSeed
        ?.let { normalizeBackdropColor(it, surface, targetLightness = 0.30f, minSaturation = 0.18f) }
        ?: darkenColor(dominant, 0.12f)

    // Keep large hue jumps from producing muddy olive/brown backdrops when dominant is already strong.
    val supportive = if (hueDistance(supportiveBase, dominant) > 42f) {
        lerp(supportiveBase, dominant, 0.45f)
    } else {
        supportiveBase
    }

    val highlightSeed = sortedSwatches
        .filter { swatch ->
            val candidate = Color(swatch.rgb)
            hueDistance(candidate, dominant) >= 8f || saturationOf(candidate) > saturationOf(dominant) + 0.08f
        }
        .maxByOrNull { accentScore(it, maxPopulation) }
        ?.let { Color(it.rgb) }
        ?: dominant

    val top = lerp(surface, dominant, 0.82f).copy(alpha = 1f)
    val middle = lerp(surface, supportive, 0.9f).copy(alpha = 1f)
    val bottom = lerp(surface, darkenColor(supportive, 0.22f), 0.96f).copy(alpha = 1f)
    val accent = ensureContrastAgainst(
        color = normalizeAccentColor(highlightSeed, surface),
        background = bottom,
        minContrast = 3.1
    )
    val title = readableForegroundForBackgrounds(top, middle, bottom)
    val body = title.copy(alpha = 0.74f)
    val outline = title.copy(alpha = 0.16f)

    return MediaBackdropPalette(
        top = top,
        middle = middle,
        bottom = bottom,
        accent = accent,
        onAccent = readableForeground(accent),
        title = title,
        body = body,
        outline = outline
    )
}

private fun defaultMediaBackdropPalette(surface: Color): MediaBackdropPalette {
    val top = lerp(surface.copy(alpha = 1f), Color.Black, 0.12f)
    val middle = lerp(surface.copy(alpha = 1f), Color.Black, 0.22f)
    val bottom = lerp(surface.copy(alpha = 1f), Color.Black, 0.34f)
    val accent = ensureContrastAgainst(Color(0xFF9CCBFF), bottom, 3.1)
    val title = readableForegroundForBackgrounds(top, middle, bottom)
    return MediaBackdropPalette(
        top = top,
        middle = middle,
        bottom = bottom,
        accent = accent,
        onAccent = readableForeground(accent),
        title = title,
        body = title.copy(alpha = 0.74f),
        outline = title.copy(alpha = 0.16f)
    )
}

private fun scoreSwatch(
    swatch: Palette.Swatch,
    maxPopulation: Int
): Float {
    val color = Color(swatch.rgb)
    val population = (swatch.population.toFloat() / maxPopulation.toFloat()).coerceIn(0f, 1f)
    val saturation = saturationOf(color)
    val lightness = lightnessOf(color)
    val lightnessScore = 1f - (abs(lightness - 0.42f) / 0.42f).coerceIn(0f, 1f)
    return population * 0.38f + saturation * 0.42f + lightnessScore * 0.20f
}

private fun accentScore(
    swatch: Palette.Swatch,
    maxPopulation: Int
): Float {
    val color = Color(swatch.rgb)
    val population = (swatch.population.toFloat() / maxPopulation.toFloat()).coerceIn(0f, 1f)
    val saturation = saturationOf(color)
    val lightness = lightnessOf(color)
    val lightnessScore = 1f - abs(lightness - 0.52f)
    return population * 0.18f + saturation * 0.62f + lightnessScore * 0.20f
}

private fun normalizeBackdropColor(
    color: Color,
    surface: Color,
    targetLightness: Float,
    minSaturation: Float
): Color {
    val hsl = color.toHsl()
    val saturation = hsl[1].coerceIn(0f, 1f)
    hsl[1] = if (saturation < minSaturation) {
        (saturation + (minSaturation - saturation) * 0.68f).coerceAtMost(0.72f)
    } else {
        saturation.coerceAtMost(0.72f)
    }
    hsl[2] = hsl[2].coerceIn(targetLightness - 0.12f, targetLightness + 0.12f)
    return lerp(surface, Color(ColorUtils.HSLToColor(hsl)), 0.86f).copy(alpha = 1f)
}

private fun normalizeAccentColor(
    color: Color,
    surface: Color
): Color {
    val hsl = color.toHsl()
    hsl[1] = hsl[1].coerceIn(0.46f, 0.9f)
    hsl[2] = hsl[2].coerceIn(0.34f, 0.72f)
    return lerp(surface, Color(ColorUtils.HSLToColor(hsl)), 0.94f).copy(alpha = 1f)
}

private fun ensureContrastAgainst(
    color: Color,
    background: Color,
    minContrast: Double
): Color {
    val hsl = color.toHsl()
    var candidate = color.copy(alpha = 1f)
    repeat(10) {
        val contrast = ColorUtils.calculateContrast(candidate.toArgb(), background.toArgb())
        if (contrast >= minContrast) return candidate
        hsl[2] = if (background.luminance() > 0.5f) {
            (hsl[2] - 0.06f).coerceAtLeast(0.18f)
        } else {
            (hsl[2] + 0.06f).coerceAtMost(0.86f)
        }
        candidate = Color(ColorUtils.HSLToColor(hsl)).copy(alpha = 1f)
    }
    return candidate
}

private fun darkenColor(color: Color, amount: Float): Color {
    val hsl = color.toHsl()
    hsl[2] = (hsl[2] - amount).coerceIn(0.08f, 0.9f)
    return Color(ColorUtils.HSLToColor(hsl)).copy(alpha = 1f)
}

private fun readableForeground(background: Color): Color {
    val blackContrast = ColorUtils.calculateContrast(Color.Black.toArgb(), background.toArgb())
    val whiteContrast = ColorUtils.calculateContrast(Color.White.toArgb(), background.toArgb())
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

private fun readableForegroundForBackgrounds(
    first: Color,
    second: Color,
    third: Color
): Color {
    val backgrounds = listOf(first, second, third)
    val blackWorstContrast = backgrounds.minOf { background ->
        ColorUtils.calculateContrast(Color.Black.toArgb(), background.toArgb())
    }
    val whiteWorstContrast = backgrounds.minOf { background ->
        ColorUtils.calculateContrast(Color.White.toArgb(), background.toArgb())
    }
    return if (blackWorstContrast >= whiteWorstContrast) Color.Black else Color.White
}

private fun hueDistance(a: Color, b: Color): Float {
    val aHue = a.toHsl()[0]
    val bHue = b.toHsl()[0]
    val diff = abs(aHue - bHue)
    return minOf(diff, 360f - diff)
}

private fun saturationOf(color: Color): Float = color.toHsl()[1]

private fun lightnessOf(color: Color): Float = color.toHsl()[2]

private fun Color.toHsl(): FloatArray {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    return hsl
}
