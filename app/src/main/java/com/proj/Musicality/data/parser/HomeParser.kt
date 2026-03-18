package com.proj.Musicality.data.parser

import com.proj.Musicality.data.json.*
import com.proj.Musicality.data.model.*
import com.proj.Musicality.util.upscaleThumbnail

object HomeParser {

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Parse the first page of the home feed (browseId = "FEmusic_home"). */
    fun extractFeed(jsonResponse: String): HomeFeed {
        val response = JsonParser.instance.decodeFromString<HomeFeedResponse>(jsonResponse)
        val sectionList = response.contents
            ?.singleColumnBrowseResultsRenderer
            ?.tabs?.firstOrNull()
            ?.tabRenderer?.content
            ?.sectionListRenderer

        return HomeFeed(
            sections = parseSections(sectionList?.contents ?: emptyList()),
            continuation = sectionList?.continuations?.firstOrNull()
                ?.nextContinuationData?.continuation
        )
    }

    /**
     * Parse a continuation page.
     * Pass the [HomeFeed.continuation] token from the previous call as the
     * `ctoken` / `continuation` query parameter to the browse endpoint.
     */
    fun extractContinuation(jsonResponse: String): HomeFeed {
        val response = JsonParser.instance.decodeFromString<HomeContinuationResponse>(jsonResponse)
        val sectionList = response.continuationContents?.sectionListContinuation

        return HomeFeed(
            sections = parseSections(sectionList?.contents ?: emptyList()),
            continuation = sectionList?.continuations?.firstOrNull()
                ?.nextContinuationData?.continuation
        )
    }

    // ── Section parsing ────────────────────────────────────────────────────────

    private fun parseSections(contents: List<HomeSectionItem>): List<HomeSection> =
        contents.mapNotNull { sectionItem ->
            val shelf = sectionItem.musicCarouselShelfRenderer ?: return@mapNotNull null
            val header = shelf.header?.musicCarouselShelfBasicHeaderRenderer

            val title = header?.title?.runs?.firstOrNull()?.text
                ?: return@mapNotNull null

            val moreEndpoint = parseMoreEndpoint(
                header.moreContentButton?.buttonRenderer?.navigationEndpoint
            )

            val items = shelf.contents?.mapNotNull(::parseItem) ?: emptyList()
            if (items.isEmpty()) return@mapNotNull null

            HomeSection(title, items, moreEndpoint)
        }

    private fun parseMoreEndpoint(nav: NavigationEndpoint?): HomeMoreEndpoint? {
        nav ?: return null
        return when {
            nav.watchEndpoint != null -> {
                val ep = nav.watchEndpoint
                val videoId = ep.videoId ?: return null
                HomeMoreEndpoint(
                    watchEndpoint = HomeWatchEndpoint(videoId, ep.playlistId, ep.params),
                    browseEndpoint = null
                )
            }
            nav.browseEndpoint != null -> {
                val ep = nav.browseEndpoint
                val browseId = ep.browseId ?: return null
                HomeMoreEndpoint(
                    watchEndpoint = null,
                    browseEndpoint = HomeBrowseEndpoint(browseId, params = ep.params)
                )
            }
            else -> null
        }
    }

    // ── Item dispatch ──────────────────────────────────────────────────────────

    private fun parseItem(item: HomeCarouselItem): HomeItem? =
        item.musicResponsiveListItemRenderer?.let { parseSong(it) }
            ?: item.musicTwoRowItemRenderer?.let { parseCard(it) }

    // ── Song (musicResponsiveListItemRenderer) ────────────────────────────────

