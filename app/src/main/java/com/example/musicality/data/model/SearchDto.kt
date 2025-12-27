package com.example.musicality.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Raw YouTube Music API response
 */
@JsonClass(generateAdapter = true)
data class YoutubeMusicResponseDto(
    @Json(name = "contents")
    val contents: List<SearchSectionDto>?
)

@JsonClass(generateAdapter = true)
data class SearchSectionDto(
    @Json(name = "searchSuggestionsSectionRenderer")
    val searchSuggestionsSectionRenderer: SearchSuggestionsSectionRendererDto?
)

@JsonClass(generateAdapter = true)
data class SearchSuggestionsSectionRendererDto(
    @Json(name = "contents")
    val contents: List<SearchItemDto>?
)

@JsonClass(generateAdapter = true)
data class SearchItemDto(
    @Json(name = "searchSuggestionRenderer")
    val searchSuggestionRenderer: SearchSuggestionRendererDto?,
    
    @Json(name = "musicResponsiveListItemRenderer")
    val musicResponsiveListItemRenderer: MusicResponsiveListItemRendererDto?
)

@JsonClass(generateAdapter = true)
data class SearchSuggestionRendererDto(
    @Json(name = "navigationEndpoint")
    val navigationEndpoint: NavigationEndpointDto?
)

@JsonClass(generateAdapter = true)
data class NavigationEndpointDto(
    @Json(name = "searchEndpoint")
    val searchEndpoint: SearchEndpointDto?,
    
    @Json(name = "browseEndpoint")
    val browseEndpoint: BrowseEndpointDto?,
    
    @Json(name = "watchEndpoint")
    val watchEndpoint: WatchEndpointDto?
)

@JsonClass(generateAdapter = true)
data class SearchEndpointDto(
    @Json(name = "query")
    val query: String?
)

@JsonClass(generateAdapter = true)
data class BrowseEndpointDto(
    @Json(name = "browseId")
    val browseId: String?,
    
    @Json(name = "browseEndpointContextSupportedConfigs")
    val browseEndpointContextSupportedConfigs: BrowseEndpointContextSupportedConfigsDto?
)

@JsonClass(generateAdapter = true)
data class BrowseEndpointContextSupportedConfigsDto(
    @Json(name = "browseEndpointContextMusicConfig")
    val browseEndpointContextMusicConfig: BrowseEndpointContextMusicConfigDto?
)

@JsonClass(generateAdapter = true)
data class BrowseEndpointContextMusicConfigDto(
    @Json(name = "pageType")
    val pageType: String?
)

@JsonClass(generateAdapter = true)
data class WatchEndpointDto(
    @Json(name = "videoId")
    val videoId: String?
)

@JsonClass(generateAdapter = true)
data class MusicResponsiveListItemRendererDto(
    @Json(name = "flexColumns")
    val flexColumns: List<FlexColumnDto>?,
    
    @Json(name = "thumbnail")
    val thumbnail: ThumbnailContainerDto?,
    
    @Json(name = "badges")
    val badges: List<BadgeDto>?,
    
    @Json(name = "navigationEndpoint")
    val navigationEndpoint: NavigationEndpointDto?
)

@JsonClass(generateAdapter = true)
data class FlexColumnDto(
    @Json(name = "musicResponsiveListItemFlexColumnRenderer")
    val musicResponsiveListItemFlexColumnRenderer: MusicResponsiveListItemFlexColumnRendererDto?
)

@JsonClass(generateAdapter = true)
data class MusicResponsiveListItemFlexColumnRendererDto(
    @Json(name = "text")
    val text: TextDto?
)

@JsonClass(generateAdapter = true)
data class TextDto(
    @Json(name = "runs")
    val runs: List<RunDto>?
)

@JsonClass(generateAdapter = true)
data class RunDto(
    @Json(name = "text")
    val text: String?,
    
    @Json(name = "navigationEndpoint")
    val navigationEndpoint: NavigationEndpointDto?
)

@JsonClass(generateAdapter = true)
data class ThumbnailContainerDto(
    @Json(name = "musicThumbnailRenderer")
    val musicThumbnailRenderer: MusicThumbnailRendererDto?
)

@JsonClass(generateAdapter = true)
data class MusicThumbnailRendererDto(
    @Json(name = "thumbnail")
    val thumbnail: ThumbnailsContainerDto?
)

@JsonClass(generateAdapter = true)
data class ThumbnailsContainerDto(
    @Json(name = "thumbnails")
    val thumbnails: List<ThumbnailDto>?
)

@JsonClass(generateAdapter = true)
data class ThumbnailDto(
    @Json(name = "url")
    val url: String?
)

@JsonClass(generateAdapter = true)
data class BadgeDto(
    @Json(name = "musicInlineBadgeRenderer")
    val musicInlineBadgeRenderer: MusicInlineBadgeRendererDto?
)

@JsonClass(generateAdapter = true)
data class MusicInlineBadgeRendererDto(
    @Json(name = "icon")
    val icon: IconDto?
)

@JsonClass(generateAdapter = true)
data class IconDto(
    @Json(name = "iconType")
    val iconType: String?
)
