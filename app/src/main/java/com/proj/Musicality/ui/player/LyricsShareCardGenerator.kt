package com.proj.Musicality.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.proj.Musicality.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

private const val SHARE_CARD_MAX_LINES = 6
private const val LYRIC_TEXT_SIZE = 60f
private const val LYRIC_LINE_SPACING = 14f

data class LyricsShareCardSpec(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val lyricLines: List<String>,
    val backgroundColor: Int,
    val textColor: Int
)

object LyricsShareCardGenerator {
    private const val TAG = "LyricsShareCard"
    private const val WIDTH = 1080
    @Volatile
    private var shareTypefaceBase: Typeface? = null

    suspend fun generate(
        context: Context,
        spec: LyricsShareCardSpec
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val boldTypeface = shareTypeface(context, Typeface.BOLD)
            val normalTypeface = shareTypeface(context, Typeface.NORMAL)
            val lyricsTypeface = shareTypefaceWeighted(context, 800)
            val edge = 64f
            val artworkSize = 164f
            val metadataTop = 64f
            val metadataStartX = edge + artworkSize + 24f

            val lyricLines = spec.lyricLines
                .filter { it.isNotBlank() }
                .take(SHARE_CARD_MAX_LINES)
                .ifEmpty { listOf("No lyrics selected") }

            val lyricsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = spec.textColor
                typeface = lyricsTypeface
                isFakeBoldText = true
                textSize = LYRIC_TEXT_SIZE
            }
            val latinLyricsPaint = TextPaint(lyricsPaint).apply {
                typeface = shareTypefaceWeighted(context, 950)
                isFakeBoldText = true
                // Fill+stroke gives Latin glyphs a visibly stronger weight without changing size.
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = 2f
                strokeJoin = Paint.Join.ROUND
            }
            val lyricsTop = metadataTop + artworkSize + 78f
            val lyricsWidth = (WIDTH - edge * 2f).toInt()
            val wrappedLyricLines = wrapLyricLines(
                lines = lyricLines,
                paint = lyricsPaint,
                maxWidthPx = lyricsWidth.toFloat()
            )
            val lyricLineHeight = lyricsPaint.fontMetrics.run { bottom - top } + LYRIC_LINE_SPACING
            val logo = loadLogoBitmap(context, 58)
            val lyricHeight = lyricLineHeight * wrappedLyricLines.size
            val footerCenterY = lyricsTop + lyricHeight + 94f

