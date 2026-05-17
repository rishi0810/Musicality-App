package com.proj.Musicality.lyrics

import android.util.Log
import com.proj.Musicality.cache.LruCache
import com.proj.Musicality.lyrics.providers.BetterLyricsProvider
import com.proj.Musicality.lyrics.providers.LrcLibProvider
import com.proj.Musicality.lyrics.providers.LyricsPlusProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class LyricsWithProvider(val lrc: String, val provider: String)

/**
 * Orchestrates a tiered race across the enabled providers.
 * Simplified vs. upstream: provider order is fixed (no DataStore / no user
 * reordering), all providers are always "enabled".
 */
object LyricsHelper {

    private const val TAG = "LyricsHelper"
    private const val MAX_LYRICS_FETCH_MS = 30_000L
    private const val GRACE_PERIOD_MS = 4_000L
    private const val TIER_SIZE = 2
    private const val MAX_CACHE_SIZE = 64

    private val providers: List<LyricsProvider> = listOf(
        BetterLyricsProvider,   // Apple-Music-style word sync, highest quality
        LyricsPlusProvider,     // Word sync via Binimum + LyricsPlus mirrors
        LrcLibProvider,         // Line-level, widest coverage
    )

    /** Ordered, stable display names — drives the provider-switch dropdown. */
    val providerNames: List<String> = providers.map { it.name }

    private val cache = LruCache<String, LyricsWithProvider>(MAX_CACHE_SIZE)

    // Per-(provider, cacheKey) cache so the switch dropdown is instant on second open
    // and so the race + lazy-load paths don't duplicate work for the same track.
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
        val winner = withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
            raceProviders(cacheKey, id, cleanedTitle, artist, duration, album)
        }

        if (winner != null) {
            val filtered = winner.copy(lrc = filterLyricsCreditLines(winner.lrc, title, artist))
            cache.put(cacheKey, filtered)
            return filtered
        }
        return null
    }

    private suspend fun raceProviders(
        cacheKey: String,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): LyricsWithProvider? = coroutineScope {
        val channel = Channel<Pair<Int, LyricsWithProvider?>>(capacity = providers.size)
        val launched = mutableListOf<Job>()

        // Launch the first tier.
        for (i in 0 until minOf(TIER_SIZE, providers.size)) {
            launched += launchProvider(i, providers[i], cacheKey, id, title, artist, duration, album, channel)
        }

        var nextTier = TIER_SIZE
        var bestIndex = Int.MAX_VALUE
        var bestResult: LyricsWithProvider? = null
        val remaining = (0 until providers.size).toMutableSet()

        val collector = launch {
            for ((index, res) in channel) {
                remaining.remove(index)
                if (res != null && index < bestIndex) {
                    bestIndex = index
                    bestResult = res
                }
                // No higher-priority provider can still beat our best? Done.
                if (remaining.none { it < bestIndex }) {
                    channel.cancel()
                    break
                }
            }
        }

        // Stagger in additional tiers only while we have nothing yet.
        while (nextTier < providers.size && collector.isActive) {
            delay(GRACE_PERIOD_MS)
            if (bestResult == null && collector.isActive) {
                for (i in nextTier until minOf(nextTier + TIER_SIZE, providers.size)) {
                    launched += launchProvider(i, providers[i], cacheKey, id, title, artist, duration, album, channel)
                }
                nextTier += TIER_SIZE
            } else break
        }

        collector.join()
        launched.forEach { it.cancel() }
        bestResult
    }

    private fun CoroutineScope.launchProvider(
        index: Int,
        provider: LyricsProvider,
        cacheKey: String,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        channel: Channel<Pair<Int, LyricsWithProvider?>>,
    ): Job = launch {
        try {
            val result = provider.getLyrics(id, title, artist, duration, album)
            if (result.isSuccess) {
                val lrc = result.getOrNull()!!
                Log.i(TAG, "${provider.name} won (index=$index)")
                val out = LyricsWithProvider(lrc, provider.name)
                // Pre-populate the per-provider cache so the dropdown switch is
                // instant for any provider that finished its fetch (even ones the
                // race didn't pick).
                providerCache.put(providerCacheKey(provider.name, cacheKey), out)
                channel.send(index to out)
            } else {
                Log.d(TAG, "${provider.name} failed: ${result.exceptionOrNull()?.message}")
                channel.send(index to null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "${provider.name} threw", e)
            channel.send(index to null)
        }
    }
}
