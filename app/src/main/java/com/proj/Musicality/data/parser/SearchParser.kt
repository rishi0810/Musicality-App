package com.proj.Musicality.data.parser

import com.proj.Musicality.data.json.*
import com.proj.Musicality.data.model.*
import com.proj.Musicality.util.upscaleThumbnail

object SearchParser {

    fun extractSongs(jsonResponse: String): List<SongResult> {
        val response = JsonParser.instance.decodeFromString<SearchResultResponse>(jsonResponse)
        val items = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.musicShelfRenderer?.contents ?: emptyList()

        return items.mapNotNull { wrapper ->
            val item = wrapper.musicResponsiveListItemRenderer ?: return@mapNotNull null
            val cols = item.flexColumns ?: return@mapNotNull null
            val title = cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: ""

            val videoId = cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                ?.navigationEndpoint?.watchEndpoint?.videoId
                ?: item.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer
                    ?.playNavigationEndpoint?.watchEndpoint?.videoId ?: ""

            val metadataRuns = cols.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs ?: emptyList()
            val artist = metadataRuns.getOrNull(0)?.text ?: ""
            val artistId = metadataRuns.getOrNull(0)?.navigationEndpoint?.browseEndpoint?.browseId
            val album = if (metadataRuns.size >= 3) metadataRuns[2].text else null
            val albumId = if (metadataRuns.size >= 3) metadataRuns[2].navigationEndpoint?.browseEndpoint?.browseId else null
            val duration = if (metadataRuns.size >= 5) metadataRuns[4].text
                else if (metadataRuns.size >= 3 && album == null) metadataRuns[2].text else null

            val plays = cols.getOrNull(2)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text

            val thumb = bestThumbUrl(
                (item.thumbnail?.musicThumbnailRenderer?.thumbnailImage
                    ?: item.thumbnail?.musicThumbnailRenderer?.thumbnail)
                    ?.thumbnails
            )

            SongResult(title, videoId, artist, artistId, album, albumId, duration, plays, thumb)
        }
    }

    fun extractVideos(jsonResponse: String): List<VideoResult> {
        val response = JsonParser.instance.decodeFromString<SearchResultResponse>(jsonResponse)
        val items = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.musicShelfRenderer?.contents ?: emptyList()

        return items.mapNotNull { wrapper ->
            val item = wrapper.musicResponsiveListItemRenderer ?: return@mapNotNull null
            val cols = item.flexColumns ?: return@mapNotNull null
            val title = cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: ""

            val videoId = item.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer
                ?.playNavigationEndpoint?.watchEndpoint?.videoId
                ?: cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                    ?.navigationEndpoint?.watchEndpoint?.videoId ?: ""

            val metadataRuns = cols.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs ?: emptyList()
            val artist = metadataRuns.getOrNull(0)?.text ?: ""
            val artistId = metadataRuns.getOrNull(0)?.navigationEndpoint?.browseEndpoint?.browseId
            val views = if (metadataRuns.size >= 3) metadataRuns[2].text else null
            val duration = if (metadataRuns.size >= 5) metadataRuns[4].text else null

            val thumb = bestThumbUrl(
                (item.thumbnail?.musicThumbnailRenderer?.thumbnailImage
                    ?: item.thumbnail?.musicThumbnailRenderer?.thumbnail)
                    ?.thumbnails
            )

            VideoResult(title, videoId, artist, artistId, views, duration, thumb)
        }
    }

    fun extractArtists(jsonResponse: String): List<ArtistResult> {
        val response = JsonParser.instance.decodeFromString<SearchResultResponse>(jsonResponse)
        val items = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.musicShelfRenderer?.contents ?: emptyList()

        return items.mapNotNull { wrapper ->
            val item = wrapper.musicResponsiveListItemRenderer ?: return@mapNotNull null
            val cols = item.flexColumns ?: return@mapNotNull null
            val name = cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: ""
            val artistId = item.navigationEndpoint?.browseEndpoint?.browseId ?: ""

            val subscribers = cols.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.lastOrNull()?.text

            val thumb = bestThumbUrl(
                (item.thumbnail?.musicThumbnailRenderer?.thumbnailImage
                    ?: item.thumbnail?.musicThumbnailRenderer?.thumbnail)
                    ?.thumbnails
            )

            ArtistResult(name, artistId, subscribers, thumb)
        }
    }

    fun extractAlbums(jsonResponse: String): List<AlbumResult> {
        val response = JsonParser.instance.decodeFromString<SearchResultResponse>(jsonResponse)
        val items = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.musicShelfRenderer?.contents ?: emptyList()

        return items.mapNotNull { wrapper ->
            val item = wrapper.musicResponsiveListItemRenderer ?: return@mapNotNull null
            val cols = item.flexColumns ?: return@mapNotNull null
            val title = cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: ""

            val albumId = item.navigationEndpoint?.browseEndpoint?.browseId
                ?: cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                    ?.navigationEndpoint?.browseEndpoint?.browseId ?: ""

            val metadataRuns = cols.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs ?: emptyList()
            val artist = if (metadataRuns.size >= 3) metadataRuns[2].text else ""
            val year = if (metadataRuns.size >= 5) metadataRuns[4].text else null

            val thumb = bestThumbUrl(
                (item.thumbnail?.musicThumbnailRenderer?.thumbnailImage
                    ?: item.thumbnail?.musicThumbnailRenderer?.thumbnail)
                    ?.thumbnails
            )

            AlbumResult(title, albumId, artist, year, thumb)
        }
    }

    fun extractPlaylists(jsonResponse: String): List<PlaylistResult> {
        val response = JsonParser.instance.decodeFromString<SearchResultResponse>(jsonResponse)
        val items = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.musicShelfRenderer?.contents ?: emptyList()

        return items.mapNotNull { wrapper ->
            val item = wrapper.musicResponsiveListItemRenderer ?: return@mapNotNull null
            val cols = item.flexColumns ?: return@mapNotNull null
            val title = cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: ""

            val playlistId = item.navigationEndpoint?.browseEndpoint?.browseId
                ?: cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                    ?.navigationEndpoint?.browseEndpoint?.browseId ?: ""

            val metadataRuns = cols.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs ?: emptyList()
            val author = metadataRuns.getOrNull(0)?.text ?: ""
            val countOrViews = if (metadataRuns.size >= 3) metadataRuns[2].text else null

            val thumb = bestThumbUrl(
                (item.thumbnail?.musicThumbnailRenderer?.thumbnailImage
                    ?: item.thumbnail?.musicThumbnailRenderer?.thumbnail)
                    ?.thumbnails
            )

            PlaylistResult(title, playlistId, author, countOrViews, thumb)
        }
    }

    private fun bestThumbUrl(thumbnails: List<Thumbnail>?): String? {
        val highest = thumbnails?.maxByOrNull { it.width }?.url
        return upscaleThumbnail(highest, size = 544)
    }
}