            val cardHeight = ceil(footerCenterY + 74f).toInt()
            val bitmap = Bitmap.createBitmap(WIDTH, cardHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.backgroundColor }
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), cardHeight.toFloat(), backgroundPaint)

            val artwork = loadArtworkBitmap(context, spec.artworkUrl, artworkSize.toInt())
            if (artwork != null) {
                drawRoundedBitmap(
                    canvas = canvas,
                    bitmap = artwork,
                    dst = RectF(edge, metadataTop, edge + artworkSize, metadataTop + artworkSize),
                    radius = 26f
                )
            } else {
                val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.textColor and 0x33FFFFFF }
                canvas.drawRoundRect(
                    RectF(edge, metadataTop, edge + artworkSize, metadataTop + artworkSize),
                    26f,
                    26f,
                    placeholder
                )
            }

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = spec.textColor
                textSize = 56f
                typeface = boldTypeface
            }
            val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = spec.textColor and 0xCCFFFFFF.toInt()
                textSize = 40f
                typeface = boldTypeface
            }
            val titleMetrics = titlePaint.fontMetrics
            val artistMetrics = artistPaint.fontMetrics
            val metadataCenterY = metadataTop + artworkSize / 2f
            val metadataGap = 14f
            val metadataBlockHeight =
                (titleMetrics.bottom - titleMetrics.top) +
                    metadataGap +
                    (artistMetrics.bottom - artistMetrics.top)
            val metadataBlockTop = metadataCenterY - metadataBlockHeight / 2f
            val titleBaseline = metadataBlockTop - titleMetrics.top
            val artistBaseline = titleBaseline + titleMetrics.bottom + metadataGap - artistMetrics.top
            val metadataWidth = WIDTH - metadataStartX - edge
            drawSingleLineEllipsized(
                canvas = canvas,
                text = spec.title.ifBlank { "Unknown song" },
                paint = titlePaint,
                x = metadataStartX,
                baseline = titleBaseline,
                width = metadataWidth.toFloat()
            )
            drawSingleLineEllipsized(
                canvas = canvas,
                text = spec.artist.ifBlank { "Unknown artist" },
                paint = artistPaint,
                x = metadataStartX,
                baseline = artistBaseline,
                width = metadataWidth.toFloat()
            )

            val firstBaseline = lyricsTop - lyricsPaint.fontMetrics.top
            wrappedLyricLines.forEachIndexed { index, line ->
                val baseline = firstBaseline + index * lyricLineHeight
                val paint = if (line.containsLatinScript()) latinLyricsPaint else lyricsPaint
                canvas.drawText(line, edge, baseline, paint)
                if (paint === latinLyricsPaint) {
                    // Extra pass to make Latin (English) text match the visual heaviness
                    // of non-Latin glyphs in mixed-script lyric cards.
                    canvas.drawText(line, edge, baseline, paint)
                }
            }

            val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = spec.textColor and 0x88FFFFFF.toInt()
                textSize = 40f
                typeface = normalTypeface
            }
            val logoSize = 56f
            val logoTop = footerCenterY - logoSize / 2f
            val logoLeft = WIDTH - edge - logoSize
            if (logo != null) {
                canvas.drawBitmap(logo, logoLeft, logoTop, null)
            }

            val outputDir = File(context.cacheDir, "lyrics-share").apply { mkdirs() }
            cleanupOldCards(outputDir)
            val outputFile = File(outputDir, "lyrics-card-${System.currentTimeMillis()}.png")
            FileOutputStream(outputFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }

            bitmap.recycle()
            logo?.recycle()
            outputFile
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to generate lyrics share card", throwable)
        }.getOrNull()
    }

    private fun drawSingleLineEllipsized(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        baseline: Float,
        width: Float
    ) {
        val line = TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()
        canvas.drawText(line, x, baseline, paint)
    }

    private fun shareTypeface(context: Context, style: Int): Typeface {
        val base = shareTypefaceBase
            ?: ResourcesCompat.getFont(context, R.font.google_sans_flex_variable)
            ?: Typeface.SANS_SERIF
        if (shareTypefaceBase == null) shareTypefaceBase = base
        return Typeface.create(base, style)
    }

    private fun shareTypefaceWeighted(context: Context, weight: Int): Typeface {
        val base = shareTypefaceBase
            ?: ResourcesCompat.getFont(context, R.font.google_sans_flex_variable)
            ?: Typeface.SANS_SERIF
        if (shareTypefaceBase == null) shareTypefaceBase = base
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(base, weight.coerceIn(1, 1000), false)
        } else {
            Typeface.create(base, Typeface.BOLD)
        }
    }

    private fun wrapLyricLines(
        lines: List<String>,
        paint: TextPaint,
        maxWidthPx: Float
    ): List<String> {
        val wrapped = mutableListOf<String>()
        lines.forEach { raw ->
            var remaining = raw.trim()
            if (remaining.isEmpty()) return@forEach
            while (remaining.isNotEmpty()) {
                var cut = paint.breakText(remaining, true, maxWidthPx, null).coerceAtLeast(1)
                if (cut < remaining.length) {
                    val lastSpace = remaining.lastIndexOf(' ', cut - 1)
                    if (lastSpace > 0) cut = lastSpace
                }
                val segment = remaining.substring(0, cut).trim()
                if (segment.isNotEmpty()) {
                    wrapped += segment
                }
                remaining = remaining.substring(cut).trimStart()
            }
        }
        return wrapped.ifEmpty { listOf("No lyrics selected") }
    }

    private fun String.containsLatinScript(): Boolean = any { it in 'A'..'Z' || it in 'a'..'z' }

    private suspend fun loadArtworkBitmap(
        context: Context,
        artworkUrl: String?,
        sizePx: Int
    ): Bitmap? {
        if (artworkUrl.isNullOrBlank()) return null
        val request = ImageRequest.Builder(context)
            .data(artworkUrl)
            .size(Size(sizePx, sizePx))
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        val source = (result as? SuccessResult)?.image?.toBitmap() ?: return null
        if (source.width == sizePx && source.height == sizePx) {
            return source
        }
        return Bitmap.createScaledBitmap(source, sizePx, sizePx, true)
    }

    private fun loadLogoBitmap(context: Context, sizePx: Int): Bitmap? {
        // Most reliable source: resolved launcher icon from PackageManager.
        runCatching {
            val appIcon = context.packageManager.getApplicationIcon(context.packageName)
            return drawableToBitmap(appIcon, sizePx)
        }

        val raster = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_foreground)
            ?: BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        if (raster != null) {
            if (raster.width == sizePx && raster.height == sizePx) return raster
            return Bitmap.createScaledBitmap(raster, sizePx, sizePx, true).also {
                if (it != raster) raster.recycle()
            }
        }

        val source = AppCompatResources.getDrawable(context, R.mipmap.ic_launcher_round)
            ?: AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)
            ?: AppCompatResources.getDrawable(context, R.mipmap.ic_launcher_foreground)
            ?: return null
        return drawableToBitmap(source, sizePx)
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }

    private fun drawRoundedBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        dst: RectF,
        radius: Float
    ) {
        val path = Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, null, dst, null)
        canvas.restore()
    }

    private fun cleanupOldCards(directory: File) {
        val files = directory.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(20).forEach { file ->
            runCatching { file.delete() }
        }
    }
}
