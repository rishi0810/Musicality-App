package com.proj.Musicality.api

import android.util.Log
import com.proj.Musicality.cache.LruCache
import com.proj.Musicality.data.model.LyricsState
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.lyrics.LyricsHelper
import com.proj.Musicality.lyrics.lyricsTextLooksSynced
import com.proj.Musicality.lyrics.parseLrc
import com.proj.Musicality.lyrics.parsePlainLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ported multi-provider lyrics flow: races LrcLib / BetterLyrics / LyricsPlus and
 * returns the first (highest-priority) winner. Supports word-level timing when the
 * winning provider gives us an extended LRC block.
 */
object LyricsRepository {
    private const val TAG = "LyricsRepository"

    // NotFound/Error cache — LyricsHelper caches successes, this prevents re-racing
    // all providers for every playback of a song that has no lyrics anywhere.
    private val terminalCache = LruCache<String, LyricsState>(64)

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

    suspend fun fetchLyrics(item: MediaItem): LyricsState = withContext(Dispatchers.IO) {
        terminalCache.get(item.videoId)?.let { return@withContext it }

        val duration = parseDurationSeconds(item.durationText).takeIf { it > 0 } ?: -1

        val result = runCatching {
            LyricsHelper.getLyrics(
                cacheKey = item.videoId,
                id = item.videoId,
                title = item.title,
                artist = item.artistName,
                duration = duration,
                album = item.albumName,
            )
        }.getOrElse { e ->
            Log.e(TAG, "getLyrics threw", e)
            return@withContext LyricsState.Error(e.message ?: "Unknown error")
        }

        if (result == null) {
            val state = LyricsState.NotFound
            terminalCache.put(item.videoId, state)
            return@withContext state
        }

        val isSynced = lyricsTextLooksSynced(result.lrc)
        val lines = if (isSynced) parseLrc(result.lrc) else parsePlainLyrics(result.lrc)
        if (lines.isEmpty()) {
            val state = LyricsState.NotFound
            terminalCache.put(item.videoId, state)
            return@withContext state
        }

        Log.d(TAG, "fetchLyrics: provider=${result.provider} lines=${lines.size} synced=$isSynced")
        LyricsState.Loaded(lines = lines, isSynced = isSynced, provider = result.provider)
    }
}
