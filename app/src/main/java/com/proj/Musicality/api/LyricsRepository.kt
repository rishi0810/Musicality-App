package com.proj.Musicality.api

import android.util.Log
import com.proj.Musicality.cache.LruCache
import com.proj.Musicality.data.model.LyricsState
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.data.model.ProviderLyricsSnapshot
import com.proj.Musicality.lyrics.LyricsHelper
import com.proj.Musicality.lyrics.LyricsWithProvider
import com.proj.Musicality.lyrics.filterLyricsCreditLines
import com.proj.Musicality.lyrics.lyricsTextLooksSynced
import com.proj.Musicality.lyrics.parseLrc
import com.proj.Musicality.lyrics.parsePlainLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Multi-provider lyrics flow: tries LyricsPlus first, falls back to LrcLib.
 * Supports word-level timing when the active provider returns extended LRC.
 */
object LyricsRepository {
    private const val TAG = "LyricsRepository"

    // NotFound/Error cache — LyricsHelper caches successes, this prevents re-racing
    // all providers for every playback of a song that has no lyrics anywhere.
    private val terminalCache = LruCache<String, LyricsState>(64)

    // "2:41" or "1:04:20" → total seconds
    fun parseDurationSeconds(durationText: String?): Int {
        if (durationText.isNullOrBlank()) return 0
        val parts = durationText.split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }

    /** Parse a raw LRC string + provider tag into a per-provider snapshot. */
    fun toSnapshot(item: MediaItem, result: LyricsWithProvider): ProviderLyricsSnapshot? {
        val filtered = filterLyricsCreditLines(result.lrc, item.title, item.artistName)
        val isSynced = lyricsTextLooksSynced(filtered)
        val lines = if (isSynced) parseLrc(filtered) else parsePlainLyrics(filtered)
        if (lines.isEmpty()) return null
        return ProviderLyricsSnapshot(lines = lines, isSynced = isSynced)
    }

    /** Fetch one specific provider (used for the dropdown switch). */
    suspend fun fetchProvider(item: MediaItem, providerName: String): LyricsWithProvider? =
        withContext(Dispatchers.IO) {
            val duration = parseDurationSeconds(item.durationText).takeIf { it > 0 } ?: -1
            runCatching {
                LyricsHelper.fetchByProvider(
                    providerName = providerName,
                    cacheKey = item.videoId,
                    id = item.videoId,
                    title = item.title,
                    artist = item.artistName,
                    duration = duration,
                    album = item.albumName,
                )
            }.getOrNull()
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
