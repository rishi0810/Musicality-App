package com.proj.Musicality.api

import android.util.Log
import com.proj.Musicality.cache.LruCache
import com.proj.Musicality.data.model.LyricLine
import com.proj.Musicality.data.model.LyricsState
import com.proj.Musicality.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object LyricsRepository {
    private const val TAG = "LyricsRepository"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cache = LruCache<String, LyricsState>(50)

    // "2:41" or "1:04:20" → total seconds
    private fun parseDurationSeconds(durationText: String?): Int {
        if (durationText.isNullOrBlank()) return 0
        val parts = durationText.split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }

    // "[MM:SS.xx] text" → LyricLine list
    private fun parseSyncedLyrics(raw: String): List<LyricLine> {
        val pattern = Regex("""^\[(\d{2}):(\d{2})\.(\d{2,3})\]\s*(.*)$""")
        return raw.lines().mapNotNull { line ->
            val m = pattern.matchEntire(line.trim()) ?: return@mapNotNull null
            val (min, sec, frac, text) = m.destructured
            val fracMs = when (frac.length) {
                2 -> frac.toLong() * 10
                3 -> frac.toLong()
                else -> 0L
            }
            val timeMs = (min.toLong() * 60 + sec.toLong()) * 1000L + fracMs
            if (text.isNotBlank()) LyricLine(timeMs, text.trim()) else null
        }
    }

    // Plain lyrics → assign sequential fake timestamps (no seeking)
    private fun parsePlainLyrics(raw: String): List<LyricLine> {
        return raw.lines()
            .filter { it.isNotBlank() }
            .mapIndexed { i, text -> LyricLine(i.toLong(), text.trim()) }
    }

    suspend fun fetchLyrics(item: MediaItem): LyricsState = withContext(Dispatchers.IO) {
        cache.get(item.videoId)?.let { return@withContext it }

        val artist = URLEncoder.encode(item.artistName, "UTF-8")
        val track = URLEncoder.encode(item.title, "UTF-8")
        val album = item.albumName?.let { URLEncoder.encode(it, "UTF-8") }
        val duration = parseDurationSeconds(item.durationText)

        val url = buildString {
            append("https://lrclib.net/api/get?artist_name=").append(artist)
            append("&track_name=").append(track)
            if (!album.isNullOrBlank()) append("&album_name=").append(album)
            if (duration > 0) append("&duration=").append(duration)
        }

        Log.d(TAG, "fetchLyrics: $url")

        val result = runCatching {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Musicality/1.0 (Android)")
                .get()
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (response.code == 404) return@runCatching LyricsState.NotFound
                if (!response.isSuccessful) return@runCatching LyricsState.Error("HTTP ${response.code}")
                response.body?.string()
            } ?: return@runCatching LyricsState.NotFound

            val json = JSONObject(body)
            val instrumental = json.optBoolean("instrumental", false)
            if (instrumental) return@runCatching LyricsState.NotFound

            val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() }
            val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() }

            when {
                synced != null -> {
                    val lines = parseSyncedLyrics(synced)
                    if (lines.isNotEmpty()) {
                        LyricsState.Loaded(lines, isSynced = true)
                    } else if (plain != null) {
                        // synced field present but parsed empty — fall back to plain
                        LyricsState.Loaded(parsePlainLyrics(plain), isSynced = false)
                    } else {
                        LyricsState.NotFound
                    }
                }
                plain != null -> LyricsState.Loaded(parsePlainLyrics(plain), isSynced = false)
                else -> LyricsState.NotFound
            }
        }.getOrElse { e ->
            Log.e(TAG, "fetchLyrics: exception", e)
            LyricsState.Error(e.message ?: "Unknown error")
        }

        // Cache successes + not-found; don't cache transient errors
        if (result is LyricsState.Loaded || result is LyricsState.NotFound) {
            cache.put(item.videoId, result)
        }
        result
    }
}
