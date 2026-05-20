package com.proj.Musicality.lyrics

import android.util.Log
import com.proj.Musicality.cache.LruCache
import com.proj.Musicality.lyrics.providers.BetterLyricsProvider
import com.proj.Musicality.lyrics.providers.LrcLibProvider
import com.proj.Musicality.lyrics.providers.LyricsPlusProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

data class LyricsWithProvider(val lrc: String, val provider: String)

/**
 * Sequential lyrics fetch across enabled providers.
 * Order matters: the first provider that returns a usable LRC wins, no race.
 *
 * BetterLyrics is intentionally excluded from [providers] right now (its
 * upstream has been unreliable). The class is still imported and kept on
 * disk so re-enabling it later is a one-line change.
 */
object LyricsHelper {

    private const val TAG = "LyricsHelper"
    private const val MAX_LYRICS_FETCH_MS = 30_000L
    private const val MAX_CACHE_SIZE = 64

    @Suppress("unused")
    private val disabledProviders: List<LyricsProvider> = listOf(BetterLyricsProvider)

    private val providers: List<LyricsProvider> = listOf(
        LyricsPlusProvider,     // Primary: word-sync via Binimum + LyricsPlus mirrors
        LrcLibProvider,         // Fallback: line-level, widest coverage
    )

    /** Ordered, stable display names — drives the provider-switch dropdown. */
    val providerNames: List<String> = providers.map { it.name }

    private val cache = LruCache<String, LyricsWithProvider>(MAX_CACHE_SIZE)

    // Per-(provider, cacheKey) cache so the switch dropdown is instant on second open
    // and so the sequential fetch + lazy-load paths don't duplicate work for the same track.
    private val providerCache = LruCache<String, LyricsWithProvider>(MAX_CACHE_SIZE * 2)

    private fun providerCacheKey(providerName: String, cacheKey: String) =
        "$providerName::$cacheKey"

    /** Fetch from one specific provider by display name. Cached. */
    suspend fun fetchByProvider(
        providerName: String,
        cacheKey: String,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): LyricsWithProvider? {
        val provider = providers.firstOrNull { it.name == providerName } ?: return null
        val pcKey = providerCacheKey(providerName, cacheKey)
        providerCache.get(pcKey)?.let { return it }

        val cleanedTitle = cleanTitleForSearch(title)
        val result = runCatching {
            provider.getLyrics(id, cleanedTitle, artist, duration, album)
        }.getOrElse {
            Log.w(TAG, "${provider.name} threw", it)
            return null
        }
        if (result.isFailure) return null
        val lrc = result.getOrNull() ?: return null
        val out = LyricsWithProvider(lrc, provider.name)
        providerCache.put(pcKey, out)
        return out
    }

    suspend fun getLyrics(
        cacheKey: String,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): LyricsWithProvider? {
        cache.get(cacheKey)?.let { return it }

        val cleanedTitle = cleanTitleForSearch(title)
        val result = withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
            fetchSequential(cacheKey, id, cleanedTitle, artist, duration, album)
        }

        if (result != null) {
            val filtered = result.copy(lrc = filterLyricsCreditLines(result.lrc, title, artist))
            cache.put(cacheKey, filtered)
            return filtered
        }
        return null
    }

    private suspend fun fetchSequential(
        cacheKey: String,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): LyricsWithProvider? {
        for (provider in providers) {
            val attempt = try {
                provider.getLyrics(id, title, artist, duration, album)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "${provider.name} threw", e)
                continue
            }

            if (attempt.isFailure) {
                Log.d(TAG, "${provider.name} failed: ${attempt.exceptionOrNull()?.message}")
                continue
            }
            val lrc = attempt.getOrNull() ?: continue
            Log.i(TAG, "${provider.name} returned lyrics")
            val out = LyricsWithProvider(lrc, provider.name)
            // Pre-populate the per-provider cache so the dropdown is instant if the
            // user opens it without triggering the lazy-load path.
            providerCache.put(providerCacheKey(provider.name, cacheKey), out)
            return out
        }
        return null
    }
}
