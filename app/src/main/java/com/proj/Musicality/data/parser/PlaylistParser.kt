package com.proj.Musicality.data.parser

import com.proj.Musicality.data.json.*
import com.proj.Musicality.data.model.*

object PlaylistParser {

    fun extractPlaylistPage(jsonString: String): PlaylistPage? {
        val response = runCatching {
            JsonParser.instance.decodeFromString<PlaylistBrowseResponse>(jsonString)
        }.getOrNull() ?: return null

        val twoColumn = response.contents?.twoColumnBrowseResultsRenderer ?: return null

        val header = twoColumn.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer
            ?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer

        val title = header?.title?.runs?.firstOrNull()?.text ?: "Unknown Playlist"
        val year = header?.subtitle?.runs?.lastOrNull()?.text

        val subRuns = header?.secondSubtitle?.runs
            ?.filter { it.text != " \u2022 " }
            ?: emptyList()

        val viewCount: String?
        val trackCount: Int?
        val duration: String?

        if (subRuns.size >= 3) {
            viewCount = subRuns[0].text
            trackCount = subRuns[1].text.filter { it.isDigit() }.toIntOrNull()
            duration = subRuns[2].text
        } else {
            viewCount = null
            trackCount = subRuns.getOrNull(0)?.text?.filter { it.isDigit() }?.toIntOrNull()
            duration = subRuns.getOrNull(1)?.text
        }

        val thumbnails = header?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails
            ?.map { Thumbnail(it.url, it.width, it.height) } ?: emptyList()

        val description = header?.description?.musicDescriptionShelfRenderer?.description
            ?.runs?.joinToString("") { it.text }

        val author = header?.facepile?.avatarStackViewModel?.text?.content

        val shelf = twoColumn.secondaryContents?.sectionListRenderer?.contents
            ?.firstOrNull()?.musicPlaylistShelfRenderer

        val playlistId = shelf?.playlistId ?: ""

        val tracks = shelf?.contents?.mapNotNull { wrapper ->
            val item = wrapper.musicResponsiveListItemRenderer ?: return@mapNotNull null
            val cols = item.flexColumns ?: return@mapNotNull null

            val videoId = item.playlistItemData?.videoId ?: return@mapNotNull null
            val setVideoId = item.playlistItemData.playlistSetVideoId

            val trackTitle = cols.getOrNull(0)
                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text
                ?: return@mapNotNull null

            val artistRuns = cols.getOrNull(1)
                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs ?: emptyList()
            val structuredArtists = artistRuns.mapNotNull { run ->
                val browseId = run.navigationEndpoint?.browseEndpoint?.browseId
                if (browseId != null) PlaylistArtist(run.text, browseId) else null
            }
            val artists = structuredArtists.ifEmpty {
                val plainText = artistRuns.joinToString("") { it.text }.trim()
                if (plainText.isNotEmpty()) listOf(PlaylistArtist(plainText, null)) else emptyList()
            }

            val col2 = cols.getOrNull(2)
                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text
            val albumOrDate = if (!col2.isNullOrBlank()) col2 else null

            val dur = item.fixedColumns?.getOrNull(0)
                ?.musicResponsiveListItemFixedColumnRenderer?.text?.runs?.firstOrNull()?.text

            val thumb = item.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails
                ?.firstOrNull()?.url

            val musicVideoType = item.overlay?.musicItemThumbnailOverlayRenderer?.content
                ?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint
                ?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType

            PlaylistTrack(
                title = trackTitle,
                videoId = videoId,
                playlistSetVideoId = setVideoId,
                artists = artists,
                duration = dur,
                albumOrDate = albumOrDate,
                thumbnailUrl = thumb,
                musicVideoType = musicVideoType
            )
        } ?: emptyList()

        return PlaylistPage(
            playlistId = playlistId,
            title = title,
            description = description,
            author = author,
            year = year,
            viewCount = viewCount,
            trackCount = trackCount,
            duration = duration,
            thumbnails = thumbnails,
            tracks = tracks
        )
    }
}
