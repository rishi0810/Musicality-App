package com.proj.Musicality.data.json

import kotlinx.serialization.Serializable

@Serializable
data class RelatedBrowseResponse(
    val contents: RelatedContents? = null
)

@Serializable
data class RelatedContents(
    val sectionListRenderer: RelatedSectionList? = null
)

@Serializable
data class RelatedSectionList(
    val contents: List<RelatedSectionItem>? = null
)

@Serializable
data class RelatedSectionItem(
    val musicCarouselShelfRenderer: HomeCarouselShelf? = null,
    val musicDescriptionShelfRenderer: MusicDescriptionShelf? = null
)

@Serializable
data class MusicDescriptionShelf(
    val header: Runs? = null,
    val description: Runs? = null
)
