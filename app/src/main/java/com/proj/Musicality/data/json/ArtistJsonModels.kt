package com.proj.Musicality.data.json

import kotlinx.serialization.Serializable

@Serializable
data class ArtistBrowseResponse(
    val contents: ArtistContents? = null,
    val microformat: Microformat? = null,
    val header: ArtistHeader? = null
)

@Serializable
data class ArtistHeader(val musicImmersiveHeaderRenderer: MusicImmersiveHeaderRenderer? = null)

@Serializable
data class MusicImmersiveHeaderRenderer(val thumbnail: ThumbnailRenderer? = null)

@Serializable
data class ArtistContents(val singleColumnBrowseResultsRenderer: SingleColumnBrowseResultsRenderer? = null)

@Serializable
data class SingleColumnBrowseResultsRenderer(val tabs: List<ArtistTabRenderer>? = null)

@Serializable
data class ArtistTabRenderer(val tabRenderer: ArtistTabContent? = null)

@Serializable
data class ArtistTabContent(val content: ArtistSectionListWrapper? = null)

@Serializable
data class ArtistSectionListWrapper(val sectionListRenderer: ArtistSectionListContent? = null)

@Serializable
data class ArtistSectionListContent(val contents: List<ArtistSectionItem>? = null)

@Serializable
data class ArtistSectionItem(
    val musicShelfRenderer: ArtistMusicShelfRenderer? = null,
    val musicCarouselShelfRenderer: MusicCarouselShelfRenderer? = null
)

@Serializable
data class ArtistMusicShelfRenderer(
    val title: Runs? = null,
    val contents: List<CarouselItem>? = null
)

@Serializable
data class MusicCarouselShelfRenderer(
    val header: MusicCarouselHeader? = null,
    val contents: List<CarouselItem>? = null
)

@Serializable
data class CarouselItem(
    val musicTwoRowItemRenderer: MusicTwoRowItemRenderer? = null,
    val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null
)

@Serializable
data class MusicCarouselHeader(val musicCarouselShelfBasicHeaderRenderer: BasicHeader? = null)

@Serializable
data class BasicHeader(
    val title: Runs? = null,
    val moreContentButton: MoreContentButton? = null
)

@Serializable
data class MusicTwoRowItemRenderer(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val thumbnailRenderer: ThumbnailRenderer? = null,
    val navigationEndpoint: NavigationEndpoint? = null
)

@Serializable
data class Microformat(val microformatDataRenderer: MicroformatDataRenderer? = null)

@Serializable
data class MicroformatDataRenderer(
    val title: String? = null,
    val description: String? = null,
    val thumbnail: Thumbnails? = null
)
