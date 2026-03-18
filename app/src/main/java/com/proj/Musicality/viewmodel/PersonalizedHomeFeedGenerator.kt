package com.proj.Musicality.viewmodel

import com.proj.Musicality.api.RequestExecutor
import com.proj.Musicality.api.VisitorManager
import com.proj.Musicality.cache.AppCache
import com.proj.Musicality.data.local.ListeningEventDbRecord
import com.proj.Musicality.data.local.ListeningHistoryRepository
import com.proj.Musicality.data.local.ListeningHistorySnapshot
import com.proj.Musicality.data.local.SongPlayCountDbRecord
import com.proj.Musicality.data.model.ArtistContent
import com.proj.Musicality.data.model.ArtistDetails
import com.proj.Musicality.data.model.ArtistRelated
import com.proj.Musicality.data.model.ArtistSong
import com.proj.Musicality.data.model.HomeItem
import com.proj.Musicality.data.model.HomeSection
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.data.model.PlaybackQueue
import com.proj.Musicality.data.model.SectionLayout
import com.proj.Musicality.data.parser.ArtistParser
import com.proj.Musicality.data.parser.NextParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random

internal class PersonalizedHomeFeedGenerator(
    private val historyRepository: ListeningHistoryRepository
) {

    suspend fun snapshotPlan(): PersonalizedSnapshotPlan = coroutineScope {
        val snapshot = historyRepository.getSnapshot()
        PersonalizedSnapshotPlan(
            snapshotKey = snapshot.personalizationSnapshotKey(),
            reservedSectionCount = snapshot.predictedSectionCount()
        )
    }

    suspend fun generateSections(): PersonalizedSectionsResult = coroutineScope {
        val snapshot = historyRepository.getSnapshot()
        val recentUnique = snapshot.recentlyPlayed
            .distinctBy { it.videoId }
            .take(MAX_RECENT_UNIQUE)
        val snapshotKey = snapshot.personalizationSnapshotKey()
        val reservedSectionCount = snapshot.predictedSectionCount()

        if (recentUnique.isEmpty()) {
            return@coroutineScope PersonalizedSectionsResult(
                snapshotKey = snapshotKey,
                reservedSectionCount = 0,
                sections = emptyList()
            )
        }

        val random = Random(System.currentTimeMillis())
        val sections = when {
            snapshot.distinctSongCount >= 10 -> buildMatureSections(snapshot, recentUnique, random)
            snapshot.distinctSongCount >= 2 -> buildWarmSections(snapshot, recentUnique, random)
            snapshot.distinctSongCount == 1 -> buildColdSections(snapshot, recentUnique, random)
            else -> emptyList()
        }
        PersonalizedSectionsResult(
            snapshotKey = snapshotKey,
            reservedSectionCount = maxOf(reservedSectionCount, sections.size),
            sections = sections
        )
    }

    // ── Cold Tier (1 song) ──────────────────────────────────────────────

    private suspend fun buildColdSections(
        snapshot: ListeningHistorySnapshot,
        recentUnique: List<ListeningEventDbRecord>,
        random: Random
    ): List<HomeSection> = coroutineScope {
        val seed = recentUnique.first()
        val artistId = seed.artistId?.takeIf { it.isNotBlank() }

        // Wave 1: parallel radio + artist browse
        val radioDeferred = async {
            runCatching { fetchRadioSongs(seed.videoId, limit = 8) }.getOrDefault(emptyList())
        }
        val artistDeferred = async {
            runCatching { artistId?.let { fetchArtistDetails(it) } }.getOrNull()
        }

        val sections = mutableListOf<HomeSection>()

        // Continue Playing — hero card from local DB
        sections += HomeSection(
            title = "Continue Playing",
            items = listOf(seed.toHomeSong()),
            moreEndpoint = null,
            layoutHint = SectionLayout.HERO_CARD
        )

        // Similar to {song.title} — /next radio
        val radio = radioDeferred.await()
        if (radio.isNotEmpty()) {
            sections += HomeSection(
                title = "Similar to ${seed.title}",
                items = radio,
                moreEndpoint = null,
                layoutHint = SectionLayout.STACKED_SONGS
            )
        }

        // More like {artistName} — artist browse → 3 similar artists → top 4 each
        val artistDetails = artistDeferred.await()
        if (artistDetails != null && artistId != null) {
            val similarArtists = artistDetails.similarArtists.shuffled(random).take(3)

            // Wave 2: parallel similar artist detail fetches
            val similarSongs = similarArtists.map { related ->
                async {
                    runCatching {
                        fetchArtistDetails(related.browseId)
                            ?.topSongs
                            ?.take(4)
                            ?.map { it.toHomeSong(related.name, related.browseId) }
                            .orEmpty()
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten().distinctBy { it.videoId }.take(12)

            if (similarSongs.isNotEmpty()) {
                sections += HomeSection(
                    title = "More from ${artistDetails.name}",
                    items = similarSongs,
                    moreEndpoint = null,
                    layoutHint = SectionLayout.STACKED_SONGS
                )
            }
        }

        sections
    }

    // ── Warm Tier (2-9 songs) ───────────────────────────────────────────

    private suspend fun buildWarmSections(
        snapshot: ListeningHistorySnapshot,
        recentUnique: List<ListeningEventDbRecord>,
        random: Random
    ): List<HomeSection> = coroutineScope {
        val mostRecent = recentUnique.first()
        val latestArtistSeed = recentUnique
            .firstOrNull { !it.artistId.isNullOrBlank() }
            ?: mostRecent

        // Wave 1: 1 similar radio + up to 5 keep-listening radios + 1 artist browse
        val similarDeferred = async {
            runCatching { fetchRadioSongs(mostRecent.videoId, limit = 8) }.getOrDefault(emptyList())
        }
        val keepListeningSeeds = recentUnique.take(5)
        val keepListeningDeferred = keepListeningSeeds.map { seed ->
            async {
                runCatching { fetchRadioSongs(seed.videoId, limit = 5) }.getOrDefault(emptyList())
            }
        }
        val latestArtistDeferred = async {
            runCatching {
                latestArtistSeed.artistId?.takeIf { it.isNotBlank() }?.let { fetchArtistDetails(it) }
            }.getOrNull()
        }

        val sections = mutableListOf<HomeSection>()

        // Continue Playing — hero card
        sections += HomeSection(
            title = "Continue Playing",
            items = listOf(mostRecent.toHomeSong()),
            moreEndpoint = null,
            layoutHint = SectionLayout.HERO_CARD
        )

        // Keep Listening — /next for each unique song, mixed & shuffled
        val keepListeningItems = keepListeningDeferred.awaitAll()
            .flatten()
            .distinctBy { it.videoId }
            .shuffled(random)
            .take(16)

        if (keepListeningItems.isNotEmpty()) {
            sections += HomeSection(
                title = "Keep Listening",
                items = keepListeningItems,
                moreEndpoint = null,
                layoutHint = SectionLayout.STACKED_SONGS
            )
        }

        // Similar to {mostRecent.title} — /next radio
        val similarRadio = similarDeferred.await()
        if (similarRadio.isNotEmpty()) {
            sections += HomeSection(
                title = "Similar to ${mostRecent.title}",
                items = similarRadio,
                moreEndpoint = null,
                layoutHint = SectionLayout.STACKED_SONGS
            )
        }

        // More like {latestArtist} — latest artist browse → top 8
        val latestArtistDetails = latestArtistDeferred.await()
        if (latestArtistDetails != null) {
            val latestArtistId = latestArtistSeed.artistId?.takeIf { it.isNotBlank() }
            val artistSongs = latestArtistDetails.topSongs
                .map { it.toHomeSong(latestArtistDetails.name, latestArtistId) }
                .distinctBy { it.videoId }
                .take(8)

            if (artistSongs.isNotEmpty()) {
                sections += HomeSection(
                    title = "More from ${latestArtistDetails.name}",
                    items = artistSongs,
                    moreEndpoint = null,
                    layoutHint = SectionLayout.STACKED_SONGS
                )
            }

            // Wave 2: Because You Like {artist} — 3 similar artists → top 5 songs each
            val similarArtists = latestArtistDetails.similarArtists.shuffled(random).take(3)
            val fansAlsoSongs = similarArtists.map { related ->
                async {
                    runCatching {
                        fetchArtistDetails(related.browseId)
                            ?.topSongs
                            ?.take(5)
                            ?.map { it.toHomeSong(related.name, related.browseId) }
                            .orEmpty()
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten().distinctBy { it.videoId }.take(12)

            if (fansAlsoSongs.isNotEmpty()) {
                sections += HomeSection(
                    title = "Because You Like ${latestArtistDetails.name}",
                    items = fansAlsoSongs,
                    moreEndpoint = null
                )
            }
        }

        sections
    }

    // ── Mature Tier (10+ songs) ─────────────────────────────────────────

    private suspend fun buildMatureSections(
        snapshot: ListeningHistorySnapshot,
        recentUnique: List<ListeningEventDbRecord>,
        random: Random
    ): List<HomeSection> = coroutineScope {
        val mostRecent = recentUnique.first()
        val topSongs = snapshot.topSongs.take(10)
        if (topSongs.isEmpty()) return@coroutineScope emptyList()

        val topArtist = snapshot.topArtists.firstOrNull()
            ?: topSongs.firstNotNullOfOrNull { song ->
                song.artistId?.takeIf { it.isNotBlank() }?.let { id ->
                    com.proj.Musicality.data.local.ArtistPlayCountDbRecord(
                        artistId = id,
                        artistName = song.artistName,
                        playCount = song.playCount,
                        lastPlayedAt = song.lastPlayedAt
                    )
                }
            }

        // Pick 2 different random top songs for "Similar to" sections
        val topSongPool = topSongs.shuffled(random)
        val randomTopSong1 = topSongPool.first()
        val randomTopSong2 = topSongPool.drop(1).firstOrNull() ?: randomTopSong1

        // Keep Listening seeds: top 3 songs
        val keepListeningSeeds = topSongs.take(3)

        // Rediscover seed: songs played >3 days ago, not in top 5 recent
        val threeDaysAgo = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000L
        val recentTop5Ids = recentUnique.take(5).map { it.videoId }.toSet()
        val rediscoverSeed = recentUnique
            .filter { it.playedAt < threeDaysAgo && it.videoId !in recentTop5Ids }
            .firstOrNull()

        // Time Capsule: top songs whose lastPlayedAt > 24h ago
        val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val timeCapsuleItems = topSongs
            .filter { it.lastPlayedAt < oneDayAgo }
            .take(8)
            .map { it.toHomeSong() }

        // Wave 1: ~8 parallel fetches
        val keepListeningDeferred = keepListeningSeeds.map { song ->
            async {
                runCatching { fetchRadioSongs(song.videoId, limit = 8) }.getOrDefault(emptyList())
            }
        }
        val similar1Deferred = async {
            runCatching { fetchRadioSongs(randomTopSong1.videoId, limit = 8) }.getOrDefault(emptyList())
        }
        val similar2Deferred = async {
            runCatching { fetchRadioSongs(randomTopSong2.videoId, limit = 8) }.getOrDefault(emptyList())
        }
        val topArtistDeferred = async {
            runCatching {
                topArtist?.artistId?.takeIf { it.isNotBlank() }?.let { fetchArtistDetails(it) }
            }.getOrNull()
        }
        val rediscoverDeferred = async {
            if (rediscoverSeed != null) {
                runCatching { fetchRadioSongs(rediscoverSeed.videoId, limit = 8) }.getOrDefault(emptyList())
            } else emptyList()
        }

        val sections = mutableListOf<HomeSection>()

        // Continue Playing — hero + top 3 picks
        val heroItem = mostRecent.toHomeSong()
        val topPicksExcluding = topSongs
            .filter { it.videoId != mostRecent.videoId }
            .take(3)
            .map { it.toHomeSong() }
        val heroItems = listOf(heroItem) + topPicksExcluding

        sections += HomeSection(
            title = "Continue Playing",
            items = heroItems,
            moreEndpoint = null,
            layoutHint = SectionLayout.HERO_WITH_TOP_PICKS
        )

        // Keep Listening — top 3 song radios interleaved
        val keepListeningResults = keepListeningDeferred.awaitAll()
        val keepListeningItems = interleaveMix(
            primary = keepListeningResults.flatMap { it },
            secondary = keepListeningSeeds.map { it.toHomeSong() },
            random = random,
            maxItems = 20
        )

        if (keepListeningItems.isNotEmpty()) {
            sections += HomeSection(
                title = "Keep Listening",
                items = keepListeningItems,
                moreEndpoint = null,
                layoutHint = SectionLayout.STACKED_SONGS
            )
        }

        // Similar to {topSong1.title}
        val similar1 = similar1Deferred.await()
        if (similar1.isNotEmpty()) {
            sections += HomeSection(
                title = "Similar to ${randomTopSong1.title}",
                items = similar1,
                moreEndpoint = null,
                layoutHint = SectionLayout.STACKED_SONGS
            )
        }

        // Similar to {topSong2.title}
        if (randomTopSong2.videoId != randomTopSong1.videoId) {
            val similar2 = similar2Deferred.await()
            if (similar2.isNotEmpty()) {
                sections += HomeSection(
                    title = "Similar to ${randomTopSong2.title}",
                    items = similar2,
                    moreEndpoint = null,
                    layoutHint = SectionLayout.STACKED_SONGS
                )
            }
        }

        // More from {topArtist} — album carousel
        val topArtistDetails = topArtistDeferred.await()
        if (topArtistDetails != null) {
            val albumCards = topArtistDetails.albums
                .shuffled(random)
                .take(8)
                .map { it.toAlbumCard(topArtistDetails.name) }

            if (albumCards.isNotEmpty()) {
                sections += HomeSection(
                    title = "More from ${topArtistDetails.name}",
                    items = albumCards,
                    moreEndpoint = null,
                    layoutHint = SectionLayout.ALBUM_CAROUSEL
                )
            }
        }

        // Your Top Artists — top 5 artists as artist cards
        // Wave 2: fetch thumbnails for top artists (mostly cache hits)
        val topArtistRecords = snapshot.topArtists.take(5)
        if (topArtistRecords.isNotEmpty()) {
            val artistCards = topArtistRecords.map { record ->
                async {
                    runCatching {
                        val details = fetchArtistDetails(record.artistId)
                        if (details != null) {
                            HomeItem.Card(
                                id = record.artistId,
                                videoId = null,
                                title = details.name,
                                subtitle = null,
                                thumbnailUrl = details.thumbnails.lastOrNull()?.url,
                                pageType = "MUSIC_PAGE_TYPE_ARTIST",
                                musicVideoType = null
                            )
                        } else {
                            HomeItem.Card(
                                id = record.artistId,
                                videoId = null,
                                title = record.artistName,
                                subtitle = null,
                                thumbnailUrl = null,
                                pageType = "MUSIC_PAGE_TYPE_ARTIST",
                                musicVideoType = null
                            )
                        }
                    }.getOrElse {
                        HomeItem.Card(
                            id = record.artistId,
                            videoId = null,
                            title = record.artistName,
                            subtitle = null,
                            thumbnailUrl = null,
                            pageType = "MUSIC_PAGE_TYPE_ARTIST",
                            musicVideoType = null
                        )
                    }
                }
            }.awaitAll()

            sections += HomeSection(
                title = "Your Top Artists",
                items = artistCards,
                moreEndpoint = null
            )
        }

        // Rediscover — songs played >3 days ago, seed through /next
        val rediscoverRadio = rediscoverDeferred.await()
        if (rediscoverRadio.isNotEmpty()) {
            sections += HomeSection(
                title = "Rediscover",
                items = rediscoverRadio,
                moreEndpoint = null,
                layoutHint = SectionLayout.STACKED_SONGS
            )
        }

        // Time Capsule — top songs by play count whose lastPlayedAt > 24h ago
        if (timeCapsuleItems.isNotEmpty()) {
            sections += HomeSection(
                title = "Time Capsule",
                items = timeCapsuleItems,
                moreEndpoint = null,
                layoutHint = SectionLayout.STACKED_SONGS
            )
        }

        sections
    }

    // ── Helpers (unchanged) ─────────────────────────────────────────────

    private suspend fun fetchRadioSongs(seedVideoId: String, limit: Int): List<HomeItem.Song> {
        if (seedVideoId.isBlank()) return emptyList()

        val cacheKey = "next:$seedVideoId"
        val cachedQueue = AppCache.browse.get(cacheKey) as? PlaybackQueue
        val queue = cachedQueue ?: run {
            val json = executeNextWithRecovery(seedVideoId)
            if (json.isBlank()) return emptyList()
            val parsed = NextParser.extractUpNextQueue(json) ?: return emptyList()
            AppCache.browse.put(cacheKey, parsed)
            parsed
        }

        return queue.items
            .asSequence()
            .filter { it.videoId.isNotBlank() && it.videoId != seedVideoId }
            .map { it.toHomeSong() }
            .distinctBy { it.videoId }
            .take(limit)
            .toList()
    }

    private suspend fun fetchArtistDetails(artistId: String): ArtistDetails? {
        if (artistId.isBlank()) return null
        val cached = AppCache.browse.get(artistId) as? ArtistDetails
        if (cached != null) return cached

        val json = VisitorManager.executeBrowseRequestWithRecovery(artistId)
        if (json.isBlank()) return null

        val parsed = ArtistParser.extractArtistDetails(json) ?: return null
        AppCache.browse.put(artistId, parsed)
        return parsed
    }

    private suspend fun executeNextWithRecovery(videoId: String): String {
        val initialId = VisitorManager.ensureBrowseVisitorId()
        if (initialId.isBlank()) return ""

        val first = runCatching { RequestExecutor.executeNextRequest(videoId, initialId) }
            .getOrDefault("")
        if (first.isNotBlank()) return first

        val refreshedId = VisitorManager.refreshBrowseVisitorId()
        if (refreshedId.isBlank() || refreshedId == initialId) return first
        return runCatching { RequestExecutor.executeNextRequest(videoId, refreshedId) }
            .getOrDefault(first)
    }

    private fun interleaveMix(
        primary: List<HomeItem>,
        secondary: List<HomeItem>,
        random: Random,
        maxItems: Int
    ): List<HomeItem> {
        val a = primary.shuffled(random).toMutableList()
        val b = secondary.shuffled(random).toMutableList()
        val result = ArrayList<HomeItem>(maxItems)

        while (result.size < maxItems && (a.isNotEmpty() || b.isNotEmpty())) {
            if (a.isNotEmpty()) result += a.removeAt(0)
            if (result.size >= maxItems) break
            if (b.isNotEmpty()) result += b.removeAt(0)
        }

        return result
            .distinctBy { it.key() }
            .take(maxItems)
    }

    private fun ListeningEventDbRecord.toHomeSong(): HomeItem.Song = HomeItem.Song(
        videoId = videoId,
        playlistId = null,
        title = title,
        artistName = artistName,
        artistId = artistId,
        albumName = null,
        albumId = null,
        plays = null,
        thumbnailUrl = thumbnailUrl,
        musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
    )

    private fun SongPlayCountDbRecord.toHomeSong(): HomeItem.Song = HomeItem.Song(
        videoId = videoId,
        playlistId = null,
        title = title,
        artistName = artistName,
        artistId = artistId,
        albumName = null,
        albumId = null,
        plays = "$playCount plays",
        thumbnailUrl = thumbnailUrl,
        musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
    )

    private fun MediaItem.toHomeSong(): HomeItem.Song = HomeItem.Song(
        videoId = videoId,
        playlistId = null,
        title = title,
        artistName = artistName,
        artistId = artistId,
        albumName = albumName,
        albumId = albumId,
        plays = null,
        thumbnailUrl = thumbnailUrl,
        musicVideoType = musicVideoType ?: "MUSIC_VIDEO_TYPE_ATV"
    )

    private fun ArtistSong.toHomeSong(artistName: String, artistId: String?): HomeItem.Song = HomeItem.Song(
        videoId = videoId,
        playlistId = null,
        title = title,
        artistName = artistName,
        artistId = artistId,
        albumName = album,
        albumId = null,
        plays = plays,
        thumbnailUrl = image,
        musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
    )

    private fun ArtistContent.toAlbumCard(artistName: String): HomeItem.Card = HomeItem.Card(
        id = browseId,
        videoId = null,
        title = title,
        subtitle = listOfNotNull(artistName.takeIf { it.isNotBlank() }, year).joinToString(" • ")
            .ifBlank { null },
        thumbnailUrl = image,
        pageType = "MUSIC_PAGE_TYPE_ALBUM",
        musicVideoType = null
    )

    private fun ArtistRelated.toArtistCard(): HomeItem.Card = HomeItem.Card(
        id = browseId,
        videoId = null,
        title = name,
        subtitle = subscribers,
        thumbnailUrl = image,
        pageType = "MUSIC_PAGE_TYPE_ARTIST",
        musicVideoType = null
    )

    private fun HomeItem.key(): String = when (this) {
        is HomeItem.Song -> "song-$videoId"
        is HomeItem.Card -> "card-${id ?: videoId ?: title}"
        is HomeItem.NavButton -> "nav-$browseId"
        is HomeItem.PodcastEpisode -> "podcast-$videoId"
    }

    private fun ListeningHistorySnapshot.personalizationSnapshotKey(): String = buildString {
        append(distinctSongCount)
        append('|')
        recentlyPlayed
            .take(12)
            .forEach { event ->
                append(event.videoId)
                append('@')
                append(event.playedAt)
                append(';')
            }
        append('|')
        topArtists
            .take(5)
            .forEach { artist ->
                append(artist.artistId)
                append('#')
                append(artist.playCount)
                append(';')
            }
    }

    private fun ListeningHistorySnapshot.predictedSectionCount(): Int = when {
        recentlyPlayed.isEmpty() -> 0
        distinctSongCount >= 10 -> 8
        distinctSongCount >= 2 -> 5
        else -> 3
    }

    companion object {
        private const val MAX_RECENT_UNIQUE = 20
    }
}

internal data class PersonalizedSnapshotPlan(
    val snapshotKey: String,
    val reservedSectionCount: Int
)

internal data class PersonalizedSectionsResult(
    val snapshotKey: String,
    val reservedSectionCount: Int,
    val sections: List<HomeSection>
)
