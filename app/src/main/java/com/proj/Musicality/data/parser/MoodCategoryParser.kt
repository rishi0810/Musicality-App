package com.proj.Musicality.data.parser

import com.proj.Musicality.data.json.*
import com.proj.Musicality.data.model.*

/**
 * Parses the response from browseId = "FEmusic_moods_and_genres_category".
 *
 * Response structure mirrors the home feed: a sectionListRenderer containing
 * musicCarouselShelfRenderer sections whose items are musicTwoRowItemRenderer
 * cards (all MUSIC_PAGE_TYPE_PLAYLIST in practice).
 *
 * Returns a [HomeFeed] with no continuation (the page is single-shot).
 * Each [HomeSection] contains [HomeItem.Card] items.
 *
 * Use [Mood] to get the correct `params` value for each mood/genre.
 */
object MoodCategoryParser {

    // ── Public API ─────────────────────────────────────────────────────────────

    fun parse(jsonResponse: String): HomeFeed {
        val response = JsonParser.instance.decodeFromString<HomeFeedResponse>(jsonResponse)
        val contents = response.contents
            ?.singleColumnBrowseResultsRenderer
            ?.tabs?.firstOrNull()
            ?.tabRenderer?.content
            ?.sectionListRenderer
            ?.contents
            ?: emptyList()

        return HomeFeed(
            sections = parseSections(contents),
            continuation = null
        )
    }

    // ── Section parsing ────────────────────────────────────────────────────────

    private fun parseSections(contents: List<HomeSectionItem>): List<HomeSection> =
        contents.mapNotNull { sectionItem ->
            val shelf = sectionItem.musicCarouselShelfRenderer ?: return@mapNotNull null
            val header = shelf.header?.musicCarouselShelfBasicHeaderRenderer

            val title = header?.title?.runs?.firstOrNull()?.text
                ?: return@mapNotNull null

            val moreEndpoint = parseMoodMoreEndpoint(
                header.moreContentButton?.buttonRenderer?.navigationEndpoint
            )

            val items = shelf.contents
                ?.mapNotNull { it.musicTwoRowItemRenderer?.let(HomeParser::parseCard) }
                ?: emptyList()

            if (items.isEmpty()) return@mapNotNull null

            HomeSection(title, items, moreEndpoint)
        }

    // Mood category "More" buttons always navigate via browseEndpoint; never watchEndpoint.
    private fun parseMoodMoreEndpoint(nav: NavigationEndpoint?): HomeMoreEndpoint? {
        val ep = nav?.browseEndpoint ?: return null
        val browseId = ep.browseId ?: return null
        return HomeMoreEndpoint(
            watchEndpoint = null,
            browseEndpoint = HomeBrowseEndpoint(browseId, params = ep.params)
        )
    }

    // ── Mood params mapping ────────────────────────────────────────────────────

    /**
     * Maps each mood/genre to the `params` value required in the browse request body.
     *
     * Usage:
     * ```
     * val params = MoodCategoryParser.Mood.CHILL.params
     * // pass to RequestExecutor as the browse params for FEmusic_moods_and_genres_category
     * ```
     */
    enum class Mood(val params: String) {
        CHILL     ("ggMPOg1uX1JOQWZFeDByc2Jm"),
        COMMUTE   ("ggMPOg1uX044Z2o5WERLckpU"),
        ENERGIZE  ("ggMPOg1uX2lRZUZiMnNrQnJW"),
        FEEL_GOOD ("ggMPOg1uXzZQbDB5eThLRTQ3"),
        FOCUS     ("ggMPOg1uX0NvNGNhWThMYWRh"),
        GAMING    ("ggMPOg1uX3NmUVV4Vzl3WGQ0"),
        PARTY     ("ggMPOg1uX0pmQ0s2V0JRclZs"),
        ROMANCE   ("ggMPOg1uX0FzQ2FhZWtUY211"),
        SAD       ("ggMPOg1uX0JLQ0gySWZKZVY1"),
        SLEEP     ("ggMPOg1uX1MxaFQ3Z0JMZkN4"),
        WORKOUT   ("ggMPOg1uX09LWkhnTjRGRUJh"),
        DECADES   ("ggMPOg1uX253QXk4VXN5NGdj"),
        FAMILY    ("ggMPOg1uXzMyY3J2SGM0bVh5"),
        MONSOON   ("ggMPOg1uX2FGWmM5SHVqYlJX"),
    }
}