    private fun parseSong(item: MusicResponsiveListItemRenderer): HomeItem.Song? {
        val cols = item.flexColumns ?: return null

        // Column 0: title + watch endpoint
        val col0Runs = cols.getOrNull(0)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?: return null
        val title = col0Runs.firstOrNull()?.text ?: return null

        // Prefer playlistItemData.videoId; fall back to overlay / col0 nav endpoint
        val videoId = item.playlistItemData?.videoId
            ?: overlayWatchEndpoint(item)?.videoId
            ?: col0Runs.firstOrNull()?.navigationEndpoint?.watchEndpoint?.videoId
            ?: return null

        // Best endpoint for playlistId + musicVideoType
        val playEndpoint = col0Runs.firstOrNull()?.navigationEndpoint?.watchEndpoint
            ?: overlayWatchEndpoint(item)

        // Column 1: artist(s) + play count
        val col1Runs = cols.getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?: emptyList()

        val artistRun = col1Runs.firstOrNull { it.navigationEndpoint?.browseEndpoint != null }
        val artistName = artistRun?.text ?: ""
        val artistId = artistRun?.navigationEndpoint?.browseEndpoint?.browseId

        // Play count is the non-separator, non-linked run in col 1
        val plays = col1Runs.firstOrNull { it.text != " • " && it.navigationEndpoint == null }?.text

        // Column 2: album
        val col2Runs = cols.getOrNull(2)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?: emptyList()
        val albumRun = col2Runs.firstOrNull { it.navigationEndpoint?.browseEndpoint != null }
        val albumName = albumRun?.text
        val albumId = albumRun?.navigationEndpoint?.browseEndpoint?.browseId

        return HomeItem.Song(
            videoId = videoId,
            playlistId = playEndpoint?.playlistId,
            title = title,
            artistName = artistName,
            artistId = artistId,
            albumName = albumName,
            albumId = albumId,
            plays = plays,
            thumbnailUrl = bestThumbUrl(
                (item.thumbnail?.musicThumbnailRenderer?.thumbnailImage
                    ?: item.thumbnail?.musicThumbnailRenderer?.thumbnail)?.thumbnails
            ),
            musicVideoType = playEndpoint
                ?.watchEndpointMusicSupportedConfigs
                ?.watchEndpointMusicConfig
                ?.musicVideoType
        )
    }

    private fun overlayWatchEndpoint(item: MusicResponsiveListItemRenderer): WatchEndpoint? =
        item.overlay
            ?.musicItemThumbnailOverlayRenderer
            ?.content
            ?.musicPlayButtonRenderer
            ?.playNavigationEndpoint
            ?.watchEndpoint

    // ── Card (musicTwoRowItemRenderer) ────────────────────────────────────────

    internal fun parseCard(item: MusicTwoRowItemRenderer): HomeItem.Card? {
        val title = item.title?.runs?.firstOrNull()?.text ?: return null

        val subtitle = item.subtitle?.runs
            ?.joinToString("") { it.text }
            ?.ifEmpty { null }

        val thumbnail = bestThumbUrl(
            (item.thumbnailRenderer?.musicThumbnailRenderer?.thumbnailImage
                ?: item.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail)?.thumbnails
        )

        val navEndpoint = item.navigationEndpoint
            ?: item.title?.runs?.firstOrNull()?.navigationEndpoint

        // Music video cards navigate via watchEndpoint
        val watchEp = navEndpoint?.watchEndpoint
        if (watchEp?.videoId != null) {
            return HomeItem.Card(
                id = null,
                videoId = watchEp.videoId,
                title = title,
                subtitle = subtitle,
                thumbnailUrl = thumbnail,
                pageType = null,
                musicVideoType = watchEp.watchEndpointMusicSupportedConfigs
                    ?.watchEndpointMusicConfig?.musicVideoType
            )
        }

        // All other cards (playlists, albums, artists) navigate via browseEndpoint
        val browseEp = navEndpoint?.browseEndpoint
            ?: return null
        val id = browseEp.browseId ?: return null
        val pageType = browseEp.browseEndpointContextSupportedConfigs
            ?.browseEndpointContextMusicConfig?.pageType

        return HomeItem.Card(
            id = id,
            videoId = null,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnail,
            pageType = pageType,
            musicVideoType = null
        )
    }

    // ── Thumbnail helper ───────────────────────────────────────────────────────

    private fun bestThumbUrl(thumbnails: List<Thumbnail>?): String? {
        val highest = thumbnails?.maxByOrNull { it.width }?.url
        return upscaleThumbnail(highest, size = 544)
    }
}
