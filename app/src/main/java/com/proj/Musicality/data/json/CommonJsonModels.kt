package com.proj.Musicality.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Shared text types
@Serializable
data class Runs(val runs: List<Run>? = null)

@Serializable
data class Run(
    val text: String,
    val navigationEndpoint: NavigationEndpoint? = null
)

// Navigation endpoints
@Serializable
data class NavigationEndpoint(
    val watchEndpoint: WatchEndpoint? = null,
    val browseEndpoint: BrowseEndpoint? = null,
    val searchEndpoint: SearchEndpoint? = null,
    val watchPlaylistEndpoint: WatchPlaylistEndpoint? = null
)

@Serializable
data class WatchEndpoint(
    val videoId: String? = null,
    val playlistId: String? = null,
    val params: String? = null,
    val watchEndpointMusicSupportedConfigs: WatchConfig? = null
)

@Serializable
data class WatchConfig(val watchEndpointMusicConfig: WatchMusicConfig? = null)

@Serializable
data class WatchMusicConfig(val musicVideoType: String? = null)

@Serializable
data class BrowseEndpoint(
    val browseId: String? = null,
    val params: String? = null,
    val browseEndpointContextSupportedConfigs: BrowseConfig? = null
)

@Serializable
data class BrowseConfig(val browseEndpointContextMusicConfig: MusicConfig? = null)

@Serializable
data class MusicConfig(val pageType: String? = null)

@Serializable
data class SearchEndpoint(val query: String? = null)

@Serializable
data class WatchPlaylistEndpoint(val playlistId: String? = null)

// Thumbnail types
@Serializable
data class ThumbnailRenderer(val musicThumbnailRenderer: ThumbnailData? = null)

@Serializable
data class ThumbnailData(
    val thumbnail: Thumbnails? = null,
    val thumbnailImage: Thumbnails? = null
)

@Serializable
data class Thumbnails(val thumbnails: List<Thumbnail>? = null)

@Serializable
data class Thumbnail(val url: String, val width: Int = 0, val height: Int = 0)

// Flex column types
@Serializable
data class FlexColumn(val musicResponsiveListItemFlexColumnRenderer: FlexColumnRenderer? = null)

@Serializable
data class FlexColumnRenderer(val text: Runs? = null)

// Fixed column types
@Serializable
data class FixedColumn(val musicResponsiveListItemFixedColumnRenderer: FixedColumnRenderer? = null)

@Serializable
data class FixedColumnRenderer(val text: Runs? = null)

// Overlay types
@Serializable
data class OverlayRenderer(val musicItemThumbnailOverlayRenderer: MusicItemThumbnailOverlay? = null)

@Serializable
data class MusicItemThumbnailOverlay(val content: OverlayContent? = null)

@Serializable
data class OverlayContent(val musicPlayButtonRenderer: MusicPlayButton? = null)

@Serializable
data class MusicPlayButton(val playNavigationEndpoint: NavigationEndpoint? = null)

// Menu types
@Serializable
data class MenuRenderer(val menuRenderer: MenuItems? = null)

@Serializable
data class MenuItems(val items: List<MenuItem>? = null)

@Serializable
data class MenuItem(val menuNavigationItemRenderer: MenuNavigationItem? = null)

@Serializable
data class MenuNavigationItem(val navigationEndpoint: NavigationEndpoint? = null)

// Responsive list item (shared across suggestions, search, artist, album, playlist)
@Serializable
data class MusicResponsiveListItemRenderer(
    val flexColumns: List<FlexColumn>? = null,
    val fixedColumns: List<FixedColumn>? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val navigationEndpoint: NavigationEndpoint? = null,
    val overlay: OverlayRenderer? = null,
    val menu: MenuRenderer? = null,
    val playlistItemData: PlaylistItemData? = null,
    val index: Runs? = null
)

@Serializable
data class PlaylistItemData(
    val videoId: String? = null,
    val playlistSetVideoId: String? = null
)

// Visitor ID response
@Serializable
data class VisitorIdResponse(val responseContext: ResponseContext? = null)

// IP geolocation response (https://free.freeipapi.com/api/json)
@Serializable
data class IpLocationResponse(val countryCode: String? = null)

@Serializable
data class ResponseContext(
    val visitorData: String? = null,
    val rolloutToken: String? = null
)
