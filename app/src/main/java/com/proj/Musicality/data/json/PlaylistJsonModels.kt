package com.proj.Musicality.data.json

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistBrowseResponse(val contents: PlaylistContents? = null)

@Serializable
data class PlaylistContents(val twoColumnBrowseResultsRenderer: PlaylistTwoColumn? = null)

@Serializable
data class PlaylistTwoColumn(
    val tabs: List<PlaylistTab>? = null,
    val secondaryContents: PlaylistSecondary? = null
)

@Serializable
data class PlaylistTab(val tabRenderer: PlaylistTabContent? = null)

@Serializable
data class PlaylistTabContent(val content: PlaylistSectionListWrapper? = null)

@Serializable
data class PlaylistSectionListWrapper(val sectionListRenderer: PlaylistSectionList? = null)

@Serializable
data class PlaylistSectionList(val contents: List<PlaylistSectionItem>? = null)

@Serializable
data class PlaylistSectionItem(
    val musicResponsiveHeaderRenderer: PlaylistHeaderRenderer? = null,
    val musicPlaylistShelfRenderer: PlaylistShelfRenderer? = null
)

@Serializable
data class PlaylistHeaderRenderer(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val secondSubtitle: Runs? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val description: PlaylistDescShelf? = null,
    val facepile: PlaylistFacepile? = null
)

@Serializable
data class PlaylistFacepile(val avatarStackViewModel: PlaylistAvatarStack? = null)

@Serializable
data class PlaylistAvatarStack(val text: PlaylistFacepileText? = null)

@Serializable
data class PlaylistFacepileText(val content: String? = null)

@Serializable
data class PlaylistDescShelf(val musicDescriptionShelfRenderer: PlaylistDescRenderer? = null)

@Serializable
data class PlaylistDescRenderer(val description: Runs? = null)

@Serializable
data class PlaylistSecondary(val sectionListRenderer: PlaylistSecondarySection? = null)

@Serializable
data class PlaylistSecondarySection(val contents: List<PlaylistSecondaryItem>? = null)

@Serializable
data class PlaylistSecondaryItem(val musicPlaylistShelfRenderer: PlaylistShelfRenderer? = null)

@Serializable
data class PlaylistShelfRenderer(
    val playlistId: String? = null,
    val contents: List<PlaylistItemWrapper>? = null
)

@Serializable
data class PlaylistItemWrapper(val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null)
