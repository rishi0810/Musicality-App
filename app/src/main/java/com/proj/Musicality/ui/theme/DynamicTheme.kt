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
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

private const val MEDIA_PALETTE_CACHE_VERSION = "v4"

private fun mediaPaletteCacheKey(imageUrl: String): String =
    "media-palette:$MEDIA_PALETTE_CACHE_VERSION:$imageUrl"

private fun cachedMediaBackdropPalette(imageUrl: String?): MediaBackdropPalette? {
    if (imageUrl.isNullOrBlank()) return null
    return AppCache.paletteColors.get(mediaPaletteCacheKey(imageUrl)) as? MediaBackdropPalette
}

/**
 * Loads + caches the backdrop palette for [imageUrl], returning a cached value instantly
 * when present. Safe to call off the composition (e.g. to prewarm on navigation). Returns
 * null if the image can't be decoded.
 */
suspend fun loadMediaBackdropPalette(
    context: Context,
    imageUrl: String,
    fallbackSurface: Color = Color(0xFF121212),
    allowNetworkFetch: Boolean = true
): MediaBackdropPalette? {
    cachedMediaBackdropPalette(imageUrl)?.let { return it }

    val loader = coil3.SingletonImageLoader.get(context)
    val request = ImageRequest.Builder(context).apply {
        if (!allowNetworkFetch) {
            networkCachePolicy(CachePolicy.DISABLED)
        }
    }
        .data(imageUrl)
        .size(Size(256, 256))
        .allowHardware(false)
        .build()

    val result = withContext(Dispatchers.IO) { loader.execute(request) }
    val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return null

    val extracted = withContext(Dispatchers.Default) {
        // 5% inset trims most logo/border crud while preserving corner color
        // detail that the previous 8% inset was discarding.
        val insetX = (bitmap.width * 0.05f).toInt().coerceAtLeast(0)
        val insetY = (bitmap.height * 0.05f).toInt().coerceAtLeast(0)
        val left = insetX.coerceAtMost((bitmap.width - 1).coerceAtLeast(0))
        val top = insetY.coerceAtMost((bitmap.height - 1).coerceAtLeast(0))
        val right = (bitmap.width - insetX).coerceAtLeast(left + 1)
        val bottom = (bitmap.height - insetY).coerceAtLeast(top + 1)
        val generatedPalette = Palette.from(bitmap)
            .setRegion(left, top, right, bottom)
            .maximumColorCount(24)
            // Default resize is 112×112 which destroys small accent regions;
            // keep the full inset area so Palette quantizes against real pixel
            // counts instead of an over-smoothed thumbnail.
            .resizeBitmapArea(65_536)
            // Drop Palette's built-in skin-tone exclusion (it rejects useful
            // colors on portrait covers); keep only the lightness-extreme
            // filter so the swatch scoring sees real artwork colors.
            .clearFilters()
            .addFilter { _, hsl -> hsl[2] in 0.04f..0.96f }
            .generate()
        buildMediaBackdropPalette(
            palette = generatedPalette,
            fallbackSurface = fallbackSurface
        )
    }
    AppCache.paletteColors.put(mediaPaletteCacheKey(imageUrl), extracted)
    return extracted
}

/**
 * Fire-and-forget palette precomputation. Call when navigating from a surface that already
 * shows [imageUrl] (search results, "Fans might also like", album/playlist art) so the
 * destination's gradient is ready the instant it composes. No-ops when already cached or
 * when there's no image (e.g. opening an artist from the player by name). Cache-only by
 * default since the source already loaded the image.
 */
