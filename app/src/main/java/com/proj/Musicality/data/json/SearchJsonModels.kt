package com.proj.Musicality.data.json

import kotlinx.serialization.Serializable

@Serializable
data class SearchResultResponse(val contents: SearchResultContents? = null)

@Serializable
data class SearchResultContents(val tabbedSearchResultsRenderer: TabbedSearchResultsRenderer? = null)

@Serializable
data class TabbedSearchResultsRenderer(val tabs: List<SearchTabRenderer> = emptyList())

@Serializable
data class SearchTabRenderer(val tabRenderer: SearchTabContent? = null)

@Serializable
data class SearchTabContent(val content: SearchSectionListWrapper? = null)

@Serializable
data class SearchSectionListWrapper(val sectionListRenderer: SearchSectionList? = null)

@Serializable
data class SearchSectionList(val contents: List<SearchSectionContent> = emptyList())

@Serializable
data class SearchSectionContent(
    val musicShelfRenderer: SearchMusicShelfRenderer? = null,
    val musicCardShelfRenderer: MusicCardShelfRenderer? = null
)

@Serializable
data class MusicCardShelfRenderer(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val buttons: List<ButtonWrapper>? = null
)

@Serializable
data class ButtonWrapper(
    val buttonRenderer: ButtonRenderer? = null
)

@Serializable
data class ButtonRenderer(
    val command: NavigationEndpoint? = null
)

@Serializable
data class SearchMusicShelfRenderer(val contents: List<MusicResponsiveListItemWrapper> = emptyList())

@Serializable
data class MusicResponsiveListItemWrapper(
    val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null
)
