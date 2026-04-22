package com.proj.Musicality.lyrics.providers

import com.proj.Musicality.lyrics.LyricsProvider
import com.proj.Musicality.lyrics.lyricsHttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
private data class LrcLibTrack(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val duration: Double,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

object LrcLibProvider : LyricsProvider {
    override val name: String = "LrcLib"

    private const val BASE = "https://lrclib.net"

    private val titleCleanupPatterns = listOf(
        Regex("""\s*\(.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\[.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\]""", RegexOption.IGNORE_CASE),
        Regex("""\s*【.*?】"""),
        Regex("""\s*\|.*$"""),
        Regex("""\s*-\s*(official|video|audio|lyrics|lyric|visualizer).*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(feat\..*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(ft\..*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*feat\..*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*ft\..*$""", RegexOption.IGNORE_CASE),
    )

    private val artistSeparators = listOf(" & ", " and ", ", ", " x ", " X ", " feat. ", " feat ", " ft. ", " ft ", " featuring ", " with ")

    private fun cleanTitle(title: String): String {
        var cleaned = title.trim()
        for (p in titleCleanupPatterns) cleaned = cleaned.replace(p, "")
        return cleaned.trim()
    }

    private fun cleanArtist(artist: String): String {
        var cleaned = artist.trim()
        for (sep in artistSeparators) {
            if (cleaned.contains(sep, ignoreCase = true)) {
                cleaned = cleaned.split(sep, ignoreCase = true, limit = 2)[0]
                break
            }
        }
        return cleaned.trim()
    }

    private suspend fun query(
        trackName: String? = null,
        artistName: String? = null,
        albumName: String? = null,
        q: String? = null,
    ): List<LrcLibTrack> = runCatching {
        val response = lyricsHttpClient.get("$BASE/api/search") {
            if (q != null) parameter("q", q)
            if (trackName != null) parameter("track_name", trackName)
            if (artistName != null) parameter("artist_name", artistName)
            if (albumName != null) parameter("album_name", albumName)
        }
        if (response.status.isSuccess()) response.body<List<LrcLibTrack>>() else emptyList()
    }.getOrElse { emptyList() }

    private suspend fun searchWithStrategies(
        artist: String,
        title: String,
        album: String?,
    ): List<LrcLibTrack> {
        val ct = cleanTitle(title)
        val ca = cleanArtist(artist)

        query(trackName = ct, artistName = ca, albumName = album)
            .filter { it.syncedLyrics != null || it.plainLyrics != null }
            .takeIf { it.isNotEmpty() }?.let { return it }

        query(trackName = ct)
            .filter { it.syncedLyrics != null || it.plainLyrics != null }
            .takeIf { it.isNotEmpty() }?.let { return it }

        query(q = "$ca $ct")
            .filter { it.syncedLyrics != null || it.plainLyrics != null }
            .takeIf { it.isNotEmpty() }?.let { return it }

        query(q = ct)
            .filter { it.syncedLyrics != null || it.plainLyrics != null }
            .takeIf { it.isNotEmpty() }?.let { return it }

        if (ct != title.trim()) {
            return query(trackName = title.trim(), artistName = artist.trim())
                .filter { it.syncedLyrics != null || it.plainLyrics != null }
        }
        return emptyList()
    }

    /** Prefer synced within ±5 s of the requested duration; else any within ±5 s. */
    private fun bestMatch(tracks: List<LrcLibTrack>, duration: Int): LrcLibTrack? {
        if (tracks.isEmpty()) return null
        if (duration == -1) return tracks.firstOrNull { it.syncedLyrics != null } ?: tracks.firstOrNull()

        val synced = tracks.filter { it.syncedLyrics != null }
            .minByOrNull { abs(it.duration.toInt() - duration) }
            ?.takeIf { abs(it.duration.toInt() - duration) <= 5 }
        if (synced != null) return synced

        return tracks.minByOrNull { abs(it.duration.toInt() - duration) }
            ?.takeIf { abs(it.duration.toInt() - duration) <= 5 }
    }

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = runCatching {
        val tracks = searchWithStrategies(artist, title, album)
        val match = bestMatch(tracks, duration)
            ?: throw IllegalStateException("No LrcLib match")
        match.syncedLyrics ?: match.plainLyrics
            ?: throw IllegalStateException("LrcLib track has no lyrics")
    }
}
