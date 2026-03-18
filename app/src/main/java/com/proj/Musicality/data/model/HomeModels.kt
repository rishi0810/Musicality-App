package com.proj.Musicality.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The full home feed, possibly paginated.
 * Call HomeParser.extractFeed for the first page, then HomeParser.extractContinuation
 * with [continuation] to append more sections.
 */
@Immutable
@Serializable
data class HomeFeed(
    val sections: List<HomeSection>,
    val continuation: String?
)

/** Layout hint for personalized home sections. API sections use [DEFAULT]. */
@Serializable
enum class SectionLayout {
    /** UI decides via title pattern matching (API sections). */
    DEFAULT,
    /** Full-width banner card (Continue Playing, 1 item). */
    HERO_CARD,
    /** Hero + 3 compact cards below (mature Continue Playing). */
    HERO_WITH_TOP_PICKS,
    /** Wider album cards in LazyRow. */
    ALBUM_CAROUSEL,
    /** Force 4-item stacked column layout. */
    STACKED_SONGS
}

/**
 * A labelled carousel shelf on the home screen (e.g. "Quick picks",
 * "Trending community playlists", "Today's Global Hits").
 */
@Immutable
@Serializable
data class HomeSection(
    val title: String,
    val items: List<HomeItem>,
    /** "Play all" or "See more" navigation hint from the shelf header. */
    val moreEndpoint: HomeMoreEndpoint?,
    val layoutHint: SectionLayout = SectionLayout.DEFAULT
)

/**
 * Describes where the "Play all / See more" button should navigate.
 * Exactly one of [watchEndpoint] or [browseEndpoint] will be non-null.
 */
@Immutable
@Serializable
data class HomeMoreEndpoint(
    val watchEndpoint: HomeWatchEndpoint?,
    val browseEndpoint: HomeBrowseEndpoint?
)

@Immutable
@Serializable
data class HomeWatchEndpoint(
    val videoId: String,
    val playlistId: String?,
    val params: String?
)

@Immutable
@Serializable
data class HomeBrowseEndpoint(
    val browseId: String,
    val params: String?
)

/** Discriminated union of every item type that can appear in a home shelf. */
@Serializable
sealed class HomeItem {

    /**
     * A playable track row (musicResponsiveListItemRenderer).
     * Appears in "Quick picks" and similar shelves.
     * [musicVideoType] will typically be "MUSIC_VIDEO_TYPE_ATV" for songs.
     */
    @Immutable
    @Serializable
    data class Song(
        val videoId: String,
        /** Auto-generated radio playlist seeded from this track. */
        val playlistId: String?,
        val title: String,
        val artistName: String,
        val artistId: String?,
        val albumName: String?,
        val albumId: String?,
        /** Play count text as returned by the API, e.g. "3.9M plays". */
        val plays: String?,
        val thumbnailUrl: String?,
        val musicVideoType: String?
    ) : HomeItem()

    /**
     * A square card (musicTwoRowItemRenderer) representing a playlist, album,
     * artist, or music video. The [pageType] discriminates browse targets:
     *  - "MUSIC_PAGE_TYPE_PLAYLIST"
     *  - "MUSIC_PAGE_TYPE_ALBUM"
     *  - "MUSIC_PAGE_TYPE_ARTIST"
     *  - "MUSIC_PAGE_TYPE_USER_CHANNEL"
     *  - null when [videoId] is set (music video with watchEndpoint)
     */
    @Immutable
    @Serializable
    data class Card(
        /** browseId for browse targets, or null for watch-endpoint music videos. */
        val id: String?,
        /** videoId for music videos navigated via watchEndpoint, otherwise null. */
        val videoId: String?,
        val title: String,
        /** Subtitle line, e.g. "Single • Artist" for albums or "Artist • 10M views" for videos. */
        val subtitle: String?,
        val thumbnailUrl: String?,
        val pageType: String?,
        /** "MUSIC_VIDEO_TYPE_OMV" etc. when the card navigates to a watch endpoint. */
        val musicVideoType: String?
    ) : HomeItem()

    /**
     * A quick-navigation chip (musicNavigationButtonRenderer).
     * Appears in the explore top-nav grid ("New releases", "Charts", …)
     * and in the "Moods & genres" carousel.
     */
    @Immutable
    @Serializable
    data class NavButton(
        val label: String,
        /** browseId to open when tapped. */
        val browseId: String,
        /** Optional params required for moods/genres category pages. */
        val params: String?,
        /** Icon identifier for top-nav buttons, e.g. "MUSIC_NEW_RELEASE". */
        val icon: String?,
        /** ARGB accent stripe color for mood/genre chips. */
        val color: Long?
    ) : HomeItem()

    /**
     * A podcast episode row (musicMultiRowListItemRenderer).
     * Appears in the "Popular episodes" carousel on explore.
     */
    @Immutable
    @Serializable
    data class PodcastEpisode(
        val videoId: String,
        val title: String,
        /** Show / podcast name. */
        val showName: String?,
        /** Browse ID for the show detail page. */
        val showId: String?,
        /** Human-readable publish time, e.g. "2d ago". */
        val timeAgo: String?,
        val description: String?,
        /** Formatted duration, e.g. "3 hr 48 min". */
        val duration: String?,
        val thumbnailUrl: String?,
        val playbackProgressPercent: Float?
    ) : HomeItem()
}
