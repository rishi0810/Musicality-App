package com.proj.Musicality.data.json

import kotlinx.serialization.Serializable

@Serializable
data class SuggestionResponse(val contents: List<SuggestionSection>? = null)

@Serializable
data class SuggestionSection(val searchSuggestionsSectionRenderer: SuggestionSectionRenderer? = null)

@Serializable
data class SuggestionSectionRenderer(val contents: List<SuggestionItem>? = null)

@Serializable
data class SuggestionItem(
    val searchSuggestionRenderer: SearchSuggestionRenderer? = null,
    val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null
)

@Serializable
data class SearchSuggestionRenderer(
    val suggestion: Runs? = null,
    val navigationEndpoint: NavigationEndpoint? = null
)
