package com.proj.Musicality.api

import android.util.Log
import com.proj.Musicality.cache.LruCache
import com.proj.Musicality.data.model.LyricsState
import com.proj.Musicality.data.model.LyricsTranslation
import com.proj.Musicality.data.model.TranslationOffer
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.data.model.ProviderLyricsSnapshot
import com.proj.Musicality.lyrics.LyricsHelper
import com.proj.Musicality.lyrics.LyricsWithProvider
import com.proj.Musicality.lyrics.filterLyricsCreditLines
import com.proj.Musicality.lyrics.lyricsTextLooksSynced
import com.proj.Musicality.lyrics.parseLrc
import com.proj.Musicality.lyrics.parsePlainLyrics
import com.proj.Musicality.lyrics.providers.YouTubeMusicLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Multi-provider lyrics flow (LyricsPlus → YouTube Music → LrcLib), plus Google
 * translations from YouTube Music. Everything is in-memory for the last
 * [LyricsHelper.RECENT_TRACKS] tracks only.
 */
object LyricsRepository {
    private const val TAG = "LyricsRepository"
    private const val NOT_FOUND_TTL_MS = 15 * 60_000L

    // LyricsHelper caches successes; this stops re-running every provider when the
    // user skips back to a song that had nothing. Expires so a flaky network or
    // timeout doesn't hide lyrics for the rest of the session.
    private val notFoundUntil = LruCache<String, Long>(LyricsHelper.RECENT_TRACKS)

    private val translationCache = LruCache<String, LyricsTranslation>(LyricsHelper.RECENT_TRACKS)

    private fun isKnownNotFound(videoId: String): Boolean {
        val until = notFoundUntil.get(videoId) ?: return false
        if (System.currentTimeMillis() < until) return true
        notFoundUntil.remove(videoId)
        return false
    }

    private fun markNotFound(videoId: String): LyricsState {
        notFoundUntil.put(videoId, System.currentTimeMillis() + NOT_FOUND_TTL_MS)
        return LyricsState.NotFound
    }

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
        return ProviderLyricsSnapshot(lines = lines, isSynced = isSynced, sourceMessage = result.sourceMessage)
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
        if (isKnownNotFound(item.videoId)) return@withContext LyricsState.NotFound

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

        if (result == null) return@withContext markNotFound(item.videoId)

        val isSynced = lyricsTextLooksSynced(result.lrc)
        val lines = if (isSynced) parseLrc(result.lrc) else parsePlainLyrics(result.lrc)
        if (lines.isEmpty()) return@withContext markNotFound(item.videoId)

        Log.d(TAG, "fetchLyrics: provider=${result.provider} lines=${lines.size} synced=$isSynced")
        LyricsState.Loaded(
            lines = lines,
            isSynced = isSynced,
            provider = result.provider,
            sourceMessage = result.sourceMessage,
        )
    }

    /**
     * Whether YouTube Music offers a translation into the device language.
     * Failure = couldn't check (network); success(null) = not offered.
     */
    suspend fun translationOffer(item: MediaItem): Result<TranslationOffer?> = withContext(Dispatchers.IO) {
        val hl = YouTubeMusicLyrics.translationLanguage()
        translationCache.get(translationKey(item.videoId, hl))?.let {
            return@withContext Result.success(TranslationOffer(it.attribution))
        }
        runCatching {
            val page = YouTubeMusicLyrics.page(item.videoId, hl)
            page?.translationToken?.let { TranslationOffer(page.translationSourceMessage) }
        }
    }

    /** Raw translated lines, index-aligned with YouTube Music's source lines. */
    suspend fun fetchTranslation(item: MediaItem): LyricsTranslation? = withContext(Dispatchers.IO) {
        val hl = YouTubeMusicLyrics.translationLanguage()
        val key = translationKey(item.videoId, hl)
        translationCache.get(key)?.let { return@withContext it }
        val lines = runCatching { YouTubeMusicLyrics.translations(item.videoId, hl) }
            .onFailure { Log.w(TAG, "fetchTranslation failed", it) }
            .getOrNull() ?: return@withContext null
        val attribution = YouTubeMusicLyrics.cachedPage(item.videoId, hl)?.translationSourceMessage
        LyricsTranslation(lines, attribution).also { translationCache.put(key, it) }
    }

    private fun translationKey(videoId: String, hl: String) = "$videoId|$hl"
}
