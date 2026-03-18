package com.proj.Musicality.data.parser

import android.util.Base64
import com.proj.Musicality.data.json.*
import com.proj.Musicality.data.model.*
import com.proj.Musicality.util.upscaleThumbnail

/**
 * Parses the response from browseId = "FEmusic_charts" with
 * params = "sgYPRkVtdXNpY19leHBsb3Jl" and formData.selectedValues = ["<countryCode>"].
 *
 * Response structure:
 *  - Section 0: musicShelfRenderer (country-filter dropdown)
 *  - Section 1: "Video charts" carousel (musicTwoRowItemRenderer playlists)
 *  - Section 2: "Languages" carousel, country-specific (musicTwoRowItemRenderer playlists; absent for some countries)
 *  - Section N: "Top artists" carousel (musicResponsiveListItemRenderer artists)
 *
 * All chart playlists and artist cards are returned as [HomeItem.Card] so
 * they integrate seamlessly with the existing ContentCard UI component.
 *
 * Country codes (pass as formData.selectedValues):
 *   "ZZ" = Global, "IN" = India, "US" = United States, etc.
 *   The full list is available in [ChartsFeed.countries].
 */
object ChartsParser {

    // ── Public API ─────────────────────────────────────────────────────────────

    fun parse(jsonResponse: String): ChartsFeed {
        val response = JsonParser.instance.decodeFromString<ChartsFeedResponse>(jsonResponse)
        val sections = response.contents
            ?.singleColumnBrowseResultsRenderer
            ?.tabs?.firstOrNull()
            ?.tabRenderer?.content
            ?.sectionListRenderer
            ?.contents
            ?: emptyList()

        val filterSection = sections.firstOrNull()?.musicShelfRenderer
        val (selectedCountry, countries) = parseCountryFilter(filterSection)

        val homeSections = sections
            .drop(1)
            .mapNotNull { it.musicCarouselShelfRenderer?.let(::parseCarouselSection) }

        return ChartsFeed(
            selectedCountryName = selectedCountry,
            countries = countries,
            sections = homeSections
        )
    }

    // ── Country filter ─────────────────────────────────────────────────────────

    private fun parseCountryFilter(
        shelf: ChartsMusicShelfRenderer?
    ): Pair<String, List<ChartCountry>> {
        val sortBtn = shelf
            ?.subheaders?.firstOrNull()
            ?.musicSideAlignedItemRenderer
            ?.startItems?.firstOrNull()
            ?.musicSortFilterButtonRenderer
            ?: return Pair("", emptyList())

        val selectedName = sortBtn.title?.runs?.firstOrNull()?.text ?: ""

        val seen = LinkedHashSet<String>()
        val countries = sortBtn.menu
            ?.musicMultiSelectMenuRenderer
            ?.options
            ?.mapNotNull { it.musicMultiSelectMenuItemRenderer?.let(::parseCountryItem) }
            ?.filter { seen.add(it.code) }   // deduplicate (India appears twice: promoted + alphabetical)
            ?: emptyList()

        return Pair(selectedName, countries)
    }

    private fun parseCountryItem(item: ChartsCountryMenuItem): ChartCountry? {
        val name = item.title?.runs?.firstOrNull()?.text ?: return null
        val code = extractCountryCode(item.formItemEntityKey ?: return null)
            .takeIf { it.isNotEmpty() } ?: return null
        val isSelected = item.selectedCommand == null
        return ChartCountry(name, code, isSelected)
    }

    /**
     * The formItemEntityKey is a URL-percent-encoded base64 string. When decoded
     * it embeds the ISO country code as the 2 characters immediately preceding
     * the literal "FEmusic_explore", e.g.:
     *   "...316766567INFEmusic_explore..." → "IN"
     *   "...316766567ZZFEmusic_explore..." → "ZZ"
     */
    private fun extractCountryCode(entityKey: String): String {
        return try {
            val urlDecoded = entityKey.replace("%3D", "=").replace("%2B", "+").replace("%2F", "/")
            val decoded = Base64.decode(urlDecoded, Base64.DEFAULT).toString(Charsets.UTF_8)
            val idx = decoded.indexOf("FEmusic_explore")
            if (idx >= 2) decoded.substring(idx - 2, idx) else ""
        } catch (_: Exception) {
            ""
        }
    }

    // ── Carousel sections ──────────────────────────────────────────────────────

    private fun parseCarouselSection(shelf: HomeCarouselShelf): HomeSection? {
        val header = shelf.header?.musicCarouselShelfBasicHeaderRenderer
        val title = header?.title?.runs?.firstOrNull()?.text ?: return null

        val moreEndpoint = parseMoodMoreEndpoint(
            header.moreContentButton?.buttonRenderer?.navigationEndpoint
        )

        val items = shelf.contents?.mapNotNull(::parseItem) ?: emptyList()
        if (items.isEmpty()) return null

        return HomeSection(title, items, moreEndpoint)
    }

    private fun parseItem(item: HomeCarouselItem): HomeItem? =
        item.musicTwoRowItemRenderer?.let(HomeParser::parseCard)
            ?: item.musicResponsiveListItemRenderer?.let(::parseArtistRow)

    // ── Artist rows (musicResponsiveListItemRenderer in "Top artists") ─────────

    /**
     * Artist rows in the Top Artists carousel use musicResponsiveListItemRenderer:
     *   col0 = artist name
     *   col1 = subscriber count text, e.g. "1.88M subscribers"
     *   navigationEndpoint.browseEndpoint = artist browse ID
     *   pageType = MUSIC_PAGE_TYPE_ARTIST
     */
    private fun parseArtistRow(item: MusicResponsiveListItemRenderer): HomeItem.Card? {
        val cols = item.flexColumns ?: return null
        val title = cols.getOrNull(0)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?.firstOrNull()?.text
            ?: return null

        val subtitle = cols.getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?.joinToString("") { it.text }
            ?.ifEmpty { null }

        val browseEp = item.navigationEndpoint?.browseEndpoint ?: return null
        val id = browseEp.browseId ?: return null
        val pageType = browseEp.browseEndpointContextSupportedConfigs
            ?.browseEndpointContextMusicConfig?.pageType

        val thumbnailUrl = bestThumbUrl(
            (item.thumbnail?.musicThumbnailRenderer?.thumbnailImage
                ?: item.thumbnail?.musicThumbnailRenderer?.thumbnail)?.thumbnails
        )

        return HomeItem.Card(
            id = id,
            videoId = null,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnailUrl,
            pageType = pageType,
            musicVideoType = null
        )
    }

    // ── More-endpoint helper ───────────────────────────────────────────────────

    // Charts "More" buttons use browseEndpoint only (no watchEndpoint).
    private fun parseMoodMoreEndpoint(nav: NavigationEndpoint?): HomeMoreEndpoint? {
        val ep = nav?.browseEndpoint ?: return null
        val browseId = ep.browseId ?: return null
        return HomeMoreEndpoint(
            watchEndpoint = null,
            browseEndpoint = HomeBrowseEndpoint(browseId, params = ep.params)
        )
    }

    // ── Thumbnail helper ───────────────────────────────────────────────────────

    private fun bestThumbUrl(thumbnails: List<Thumbnail>?): String? {
        val highest = thumbnails?.maxByOrNull { it.width }?.url
        return upscaleThumbnail(highest, size = 544)
    }
}
