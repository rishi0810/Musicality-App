package com.proj.Musicality.lyrics.providers

import android.util.Log
import com.proj.Musicality.api.RequestExecutor
import com.proj.Musicality.cache.LruCache
import com.proj.Musicality.data.parser.JsonParser
import com.proj.Musicality.lyrics.LyricsHelper
import com.proj.Musicality.lyrics.LyricsProvider
import com.proj.Musicality.lyrics.formatLrcTimestamp
import java.io.IOException
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * YouTube Music's own lyrics tab (Musixmatch / LyricFind), resolved by videoId:
 * `next` (WEB_REMIX) → lyrics browseId → `browse` (ANDROID_MUSIC) → timedLyricsModel.
 * See docs/LYRICS_API_IMPLEMENTATION.md.
 *
 * Unlike the doc's anonymous probes, some tracks DO return per-line `cueRange`
 * timestamps; others are `staticLayout: true` with text only. Both are handled.
 */
object YouTubeMusicLyrics {
    private const val TAG = "YouTubeMusicLyrics"
    const val PROVIDER_NAME = "YouTube Music"

    private const val PAGE_TYPE_LYRICS = "MUSIC_PAGE_TYPE_TRACK_LYRICS"
    private const val PAGE_TTL_MS = 24 * 60 * 60_000L
    private const val NO_LYRICS_TTL_MS = 15 * 60_000L

    data class Line(val text: String, val startMs: Long?, val endMs: Long?)

    data class Page(
        val browseId: String,
        val lines: List<Line>,
        val sourceMessage: String?,
        val isTimed: Boolean,
        val translationToken: String?,
        val translationSourceMessage: String?,
        val fetchedAt: Long,
    )

    // browseId is opaque and per-video; never derive it from the videoId.
    private val browseIds = LruCache<String, String>(LyricsHelper.RECENT_TRACKS)
    private val noLyricsUntil = LruCache<String, Long>(LyricsHelper.RECENT_TRACKS)
    private val pages = LruCache<String, Page>(LyricsHelper.RECENT_TRACKS)

    private fun pageKey(videoId: String, hl: String) = "$videoId|$hl"

    /** `hl` doubles as the translation target, so it follows the device language. */
    fun translationLanguage(): String {
        val locale = Locale.getDefault()
        return when (val lang = locale.language) {
            "" -> "en"
            "in" -> "id"
            "zh" -> if (locale.country in setOf("TW", "HK", "MO") || locale.script == "Hant") "zh-TW" else "zh-CN"
            "pt" -> if (locale.country == "PT") "pt-PT" else "pt"
            else -> lang
        }
    }

    fun cachedPage(videoId: String, hl: String = translationLanguage()): Page? =
        pages.get(pageKey(videoId, hl))?.takeIf { System.currentTimeMillis() - it.fetchedAt < PAGE_TTL_MS }

    /**
     * Returns null when the track has no lyrics on YouTube Music (negatively cached).
     * Throws [IOException] on transient failures so they are never cached as "no lyrics".
     */
    suspend fun page(
        videoId: String,
        hl: String = translationLanguage(),
        forceRefresh: Boolean = false,
    ): Page? {
        if (!forceRefresh) cachedPage(videoId, hl)?.let { return it }
        noLyricsUntil.get(videoId)?.let { until ->
            if (System.currentTimeMillis() < until) return null
            noLyricsUntil.remove(videoId)
        }

        var browseId = resolveBrowseId(videoId, hl) ?: return markNoLyrics(videoId)
        var response = RequestExecutor.executeLyricsBrowseRequest(hl, browseId = browseId)
        if (response.code == 400 || response.code == 404) {
            browseIds.remove(videoId)
            browseId = resolveBrowseId(videoId, hl) ?: return markNoLyrics(videoId)
            response = RequestExecutor.executeLyricsBrowseRequest(hl, browseId = browseId)
        }
        if (!response.isSuccessful) throw IOException("Lyrics browse HTTP ${response.code}")

        val page = parseTimedPage(browseId, response.body)
            ?: fetchWebFallback(browseId, hl)
            ?: return markNoLyrics(videoId)
        pages.put(pageKey(videoId, hl), page)
        return page
    }

    /**
     * Index-aligned with [Page.lines] minus untranslatable rows (blank / "♪"), as served.
     * Retries once with a fresh page token if the continuation fails.
     */
    suspend fun translations(videoId: String, hl: String = translationLanguage()): List<String>? {
        val page = page(videoId, hl) ?: return null
        val token = page.translationToken ?: return null
        fetchTranslations(hl, token)?.let { return it }

        val fresh = page(videoId, hl, forceRefresh = true) ?: return null
        val freshToken = fresh.translationToken ?: return null
        return fetchTranslations(hl, freshToken)
    }

    fun toLrc(page: Page): String? {
        val visible = page.lines.filter { it.text.isNotBlank() }
        if (visible.isEmpty()) return null
        return if (page.isTimed) {
            visible.joinToString("\n") { formatLrcTimestamp(it.startMs ?: 0L) + it.text.trim() }
        } else {
            visible.joinToString("\n") { it.text.trim() }
        }
    }

    private fun markNoLyrics(videoId: String): Page? {
        noLyricsUntil.put(videoId, System.currentTimeMillis() + NO_LYRICS_TTL_MS)
        return null
    }

