package com.proj.Musicality.data.parser

import com.proj.Musicality.data.json.*
import com.proj.Musicality.data.model.*
import com.proj.Musicality.util.upscaleThumbnail

object ExploreParser {

    /**
     * Parse the explore feed (browseId = "FEmusic_explore").
     *
     * Returns a [HomeFeed] (no continuation – explore is a single page).
     * Sections produced:
     *  - A section with [HomeItem.NavButton] items for the top-nav grid
     *    ("New releases", "Charts", "Moods & genres", "Podcasts")
     *  - "New albums & singles"  → [HomeItem.Card] (MUSIC_PAGE_TYPE_ALBUM)
     *  - "Moods & genres"        → [HomeItem.NavButton] (genre chips)
     *  - "Popular episodes"      → [HomeItem.PodcastEpisode]
     *  - "Trending"              → [HomeItem.Song] (musicResponsiveListItemRenderer)
     *  - "New music videos"      → [HomeItem.Card] (watch or browse endpoint)
     */
    fun extractFeed(jsonResponse: String): HomeFeed {
        val response = JsonParser.instance.decodeFromString<HomeFeedResponse>(jsonResponse)
        val sectionItems = response.contents
            ?.singleColumnBrowseResultsRenderer
            ?.tabs?.firstOrNull()
            ?.tabRenderer?.content
            ?.sectionListRenderer
            ?.contents
            ?: emptyList()

        return HomeFeed(
            sections = parseSections(sectionItems),
            continuation = null
        )
    }

    // ── Section dispatch ───────────────────────────────────────────────────────

    private fun parseSections(contents: List<HomeSectionItem>): List<HomeSection> =
        contents.mapNotNull { sectionItem ->
            sectionItem.gridRenderer?.let { return@mapNotNull parseGridSection(it) }
            sectionItem.musicCarouselShelfRenderer?.let { return@mapNotNull parseCarouselSection(it) }
            null
        }

    /** Top-nav quick-link grid ("New releases", "Charts", …). Rendered title-less. */
    private fun parseGridSection(grid: GridRenderer): HomeSection? {
        val items = grid.items
            ?.mapNotNull { it.musicNavigationButtonRenderer?.let(::parseNavButton) }
            ?: emptyList()
        if (items.isEmpty()) return null
        return HomeSection(title = "", items = items, moreEndpoint = null)
    }

    private fun parseCarouselSection(shelf: HomeCarouselShelf): HomeSection? {
        val header = shelf.header?.musicCarouselShelfBasicHeaderRenderer
        val title = header?.title?.runs?.firstOrNull()?.text ?: return null

        val moreEndpoint = parseMoreEndpoint(
            header.moreContentButton?.buttonRenderer?.navigationEndpoint
        )

        val items = shelf.contents?.mapNotNull(::parseItem) ?: emptyList()
        if (items.isEmpty()) return null

        return HomeSection(title, items, moreEndpoint)
    }

    // ── Item dispatch ──────────────────────────────────────────────────────────

    private fun parseItem(item: HomeCarouselItem): HomeItem? =
        item.musicResponsiveListItemRenderer?.let { parseSong(it) }
            ?: item.musicTwoRowItemRenderer?.let { HomeParser.parseCard(it) }
            ?: item.musicNavigationButtonRenderer?.let { parseNavButton(it) }
            ?: item.musicMultiRowListItemRenderer?.let { parsePodcastEpisode(it) }

