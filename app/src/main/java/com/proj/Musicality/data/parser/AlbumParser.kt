package com.proj.Musicality.data.parser

import com.proj.Musicality.data.json.*
import com.proj.Musicality.data.model.*

object AlbumParser {

    fun extractAlbumPage(jsonString: String): AlbumPage? {
        val response = runCatching {
            JsonParser.instance.decodeFromString<AlbumBrowseResponse>(jsonString)
        }.getOrNull() ?: return null

        val twoColumn = response.contents?.twoColumnBrowseResultsRenderer ?: return null

        val header = twoColumn.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer
            ?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer
            ?: return null

        val title = header.title?.runs?.firstOrNull()?.text ?: "Unknown Album"

        val artistName = header.straplineTextOne?.runs?.firstOrNull()?.text ?: "Unknown Artist"
        val artistId = header.straplineTextOne?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseId

        val year = header.subtitle?.runs?.lastOrNull()?.text

        val trackCountStr = header.secondSubtitle?.runs?.firstOrNull()?.text?.filter { it.isDigit() }
        val trackCount = trackCountStr?.toIntOrNull()
        val duration = header.secondSubtitle?.runs?.lastOrNull()?.text

        val thumbnail = header.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url

        val description = header.description?.musicDescriptionShelfRenderer?.description?.runs
            ?.joinToString("") { it.text }

        val shelf = twoColumn.secondaryContents?.sectionListRenderer?.contents?.firstOrNull()?.musicShelfRenderer

        val tracks = shelf?.contents?.mapNotNull { wrapper ->
            val renderer = wrapper.musicResponsiveListItemRenderer ?: return@mapNotNull null
            val cols = renderer.flexColumns ?: return@mapNotNull null

            val trackTitle = cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text
                ?: return@mapNotNull null

            val videoId = cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                ?.navigationEndpoint?.watchEndpoint?.videoId
                ?: renderer.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer
                    ?.playNavigationEndpoint?.watchEndpoint?.videoId

            val trackDuration = renderer.fixedColumns?.getOrNull(0)
                ?.musicResponsiveListItemFixedColumnRenderer?.text?.runs?.firstOrNull()?.text

            val plays = cols.getOrNull(2)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text

            val indexStr = renderer.index?.runs?.firstOrNull()?.text
            val index = indexStr?.toIntOrNull() ?: 0

            Track(trackTitle, videoId, trackDuration, plays, index)
        } ?: emptyList()

        return AlbumPage(
            title = title,
            description = description,
            artist = ArtistTiny(artistName, artistId),
            year = year,
            trackCount = trackCount,
            duration = duration,
            thumbnail = thumbnail,
            tracks = tracks
        )
    }
}
