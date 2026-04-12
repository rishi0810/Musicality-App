package com.proj.Musicality.data.parser

import com.proj.Musicality.data.json.*
import com.proj.Musicality.data.model.*
import com.proj.Musicality.util.upscaleThumbnail

object SearchParser {

    private const val PAGE_TYPE_ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
    private const val PAGE_TYPE_ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
    private val DURATION_REGEX = Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")
    private val YEAR_REGEX = Regex("""^(19|20)\d{2}$""")
    private val COUNT_SUFFIX_REGEX = Regex("""\s(plays|views)$""", RegexOption.IGNORE_CASE)

    private fun Run.pageTypeOrNull(): String? =
        navigationEndpoint?.browseEndpoint
            ?.browseEndpointContextSupportedConfigs
            ?.browseEndpointContextMusicConfig
            ?.pageType

    private fun List<Run>.findAlbumRun(): Run? =
        firstOrNull { it.pageTypeOrNull() == PAGE_TYPE_ALBUM }

    private fun List<Run>.findDurationText(): String? =
        lastOrNull { DURATION_REGEX.matches(it.text.trim()) }?.text

    private fun List<Run>.findYearText(): String? =
        lastOrNull { YEAR_REGEX.matches(it.text.trim()) }?.text

    private fun List<Run>.findCountText(): String? =
        firstOrNull { COUNT_SUFFIX_REGEX.containsMatchIn(it.text) }?.text

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
            val artistRun = metadataRuns.primaryArtistRun()
            val artist = artistRun?.text?.primaryArtistName().orEmpty()
            val artistId = artistRun?.artistBrowseIdOrNull()
            val albumRun = metadataRuns.findAlbumRun()
            val album = albumRun?.text
            val albumId = albumRun?.navigationEndpoint?.browseEndpoint?.browseId
            val duration = metadataRuns.findDurationText()

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
            val artistRun = metadataRuns.primaryArtistRun()
            val artist = artistRun?.text?.primaryArtistName().orEmpty()
            val artistId = artistRun?.artistBrowseIdOrNull()
            val views = metadataRuns.findCountText()
            val duration = metadataRuns.findDurationText()

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
            // First ARTIST-typed run is the primary artist (collab albums have
            // multiple ARTIST runs; we keep the first for this flat model).
            val artistRun = metadataRuns.firstOrNull { it.pageTypeOrNull() == PAGE_TYPE_ARTIST }
            val artist = artistRun?.text ?: ""
            val year = metadataRuns.findYearText()

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

    fun extractAll(jsonResponse: String): List<AllResult> {
        val response = JsonParser.instance.decodeFromString<SearchResultResponse>(jsonResponse)
        val sections = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents ?: emptyList()

        val results = mutableListOf<AllResult>()

        for (section in sections) {
            val topCard = section.musicCardShelfRenderer
            if (topCard != null) {
                val title = topCard.title?.runs?.firstOrNull()?.text ?: ""
                val subtitleRuns = topCard.subtitle?.runs ?: emptyList()
                val subtitle = subtitleRuns.joinToString("") { it.text }
                val typeText = subtitleRuns.firstOrNull()?.text ?: "Top Result"

                val id = topCard.title?.runs?.firstOrNull()?.navigationEndpoint?.watchEndpoint?.videoId
                    ?: topCard.title?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseId
                    ?: topCard.buttons?.firstOrNull()?.buttonRenderer?.command?.watchEndpoint?.videoId
                    ?: topCard.buttons?.firstOrNull()?.buttonRenderer?.command?.watchPlaylistEndpoint?.playlistId
                    ?: ""

                val thumb = bestThumbUrl(
                    (topCard.thumbnail?.musicThumbnailRenderer?.thumbnailImage
                        ?: topCard.thumbnail?.musicThumbnailRenderer?.thumbnail)
                        ?.thumbnails
                )

                results.add(AllResult(title, id, typeText, subtitle, thumb, isTopResult = true))
            }

            val shelf = section.musicShelfRenderer
            if (shelf != null) {
                val items = shelf.contents.mapNotNull { wrapper ->
                    val item = wrapper.musicResponsiveListItemRenderer ?: return@mapNotNull null
                    val cols = item.flexColumns ?: return@mapNotNull null

                    val titleRuns = cols.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs ?: emptyList()
                    val title = titleRuns.firstOrNull()?.text ?: ""

                    val navEndpoint = titleRuns.firstOrNull()?.navigationEndpoint
                    val id = navEndpoint?.watchEndpoint?.videoId
                        ?: navEndpoint?.watchPlaylistEndpoint?.playlistId
                        ?: navEndpoint?.browseEndpoint?.browseId
                        ?: item.navigationEndpoint?.browseEndpoint?.browseId
                        ?: item.navigationEndpoint?.watchEndpoint?.videoId
                        ?: ""

                    val metadataRuns = cols.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs ?: emptyList()
                    val subtitle = metadataRuns.joinToString("") { it.text }
                    val typeText = metadataRuns.firstOrNull()?.text ?: "Unknown"

                    val thumb = bestThumbUrl(
                        (item.thumbnail?.musicThumbnailRenderer?.thumbnailImage
                            ?: item.thumbnail?.musicThumbnailRenderer?.thumbnail)
                            ?.thumbnails
                    )

                    AllResult(title, id, typeText, subtitle, thumb, isTopResult = false)
                }
                results.addAll(items)
            }
        }
        return results
    }

    private fun bestThumbUrl(thumbnails: List<Thumbnail>?): String? {
        val highest = thumbnails?.maxByOrNull { it.width }?.url
        return upscaleThumbnail(highest, size = 544)
    }
}
