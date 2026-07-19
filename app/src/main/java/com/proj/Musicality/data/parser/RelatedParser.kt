package com.proj.Musicality.data.parser

import com.proj.Musicality.data.json.RelatedBrowseResponse
import com.proj.Musicality.data.model.HomeSection
import com.proj.Musicality.data.model.RelatedFeed

object RelatedParser {
    fun extractFeed(jsonResponse: String): RelatedFeed {
        val response = JsonParser.instance.decodeFromString<RelatedBrowseResponse>(jsonResponse)
        val contents = response.contents?.sectionListRenderer?.contents.orEmpty()
        val sections = contents.mapNotNull { section ->
            val shelf = section.musicCarouselShelfRenderer
            if (shelf != null) {
                val title = shelf.header
                    ?.musicCarouselShelfBasicHeaderRenderer
                    ?.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text
                    ?.trim()
                    .orEmpty()
                if (title.isBlank()) return@mapNotNull null

                val items = shelf.contents.orEmpty().mapNotNull(HomeParser::parseCarouselItem)
                if (items.isEmpty()) null else HomeSection(title, items, moreEndpoint = null)
            } else {
                null
            }
        }
        val aboutArtist = contents
            .asSequence()
            .mapNotNull { it.musicDescriptionShelfRenderer }
            .firstOrNull { it.header?.runs?.firstOrNull()?.text == "About the artist" }
            ?.description
            ?.runs
            ?.joinToString(separator = "") { it.text }
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return RelatedFeed(sections = sections, aboutArtist = aboutArtist)
    }
}