    // Trending shelf uses musicResponsiveListItemRenderer with OMV music video type.
    private fun parseSong(item: MusicResponsiveListItemRenderer): HomeItem.Song? {
        val cols = item.flexColumns ?: return null
        val col0Runs = cols.getOrNull(0)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?: return null
        val title = col0Runs.firstOrNull()?.text ?: return null

        val videoId = item.playlistItemData?.videoId
            ?: overlayWatchEndpoint(item)?.videoId
            ?: col0Runs.firstOrNull()?.navigationEndpoint?.watchEndpoint?.videoId
            ?: return null

        val playEndpoint = col0Runs.firstOrNull()?.navigationEndpoint?.watchEndpoint
            ?: overlayWatchEndpoint(item)

        val col1Runs = cols.getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?: emptyList()
        val artistRun = col1Runs.primaryArtistRun()
        val plays = col1Runs.firstOrNull { it.text != " • " && it.navigationEndpoint == null }?.text

        val col2Runs = cols.getOrNull(2)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?: emptyList()
        val albumRun = col2Runs.firstOrNull { it.navigationEndpoint?.browseEndpoint != null }

        return HomeItem.Song(
            videoId = videoId,
            playlistId = playEndpoint?.playlistId,
            title = title,
            artistName = artistRun?.text?.primaryArtistName().orEmpty(),
            artistId = artistRun?.artistBrowseIdOrNull(),
            albumName = albumRun?.text,
            albumId = albumRun?.navigationEndpoint?.browseEndpoint?.browseId,
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

    // ── NavButton (musicNavigationButtonRenderer) ──────────────────────────────

    private fun parseNavButton(btn: MusicNavigationButtonRenderer): HomeItem.NavButton? {
        val label = btn.buttonText?.runs?.firstOrNull()?.text ?: return null
        val browseId = btn.clickCommand?.browseEndpoint?.browseId ?: return null
        val params = btn.clickCommand.browseEndpoint?.params
        val icon = btn.iconStyle?.icon?.iconType
        val color = btn.solid?.leftStripeColor
        return HomeItem.NavButton(label, browseId, params, icon, color)
    }

    // ── Podcast episode (musicMultiRowListItemRenderer) ───────────────────────

    private fun parsePodcastEpisode(item: MusicMultiRowListItemRenderer): HomeItem.PodcastEpisode? {
        val title = item.title?.runs?.firstOrNull()?.text ?: return null

        // Primary play target: onTap (direct click) or overlay play button
        val videoId = item.onTap?.watchEndpoint?.videoId
            ?: item.overlay
                ?.musicItemThumbnailOverlayRenderer
                ?.content
                ?.musicPlayButtonRenderer
                ?.playNavigationEndpoint
                ?.watchEndpoint
                ?.videoId
            ?: return null

        val showRun = item.secondTitle?.runs?.firstOrNull()
        val showName = showRun?.text
        val showId = showRun?.navigationEndpoint?.browseEndpoint?.browseId

        val timeAgo = item.subtitle?.runs?.firstOrNull()?.text

        val description = item.description?.runs?.firstOrNull()?.text

        // durationText.runs is [ " • ", "<duration>" ] – take index 1
        val duration = item.playbackProgress
            ?.musicPlaybackProgressRenderer
            ?.durationText
            ?.runs
            ?.getOrNull(1)
            ?.text

        val progressPercent = item.playbackProgress
            ?.musicPlaybackProgressRenderer
            ?.playbackProgressPercentage

        val thumbnail = bestThumbUrl(
            (item.thumbnail?.musicThumbnailRenderer?.thumbnailImage
                ?: item.thumbnail?.musicThumbnailRenderer?.thumbnail)?.thumbnails
        )

        return HomeItem.PodcastEpisode(
            videoId = videoId,
            title = title,
            showName = showName,
            showId = showId,
            timeAgo = timeAgo,
            description = description,
            duration = duration,
            thumbnailUrl = thumbnail,
            playbackProgressPercent = progressPercent
        )
    }

    // ── More-endpoint helper ───────────────────────────────────────────────────

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

    // ── Thumbnail helper ───────────────────────────────────────────────────────

    private fun bestThumbUrl(thumbnails: List<Thumbnail>?): String? {
        val highest = thumbnails?.maxByOrNull { it.width }?.url
        return upscaleThumbnail(highest, size = 544)
    }
}
