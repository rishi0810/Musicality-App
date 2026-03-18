package com.proj.Musicality.data.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ── Top-level charts response ──────────────────────────────────────────────────
// Structure mirrors HomeFeedResponse but sections include musicShelfRenderer.

@Serializable
data class ChartsFeedResponse(val contents: ChartsContents? = null)

@Serializable
data class ChartsContents(
    val singleColumnBrowseResultsRenderer: ChartsSingleColumnRenderer? = null
)

@Serializable
data class ChartsSingleColumnRenderer(val tabs: List<ChartsTabRenderer>? = null)

@Serializable
data class ChartsTabRenderer(val tabRenderer: ChartsTabContent? = null)

@Serializable
data class ChartsTabContent(val content: ChartsSectionListWrapper? = null)

@Serializable
data class ChartsSectionListWrapper(val sectionListRenderer: ChartsSectionList? = null)

@Serializable
data class ChartsSectionList(val contents: List<ChartsSectionItem>? = null)

// ── Section item wrapper ───────────────────────────────────────────────────────

@Serializable
data class ChartsSectionItem(
    /** First section only: contains the country-filter dropdown. */
    val musicShelfRenderer: ChartsMusicShelfRenderer? = null,
    /** Remaining sections: "Video charts", "Languages" (country-specific), "Top artists". */
    val musicCarouselShelfRenderer: HomeCarouselShelf? = null
)

// ── Country filter shelf (musicShelfRenderer) ─────────────────────────────────

@Serializable
data class ChartsMusicShelfRenderer(
    val subheaders: List<ChartsSubheader>? = null
)

@Serializable
data class ChartsSubheader(
    val musicSideAlignedItemRenderer: ChartsSideAlignedItem? = null
)

@Serializable
data class ChartsSideAlignedItem(
    val startItems: List<ChartsSortFilterItem>? = null
)

@Serializable
data class ChartsSortFilterItem(
    val musicSortFilterButtonRenderer: ChartsSortFilterButton? = null
)

@Serializable
data class ChartsSortFilterButton(
    /** Text of the currently selected country, e.g. "India". */
    val title: Runs? = null,
    val menu: ChartsMultiSelectMenu? = null
)

@Serializable
data class ChartsMultiSelectMenu(
    val musicMultiSelectMenuRenderer: ChartsMenuRenderer? = null
)

@Serializable
data class ChartsMenuRenderer(
    /** Mix of musicMultiSelectMenuItemRenderer and musicMenuItemDividerRenderer (ignored). */
    val options: List<ChartsMenuOption>? = null
)

@Serializable
data class ChartsMenuOption(
    /** Null for divider entries (musicMenuItemDividerRenderer). */
    val musicMultiSelectMenuItemRenderer: ChartsCountryMenuItem? = null
)

@Serializable
data class ChartsCountryMenuItem(
    val title: Runs? = null,
    /**
     * Base64-encoded entity key. Decoding yields a string that embeds the
     * 2-char country code immediately before the literal "FEmusic_explore".
     * Example: "...316766567INFEmusic_explore..." → code = "IN".
     */
    val formItemEntityKey: String? = null,
    /**
     * Present only when this country is NOT currently selected.
     * Absence (null) means this item IS the active selection.
     */
    val selectedCommand: JsonElement? = null
)
