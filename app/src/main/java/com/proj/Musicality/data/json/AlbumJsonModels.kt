package com.proj.Musicality.data.json

import kotlinx.serialization.Serializable

@Serializable
data class AlbumBrowseResponse(val contents: AlbumContents? = null)

@Serializable
data class AlbumContents(val twoColumnBrowseResultsRenderer: TwoColumnRenderer? = null)

@Serializable
data class TwoColumnRenderer(
    val tabs: List<AlbumTabRenderer>? = null,
    val secondaryContents: SecondaryContents? = null
)

@Serializable
data class AlbumTabRenderer(val tabRenderer: AlbumTabContent? = null)

@Serializable
data class AlbumTabContent(val content: AlbumSectionListWrapper? = null)

@Serializable
data class AlbumSectionListWrapper(val sectionListRenderer: AlbumSectionList? = null)

@Serializable
data class AlbumSectionList(val contents: List<AlbumSectionItem>? = null)

@Serializable
data class AlbumSectionItem(
    val musicResponsiveHeaderRenderer: AlbumHeaderRenderer? = null,
    val musicShelfRenderer: AlbumMusicShelfRenderer? = null
)

@Serializable
data class AlbumHeaderRenderer(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val secondSubtitle: Runs? = null,
    val straplineTextOne: Runs? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val description: DescriptionShelf? = null
)

@Serializable
data class DescriptionShelf(val musicDescriptionShelfRenderer: MusicDescriptionShelfRenderer? = null)

@Serializable
data class MusicDescriptionShelfRenderer(val description: Runs? = null)

@Serializable
data class SecondaryContents(val sectionListRenderer: AlbumSecondarySectionList? = null)

@Serializable
data class AlbumSecondarySectionList(val contents: List<AlbumSecondaryItem>? = null)

@Serializable
data class AlbumSecondaryItem(val musicShelfRenderer: AlbumMusicShelfRenderer? = null)

@Serializable
data class AlbumMusicShelfRenderer(val contents: List<AlbumTrackWrapper>? = null)

@Serializable
data class AlbumTrackWrapper(val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null)
