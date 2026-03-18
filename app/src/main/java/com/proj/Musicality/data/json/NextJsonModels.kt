package com.proj.Musicality.data.json

import kotlinx.serialization.Serializable

@Serializable
data class NextResponse(
    val contents: NextContents? = null,
    val currentVideoEndpoint: NavigationEndpoint? = null
)

@Serializable
data class NextContents(
    val singleColumnMusicWatchNextResultsRenderer: SingleColumnMusicWatchNextResultsRenderer? = null
)

@Serializable
data class SingleColumnMusicWatchNextResultsRenderer(
    val tabbedRenderer: MusicWatchNextTabbedRenderer? = null
)

@Serializable
data class MusicWatchNextTabbedRenderer(
    val watchNextTabbedResultsRenderer: WatchNextTabbedResultsRenderer? = null
)

@Serializable
data class WatchNextTabbedResultsRenderer(
    val tabs: List<WatchNextTab>? = null
)

@Serializable
data class WatchNextTab(
    val tabRenderer: WatchNextTabRenderer? = null
)

@Serializable
data class WatchNextTabRenderer(
    val title: String? = null,
    val content: WatchNextTabContent? = null
)

@Serializable
data class WatchNextTabContent(
    val musicQueueRenderer: MusicQueueRenderer? = null
)

@Serializable
data class MusicQueueRenderer(
    val content: MusicQueueContent? = null
)

@Serializable
data class MusicQueueContent(
    val playlistPanelRenderer: PlaylistPanelRenderer? = null
)

@Serializable
data class PlaylistPanelRenderer(
    val contents: List<PlaylistPanelContentItem>? = null
)

@Serializable
data class PlaylistPanelContentItem(
    val playlistPanelVideoRenderer: PlaylistPanelVideoRenderer? = null,
    val playlistPanelVideoWrapperRenderer: PlaylistPanelVideoWrapperRenderer? = null
)

@Serializable
data class PlaylistPanelVideoWrapperRenderer(
    val primaryRenderer: PlaylistPanelPrimaryRenderer? = null
)

@Serializable
data class PlaylistPanelPrimaryRenderer(
    val playlistPanelVideoRenderer: PlaylistPanelVideoRenderer? = null
)

@Serializable
data class PlaylistPanelVideoRenderer(
    val title: Runs? = null,
    val shortBylineText: Runs? = null,
    val longBylineText: Runs? = null,
    val thumbnail: Thumbnails? = null,
    val lengthText: Runs? = null,
    val navigationEndpoint: NavigationEndpoint? = null,
    val videoId: String? = null,
    val selected: Boolean? = null
)

