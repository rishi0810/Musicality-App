package com.example.musicality.data.mapper

import com.example.musicality.domain.model.SearchResponse
import com.example.musicality.domain.model.SearchResult

/**
 * Extension function to parse YouTube Music API response (as generic Map) to domain model
 * Mimics the JavaScript parsing logic
 */
fun Map<String, Any>.parseYouTubeMusicResponse(): SearchResponse {
    val suggestions = mutableListOf<String>()
    val results = mutableListOf<SearchResult>()

    val rootContents = this["contents"] as? List<*>

    if (rootContents != null) {
        for (section in rootContents) {
            val sectionMap = section as? Map<*, *> ?: continue
            val sectionRenderer = sectionMap["searchSuggestionsSectionRenderer"] as? Map<*, *>
            val sectionContents = sectionRenderer?.get("contents") as? List<*>

            if (sectionContents != null) {
                for (item in sectionContents) {
                    val itemMap = item as? Map<*, *> ?: continue

                    // Extract suggestions
                    val suggestionNode = itemMap["searchSuggestionRenderer"] as? Map<*, *>
                    if (suggestionNode != null) {
                        try {
                            val navEndpoint = suggestionNode["navigationEndpoint"] as? Map<*, *>
                            val searchEndpoint = navEndpoint?.get("searchEndpoint") as? Map<*, *>
                            val query = searchEndpoint?.get("query") as? String
                            if (query != null) {
                                suggestions.add(query)
                            }
                        } catch (e: Exception) {
                            // Skip failed suggestion extraction
                        }
                    }

                    // Extract results
                    val resultNode = itemMap["musicResponsiveListItemRenderer"] as? Map<*, *>
                    if (resultNode != null) {
                        try {
                            val result = parseResultNode(resultNode)
                            if (result != null) {
                                results.add(result)
                            }
                        } catch (e: Exception) {
                            // Skip failed result extraction
                        }
                    }
                }
            }
        }
    }

    return SearchResponse(
        suggestions = suggestions,
        results = results
    )
}

/**
 * Parse a music responsive list item renderer to a SearchResult
 */
