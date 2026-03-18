package com.proj.Musicality.data.json

import kotlinx.serialization.Serializable

// ── Top-level home feed response ──────────────────────────────────────────────

@Serializable
data class HomeFeedResponse(val contents: HomeContents? = null)

@Serializable
data class HomeContents(
    val singleColumnBrowseResultsRenderer: HomeSingleColumnRenderer? = null
)

@Serializable
data class HomeSingleColumnRenderer(val tabs: List<HomeTabRenderer>? = null)

@Serializable
data class HomeTabRenderer(val tabRenderer: HomeTabContent? = null)

@Serializable
data class HomeTabContent(val content: HomeSectionListWrapper? = null)

@Serializable
data class HomeSectionListWrapper(val sectionListRenderer: HomeSectionList? = null)

@Serializable
data class HomeSectionList(
    val contents: List<HomeSectionItem>? = null,
    val continuations: List<HomeContinuation>? = null
)

// ── Continuation response ─────────────────────────────────────────────────────

@Serializable
data class HomeContinuationResponse(
    val continuationContents: HomeContinuationContents? = null
)

@Serializable
data class HomeContinuationContents(
    val sectionListContinuation: HomeSectionListContinuation? = null
)

@Serializable
data class HomeSectionListContinuation(
    val contents: List<HomeSectionItem>? = null,
    val continuations: List<HomeContinuation>? = null
)

// ── Section items ─────────────────────────────────────────────────────────────

@Serializable
data class HomeSectionItem(
    // Labelled carousel shelf (home + explore)
    val musicCarouselShelfRenderer: HomeCarouselShelf? = null,
    // Top-nav grid of 4 quick-link buttons (explore only)
    val gridRenderer: GridRenderer? = null
    // musicTastebuilderShelfRenderer and others are intentionally ignored
)

@Serializable
data class HomeCarouselShelf(
    val header: HomeCarouselHeader? = null,
    val contents: List<HomeCarouselItem>? = null,
    val shelfId: String? = null,
    val itemSize: String? = null
)

@Serializable
data class HomeCarouselHeader(
    val musicCarouselShelfBasicHeaderRenderer: BasicHeader? = null
)

// MoreContentButton / buttonRenderer – also added to BasicHeader in ArtistJsonModels

@Serializable
data class MoreContentButton(val buttonRenderer: MoreButtonRenderer? = null)

@Serializable
data class MoreButtonRenderer(
    val text: Runs? = null,
    val navigationEndpoint: NavigationEndpoint? = null
)

// ── Carousel item wrapper ─────────────────────────────────────────────────────

@Serializable
data class HomeCarouselItem(
    // Songs / track-list rows (Quick picks, Trending)
    val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
    // Playlists, albums, music videos (grid cards)
    val musicTwoRowItemRenderer: MusicTwoRowItemRenderer? = null,
    // Mood/genre chips + explore top-nav buttons
    val musicNavigationButtonRenderer: MusicNavigationButtonRenderer? = null,
    // Podcast episodes
    val musicMultiRowListItemRenderer: MusicMultiRowListItemRenderer? = null
)

// ── Grid renderer (explore quick-nav) ────────────────────────────────────────

@Serializable
data class GridRenderer(val items: List<GridItem>? = null)

@Serializable
data class GridItem(val musicNavigationButtonRenderer: MusicNavigationButtonRenderer? = null)

// ── Navigation button renderer ────────────────────────────────────────────────

@Serializable
data class MusicNavigationButtonRenderer(
    val buttonText: Runs? = null,
    val clickCommand: NavButtonClickCommand? = null,
    /** Accent stripe color (ARGB long). Present on mood/genre chips. */
    val solid: NavButtonSolid? = null,
    /** Icon identifier. Present on explore top-nav buttons. */
    val iconStyle: NavButtonIconStyle? = null
)

@Serializable
data class NavButtonClickCommand(val browseEndpoint: BrowseEndpoint? = null)

@Serializable
data class NavButtonSolid(val leftStripeColor: Long? = null)

@Serializable
data class NavButtonIconStyle(val icon: NavButtonIcon? = null)

@Serializable
data class NavButtonIcon(val iconType: String? = null)

// ── Multi-row list item (podcast episodes) ────────────────────────────────────

@Serializable
data class MusicMultiRowListItemRenderer(
    val title: Runs? = null,
    /** Time-ago label, e.g. "2d ago". */
    val subtitle: Runs? = null,
    /** Show name with its own browse endpoint. */
    val secondTitle: Runs? = null,
    val description: Runs? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val overlay: OverlayRenderer? = null,
    /** Primary tap target – contains the watchEndpoint for the episode. */
    val onTap: NavigationEndpoint? = null,
    val playbackProgress: PlaybackProgress? = null
)

@Serializable
data class PlaybackProgress(
    val musicPlaybackProgressRenderer: MusicPlaybackProgressRenderer? = null
)

@Serializable
data class MusicPlaybackProgressRenderer(
    /** Formatted duration string in runs[1], e.g. "3 hr 48 min". */
    val durationText: Runs? = null,
    val playbackProgressPercentage: Float? = null
)

// ── Continuation token ────────────────────────────────────────────────────────

@Serializable
data class HomeContinuation(
    val nextContinuationData: NextContinuationData? = null
)

@Serializable
data class NextContinuationData(val continuation: String? = null)
