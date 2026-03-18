package com.proj.Musicality.data.model

import androidx.compose.runtime.Immutable

/**
 * Top-level result from browseId = "FEmusic_charts".
 *
 * [selectedCountryName] is the human-readable label of the active filter (e.g. "India").
 * [countries] is the full list extracted from the country dropdown; pass
 * [ChartCountry.code] as `formData.selectedValues` to fetch a different country's charts.
 * [sections] reuses [HomeSection]/[HomeItem] — items are either
 *   [HomeItem.Card] (playlists, pageType = MUSIC_PAGE_TYPE_PLAYLIST) or
 *   [HomeItem.Card] (artists, pageType = MUSIC_PAGE_TYPE_ARTIST).
 */
@Immutable
data class ChartsFeed(
    val selectedCountryName: String,
    val countries: List<ChartCountry>,
    val sections: List<HomeSection>
)

/**
 * A single entry in the country-selector dropdown.
 *
 * Pass [code] as `formData.selectedValues[0]` in the browse request body.
 * "ZZ" is the Global/worldwide entry.
 */
@Immutable
data class ChartCountry(
    val name: String,
    /** ISO 3166-1 alpha-2 country code, or "ZZ" for Global. */
    val code: String,
    val isSelected: Boolean
)