private fun parseResultNode(resultNode: Map<*, *>): SearchResult? {
    // Extract title
    val flexColumns = resultNode["flexColumns"] as? List<*>
    val firstColumn = flexColumns?.getOrNull(0) as? Map<*, *>
    val firstColumnRenderer = firstColumn?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
    val firstText = firstColumnRenderer?.get("text") as? Map<*, *>
    val firstRuns = firstText?.get("runs") as? List<*>
    val firstRun = firstRuns?.getOrNull(0) as? Map<*, *>
    val title = firstRun?.get("text") as? String ?: return null

    // Extract thumbnail
    val thumbnail = resultNode["thumbnail"] as? Map<*, *>
    val musicThumbnailRenderer = thumbnail?.get("musicThumbnailRenderer") as? Map<*, *>
    val thumbnailObj = musicThumbnailRenderer?.get("thumbnail") as? Map<*, *>
    val thumbnails = thumbnailObj?.get("thumbnails") as? List<*>
    val lastThumbnail = thumbnails?.lastOrNull() as? Map<*, *>
    val thumbnailUrl = lastThumbnail?.get("url") as? String ?: ""

    // Extract isExplicit
    val badges = resultNode["badges"] as? List<*>
    var isExplicit = false
    if (badges != null) {
        for (badge in badges) {
            val badgeMap = badge as? Map<*, *> ?: continue
            val badgeRenderer = badgeMap["musicInlineBadgeRenderer"] as? Map<*, *>
            val icon = badgeRenderer?.get("icon") as? Map<*, *>
            val iconType = icon?.get("iconType") as? String
            if (iconType == "MUSIC_EXPLICIT_BADGE") {
                isExplicit = true
                break
            }
        }
    }

    // Extract year
    val secondColumn = flexColumns?.getOrNull(1) as? Map<*, *>
    val secondColumnRenderer = secondColumn?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
    val secondText = secondColumnRenderer?.get("text") as? Map<*, *>
    val secondColumnRuns = secondText?.get("runs") as? List<*>
    
    var year = ""
    if (secondColumnRuns != null) {
        val lastRun = secondColumnRuns.lastOrNull() as? Map<*, *>
        val lastText = lastRun?.get("text") as? String ?: ""
        if (lastText.trim().matches(Regex("^\\d{4}$"))) {
            year = lastText.trim()
        }
    }

    // Extract navigation info
    val navEndpoint = resultNode["navigationEndpoint"] as? Map<*, *>
    val browseEndpoint = navEndpoint?.get("browseEndpoint") as? Map<*, *>
    val browseId = browseEndpoint?.get("browseId") as? String
    val watchEndpoint = navEndpoint?.get("watchEndpoint") as? Map<*, *>
    val videoId = watchEndpoint?.get("videoId") as? String
    
    val browseConfigs = browseEndpoint?.get("browseEndpointContextSupportedConfigs") as? Map<*, *>
    val musicConfig = browseConfigs?.get("browseEndpointContextMusicConfig") as? Map<*, *>
    val pageType = musicConfig?.get("pageType") as? String

    // Determine type and create appropriate result
    return when {
        // Artist
        browseId != null && browseId.startsWith("UC") && pageType == "MUSIC_PAGE_TYPE_ARTIST" -> {
            val firstRun = secondColumnRuns?.getOrNull(0) as? Map<*, *>
            val monthlyAudience = firstRun?.get("text") as? String ?: ""
            
            SearchResult.Artist(
                id = browseId,
                name = title,
                thumbnailUrl = thumbnailUrl,
                isExplicit = isExplicit,
                year = year,
                monthlyAudience = monthlyAudience
            )
        }

        // Album
        browseId != null && browseId.startsWith("MPREb_") -> {
            val artistName = extractArtistName(secondColumnRuns)
            
            SearchResult.Album(
                id = browseId,
                name = title,
                thumbnailUrl = thumbnailUrl,
                isExplicit = isExplicit,
                year = year,
                singer = artistName
            )
        }

        // Playlist
        browseId != null && browseId.startsWith("VL") -> {
            val lastRun = secondColumnRuns?.lastOrNull() as? Map<*, *>
            val views = lastRun?.get("text") as? String ?: ""
            
            SearchResult.Playlist(
                id = browseId,
                name = title,
                thumbnailUrl = thumbnailUrl,
                isExplicit = isExplicit,
                year = year,
                views = views
            )
        }

        // Song
        videoId != null -> {
            val artistName = extractArtistName(secondColumnRuns)
            
            SearchResult.Song(
                id = videoId,
                name = title,
                thumbnailUrl = thumbnailUrl,
                isExplicit = isExplicit,
                year = year,
                singer = artistName
            )
        }

        else -> null
    }
}

/**
 * Extract artist name from runs
 */
private fun extractArtistName(runs: List<*>?): String {
    if (runs == null) return ""

    // Find run with artist page type
    for (run in runs) {
        val runMap = run as? Map<*, *> ?: continue
        val navEndpoint = runMap["navigationEndpoint"] as? Map<*, *>
        val browseEndpoint = navEndpoint?.get("browseEndpoint") as? Map<*, *>
        val configs = browseEndpoint?.get("browseEndpointContextSupportedConfigs") as? Map<*, *>
        val musicConfig = configs?.get("browseEndpointContextMusicConfig") as? Map<*, *>
        val pageType = musicConfig?.get("pageType") as? String
        
        if (pageType == "MUSIC_PAGE_TYPE_ARTIST") {
            return runMap["text"] as? String ?: ""
        }
    }

    // Fallback: get third run if available
    if (runs.size > 2) {
        val thirdRun = runs[2] as? Map<*, *>
        return thirdRun?.get("text") as? String ?: ""
    }

    return ""
}