fun prewarmMediaBackdropPalette(
    scope: CoroutineScope,
    context: Context,
    imageUrl: String?,
    allowNetworkFetch: Boolean = false
) {
    if (imageUrl.isNullOrBlank()) return
    if (cachedMediaBackdropPalette(imageUrl) != null) return
    val appContext = context.applicationContext
    scope.launch {
        loadMediaBackdropPalette(appContext, imageUrl, allowNetworkFetch = allowNetworkFetch)
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
    // Synchronous cache read so a palette already computed by a prewarm (navigating from
    // a surface that showed this art) or a previous visit paints with the real color on
    // the very first frame — no black-then-fade. Defaults until the async load resolves.
    var palette by remember {
        mutableStateOf(cachedMediaBackdropPalette(imageUrl) ?: defaultMediaBackdropPalette(fallbackSurface))
    }

    LaunchedEffect(imageUrl, allowNetworkFetch) {
        if (imageUrl.isNullOrBlank()) {
            palette = defaultMediaBackdropPalette(fallbackSurface)
            return@LaunchedEffect
        }
        loadMediaBackdropPalette(context, imageUrl, fallbackSurface, allowNetworkFetch)?.let {
            palette = it
        }
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

private val GradientSurface = Color(0xFF000000)

private fun buildMediaBackdropPalette(
    palette: Palette,
    fallbackSurface: Color
): MediaBackdropPalette {
    val surface = GradientSurface
    val allSwatches = palette.swatches
    val totalPopulation = allSwatches.sumOf { it.population }.coerceAtLeast(1)
    // HSL saturation reads near-1.0 on very-dark colors that visually look black;
    // the lightness gate keeps those out of the "expressive" pool so a vignette
    // doesn't masquerade as a vivid accent.
    val expressiveSwatches = allSwatches.filter { swatch ->
        val c = Color(swatch.rgb)
        val hsl = c.toHsl()
        hsl[1] >= 0.18f && hsl[2] in 0.10f..0.92f
    }
    val expressivePopulationRatio =
        expressiveSwatches.sumOf { it.population }.toFloat() / totalPopulation.toFloat()
    val swatches = if (expressiveSwatches.isNotEmpty() && expressivePopulationRatio >= 0.15f) {
        expressiveSwatches
    } else {
        allSwatches
    }
    val isAchromatic = expressivePopulationRatio < 0.15f
    val maxPopulation = swatches.maxOfOrNull { it.population }?.coerceAtLeast(1) ?: 1
    val sortedSwatches = swatches
        .sortedByDescending { scoreSwatch(it, maxPopulation) }

    val scoredDominantSeed = sortedSwatches
        .firstOrNull()
        ?.let { Color(it.rgb) }

    // For color art, fall back to Palette's specialized vibrant targets when our
    // population-weighted score lands on a desaturated swatch — covers with a
    // huge muted backdrop and a small vivid logo were previously losing the logo.
    val vibrantFallbackSeed = if (!isAchromatic) {
        (palette.vibrantSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.lightVibrantSwatch)
            ?.let { Color(it.rgb) }
    } else null

    val dominantSeed = when {
        scoredDominantSeed == null -> vibrantFallbackSeed
        vibrantFallbackSeed != null &&
            saturationOf(scoredDominantSeed) < 0.20f &&
            saturationOf(vibrantFallbackSeed) > saturationOf(scoredDominantSeed) + 0.12f ->
            vibrantFallbackSeed
        else -> scoredDominantSeed
    }

    val dominantMinSaturation = if (isAchromatic) 0.06f else 0.22f
    val dominantTargetLightness = if (isAchromatic) 0.28f else 0.38f
    val dominant = dominantSeed
        ?.let { normalizeBackdropColor(it, surface, targetLightness = dominantTargetLightness, minSaturation = dominantMinSaturation) }
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
            val hueHarmony = harmonyScore(hueGap)
            val score = scoreSwatch(swatch, maxPopulation) * 0.62f + hueHarmony * 0.38f
            swatch to score
        }
        .maxByOrNull { it.second }
        ?.first
        ?.let { Color(it.rgb) }
        ?: sortedSwatches
            .firstOrNull { swatch -> hueDistance(Color(swatch.rgb), dominant) >= 10f }
            ?.let { Color(it.rgb) }
        ?: dominantSeed

    val supportiveMinSaturation = if (isAchromatic) 0.04f else 0.18f
    val supportiveBase = supportiveSeed
        ?.let { normalizeBackdropColor(it, surface, targetLightness = 0.30f, minSaturation = supportiveMinSaturation) }
        ?: darkenColor(dominant, 0.12f)

    // Snap "muddy" mid-range hue gaps (60°–135°) back toward the dominant so we
    // don't produce olive/brown second stops when the cover only has one strong
    // hue plus an off-tone speck.
    val supportiveHueGap = hueDistance(supportiveBase, dominant)
    val supportive = when {
        supportiveHueGap in 60f..135f -> lerp(supportiveBase, dominant, 0.55f)
        supportiveHueGap > 200f -> lerp(supportiveBase, dominant, 0.30f)
        else -> supportiveBase
    }

    val highlightSeed = sortedSwatches
        .filter { swatch ->
            val candidate = Color(swatch.rgb)
            hueDistance(candidate, dominant) >= 8f || saturationOf(candidate) > saturationOf(dominant) + 0.08f
        }
        .maxByOrNull { accentScore(it, maxPopulation) }
        ?.let { Color(it.rgb) }
        ?: vibrantFallbackSeed
        ?: dominant

    // Pull less of the surface into each stop than before so the actual artwork
    // colors carry through. Bottom stays heavily dark for title legibility.
    val top = lerp(surface, dominant, 0.94f).copy(alpha = 1f)
    val middle = lerp(surface, supportive, 0.97f).copy(alpha = 1f)
    val bottom = lerp(surface, darkenColor(supportive, 0.20f), 0.985f).copy(alpha = 1f)
    val baseAccent = normalizeAccentColor(highlightSeed, surface)
    // Accent is painted on top/middle/bottom in different surfaces — check
    // against the worst of the three so it stays legible everywhere it lands.
    val worstAccentBackground = listOf(top, middle, bottom).minByOrNull { bg ->
        ColorUtils.calculateContrast(baseAccent.toArgb(), bg.toArgb())
    } ?: bottom
    val accent = ensureContrastAgainst(
        color = baseAccent,
        background = worstAccentBackground,
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

internal fun defaultMediaBackdropPalette(surface: Color): MediaBackdropPalette {
    val s = GradientSurface
    val top = lerp(s, Color.Black, 0.12f)
    val middle = lerp(s, Color.Black, 0.22f)
    val bottom = lerp(s, Color.Black, 0.34f)
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
        (saturation + (minSaturation - saturation) * 0.68f).coerceAtMost(0.78f)
    } else {
        saturation.coerceAtMost(0.78f)
    }
    // Widened from ±0.12 → ±0.16 so pastel covers don't get crushed to the same
    // mid-tone band as deep covers; lets each artwork keep its lightness identity.
    hsl[2] = hsl[2].coerceIn(targetLightness - 0.16f, targetLightness + 0.16f)
    return lerp(surface, Color(ColorUtils.HSLToColor(hsl)), 0.94f).copy(alpha = 1f)
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

// Two-peak harmony curve: rewards either an analogous pairing (~30° away from
// the dominant) or a near-complementary pairing (~180°). The old single-peak
// curve at 24° gave 0 to true complementaries, so covers with strong opposing
// accents collapsed back to a single hue.
private fun harmonyScore(hueGap: Float): Float {
    val analogous = 1f - (abs(hueGap - 30f) / 35f).coerceIn(0f, 1f)
    val complementary = 0.82f * (1f - (abs(hueGap - 180f) / 55f).coerceIn(0f, 1f))
    return maxOf(analogous, complementary)
}

private fun saturationOf(color: Color): Float = color.toHsl()[1]

private fun lightnessOf(color: Color): Float = color.toHsl()[2]

private fun Color.toHsl(): FloatArray {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    return hsl
}