    private suspend fun resolveBrowseId(videoId: String, hl: String): String? {
        browseIds.get(videoId)?.let { return it }
        val response = RequestExecutor.executeLyricsNextRequest(videoId, hl)
        if (!response.isSuccessful) throw IOException("Lyrics next HTTP ${response.code}")
        val root = parse(response.body) ?: throw IOException("Lyrics next: unparseable body")
        val endpoint = root.findFirstObject { obj ->
            val browse = obj["browseEndpoint"] as? JsonObject ?: return@findFirstObject false
            browse.path("browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType")
                .string() == PAGE_TYPE_LYRICS
        } ?: return null
        val browseId = (endpoint["browseEndpoint"] as JsonObject)["browseId"].string() ?: return null
        browseIds.put(videoId, browseId)
        return browseId
    }

    private fun parseTimedPage(browseId: String, body: String): Page? {
        val root = parse(body) ?: return null
        // Pre-order search hits the primary model before the nested sharing-flow copy.
        val holder = root.findFirstObject { it.containsKey("timedLyricsModel") } ?: return null
        val data = holder.path("timedLyricsModel", "lyricsData") as? JsonObject ?: return null
        val rows = data["timedLyricsData"] as? JsonArray ?: return null

        val lines = rows.mapNotNull { row ->
            val obj = row as? JsonObject ?: return@mapNotNull null
            val text = obj["lyricLine"].text() ?: return@mapNotNull null
            val cue = obj["cueRange"] as? JsonObject
            Line(
                text = text,
                startMs = cue?.get("startTimeMilliseconds").string()?.toLongOrNull(),
                endMs = cue?.get("endTimeMilliseconds").string()?.toLongOrNull(),
            )
        }
        if (lines.none { it.text.isNotBlank() }) return null

        return Page(
            browseId = browseId,
            lines = lines,
            sourceMessage = data["sourceMessage"].text(),
            isTimed = lines.all { it.startMs != null },
            translationToken = data["translationContinuationToken"].string(),
            translationSourceMessage = data["translationSourceMessage"].text(),
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun fetchWebFallback(browseId: String, hl: String): Page? {
        val response = runCatching {
            RequestExecutor.executeLyricsBrowseRequest(hl, browseId = browseId, webFallback = true)
        }.getOrElse {
            Log.w(TAG, "WEB_REMIX fallback failed", it)
            return null
        }
        if (!response.isSuccessful) return null
        val root = parse(response.body) ?: return null
        val shelf = root.findFirstObject { it.containsKey("musicDescriptionShelfRenderer") }
            ?.get("musicDescriptionShelfRenderer") as? JsonObject ?: return null
        val text = shelf["description"].text() ?: return null
        val lines = text.split('\n').map { Line(it, null, null) }
        if (lines.none { it.text.isNotBlank() }) return null
        return Page(
            browseId = browseId,
            lines = lines,
            sourceMessage = shelf["footer"].text(),
            isTimed = false,
            translationToken = null,
            translationSourceMessage = null,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun fetchTranslations(hl: String, token: String): List<String>? {
        val response = runCatching {
            RequestExecutor.executeLyricsBrowseRequest(hl, continuation = token)
        }.getOrElse {
            Log.w(TAG, "Translation continuation threw", it)
            return null
        }
        if (!response.isSuccessful) {
            Log.d(TAG, "Translation continuation HTTP ${response.code}")
            return null
        }
        val rows = parse(response.body)
            ?.path("continuationContents", "musicLyricsContinuation", "lyricsTranslations") as? JsonArray
            ?: return null
        return rows.mapNotNull { (it as? JsonObject)?.get("translatedLyricText").text() }
            .takeIf { it.isNotEmpty() }
    }

    private fun parse(body: String): JsonObject? =
        runCatching { JsonParser.instance.parseToJsonElement(body) as? JsonObject }.getOrNull()

    private fun JsonElement.path(vararg keys: String): JsonElement? {
        var cur: JsonElement? = this
        for (k in keys) cur = (cur as? JsonObject)?.get(k) ?: return null
        return cur
    }

    private fun JsonElement?.string(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    /** Plain string, or a `{ runs: [{ text }] }` / `{ content }` text object. */
    private fun JsonElement?.text(): String? = when (this) {
        is JsonPrimitive -> if (isString) content else null
        is JsonObject -> (this["runs"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
            ?.joinToString("")
            ?: this["content"].string()
        else -> null
    }

    private fun JsonElement.findFirstObject(predicate: (JsonObject) -> Boolean): JsonObject? {
        val stack = ArrayDeque<JsonElement>()
        stack.addLast(this)
        while (stack.isNotEmpty()) {
            when (val el = stack.removeLast()) {
                is JsonObject -> {
                    if (predicate(el)) return el
                    el.values.reversed().forEach { stack.addLast(it) }
                }
                is JsonArray -> el.reversed().forEach { stack.addLast(it) }
                else -> Unit
            }
        }
        return null
    }
}

object YouTubeMusicProvider : LyricsProvider {
    override val name: String = YouTubeMusicLyrics.PROVIDER_NAME

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = runCatching {
        val page = YouTubeMusicLyrics.page(id) ?: throw IllegalStateException("No YouTube Music lyrics")
        YouTubeMusicLyrics.toLrc(page) ?: throw IllegalStateException("Empty YouTube Music lyrics")
    }

    override fun attributionFor(id: String): String? = YouTubeMusicLyrics.cachedPage(id)?.sourceMessage
}
