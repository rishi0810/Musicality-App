package com.proj.Musicality.data.resolver

import android.util.Log
import com.proj.Musicality.api.RequestExecutor
import com.proj.Musicality.api.SearchType
import com.proj.Musicality.api.VisitorManager
import com.proj.Musicality.data.json.NextResponse
import com.proj.Musicality.data.json.PlaylistPanelVideoRenderer
import com.proj.Musicality.data.json.PlaylistPanelVideoWrapperRenderer
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.data.parser.JsonParser
import com.proj.Musicality.data.parser.SearchParser
import com.proj.Musicality.util.albumArtUrlOrNull

data class SongResolution(
    val videoId: String,
    val thumbnailUrl: String?,
    val musicVideoType: String?,
    val wasResolved: Boolean
)

object SongArtResolver {
    private const val TAG = "SongArtResolver"
    private const val MUSIC_VIDEO_TYPE_ATV = "MUSIC_VIDEO_TYPE_ATV"
    private const val MUSIC_VIDEO_TYPE_OMV = "MUSIC_VIDEO_TYPE_OMV"
    private const val MUSIC_VIDEO_TYPE_UGC = "MUSIC_VIDEO_TYPE_UGC"

    suspend fun resolveToAtv(item: MediaItem): SongResolution {
        if (item.videoId.isBlank()) {
            return SongResolution(item.videoId, null, item.musicVideoType, wasResolved = false)
        }

        val visitorId = VisitorManager.ensureBrowseVisitorId()
        if (visitorId.isBlank()) {
            return SongResolution(item.videoId, null, item.musicVideoType, wasResolved = false)
        }

        val nextJson = runCatching {
            RequestExecutor.executeNextRequest(item.videoId, visitorId)
        }.getOrDefault("")
        if (nextJson.isBlank()) {
            return SongResolution(item.videoId, null, item.musicVideoType, wasResolved = false)
        }

        val nextResponse = runCatching {
            JsonParser.instance.decodeFromString<NextResponse>(nextJson)
        }.getOrNull() ?: return SongResolution(item.videoId, null, item.musicVideoType, wasResolved = false)

        val firstItem = nextResponse.contents
            ?.singleColumnMusicWatchNextResultsRenderer
            ?.tabbedRenderer
            ?.watchNextTabbedResultsRenderer
            ?.tabs
            ?.firstOrNull()
            ?.tabRenderer
            ?.content
            ?.musicQueueRenderer
            ?.content
            ?.playlistPanelRenderer
            ?.contents
            ?.firstOrNull()

        firstItem ?: return SongResolution(item.videoId, null, item.musicVideoType, wasResolved = false)

        firstItem.playlistPanelVideoWrapperRenderer?.let { wrapper ->
            resolveFromWrapper(item, wrapper)?.let { return it }
        }

        firstItem.playlistPanelVideoRenderer?.let { renderer ->
            resolveFromDirectRenderer(item, renderer)?.let { return it }
        }

        return SongResolution(item.videoId, null, item.musicVideoType, wasResolved = false)
    }

    private suspend fun resolveFromWrapper(
        item: MediaItem,
        wrapper: PlaylistPanelVideoWrapperRenderer
    ): SongResolution? {
        val primary = wrapper.primaryRenderer?.playlistPanelVideoRenderer
        val counterpart = wrapper.counterpart
            ?.firstOrNull()
            ?.counterpartRenderer
            ?.playlistPanelVideoRenderer

        val primaryType = primary?.extractMusicVideoType()
        val counterpartType = counterpart?.extractMusicVideoType()

        if (primaryType == MUSIC_VIDEO_TYPE_ATV) {
            return SongResolution(item.videoId, null, MUSIC_VIDEO_TYPE_ATV, wasResolved = false)
        }

        if (counterpart != null && (counterpartType == MUSIC_VIDEO_TYPE_ATV || primaryType == MUSIC_VIDEO_TYPE_OMV)) {
            val counterpartVideoId = counterpart.videoId
                ?.takeIf { it.isNotBlank() }
                ?: counterpart.navigationEndpoint?.watchEndpoint?.videoId
                ?.takeIf { it.isNotBlank() }

            counterpartVideoId ?: return null

            val art = counterpart.bestAlbumArtUrl()
            return SongResolution(
                videoId = counterpartVideoId,
                thumbnailUrl = art,
                musicVideoType = MUSIC_VIDEO_TYPE_ATV,
                wasResolved = counterpartVideoId != item.videoId
            )
        }

        return null
    }

    private suspend fun resolveFromDirectRenderer(
        item: MediaItem,
        renderer: PlaylistPanelVideoRenderer
    ): SongResolution? {
        val type = renderer.extractMusicVideoType()
        if (type == MUSIC_VIDEO_TYPE_ATV) {
            return SongResolution(item.videoId, null, MUSIC_VIDEO_TYPE_ATV, wasResolved = false)
        }

        if (type != MUSIC_VIDEO_TYPE_OMV && type != MUSIC_VIDEO_TYPE_UGC) return null

        val title = renderer.title?.runs?.firstOrNull()?.text.orEmpty().trim()
        val artist = renderer.longBylineText?.runs?.firstOrNull()?.text
            ?.takeIf { it.isNotBlank() }
            ?: item.artistName

        if (title.isBlank()) return null

        return searchAtvByTitleArtist(
            expectedTitle = title,
            artist = artist,
            originalVideoId = item.videoId
        )
    }

    private suspend fun searchAtvByTitleArtist(
        expectedTitle: String,
        artist: String,
        originalVideoId: String
    ): SongResolution? {
        val visitorId = VisitorManager.ensureBrowseVisitorId()
        if (visitorId.isBlank()) return null

        val query = listOf(expectedTitle, artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        if (query.isBlank()) return null

        val searchJson = runCatching {
            RequestExecutor.executeSearchRequest(query, SearchType.SONGS.params, visitorId)
        }.onFailure {
            Log.w(TAG, "searchAtvByTitleArtist: search request failed", it)
        }.getOrDefault("")
        if (searchJson.isBlank()) return null

        val normalizedExpected = normalizeTitle(expectedTitle)
        val topMatches = SearchParser.extractSongs(searchJson).take(3)
        for (song in topMatches) {
            val candidateId = song.videoId.takeIf { it.isNotBlank() } ?: continue
            if (candidateId == originalVideoId) continue

            val normalizedCandidate = normalizeTitle(song.title)
            val titleMatches = normalizedCandidate == normalizedExpected ||
                normalizedCandidate.contains(normalizedExpected) ||
                normalizedExpected.contains(normalizedCandidate)
            if (!titleMatches) continue

            return SongResolution(
                videoId = candidateId,
                thumbnailUrl = albumArtUrlOrNull(song.thumb) ?: song.thumb,
                musicVideoType = MUSIC_VIDEO_TYPE_ATV,
                wasResolved = true
            )
        }
        return null
    }

    private fun normalizeTitle(value: String): String {
        return value.lowercase()
            .replace(Regex("""\((official music video|official video|official lyric video|lyric video|music video)\)"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun PlaylistPanelVideoRenderer.extractMusicVideoType(): String? =
        navigationEndpoint
            ?.watchEndpoint
            ?.watchEndpointMusicSupportedConfigs
            ?.watchEndpointMusicConfig
            ?.musicVideoType

    private fun PlaylistPanelVideoRenderer.bestAlbumArtUrl(): String? {
        val raw = thumbnail?.thumbnails?.maxByOrNull { it.width }?.url
        return albumArtUrlOrNull(raw)
    }
}
