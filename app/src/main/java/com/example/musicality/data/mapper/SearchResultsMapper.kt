package com.example.musicality.data.mapper

import com.example.musicality.domain.model.SearchResultType
import com.example.musicality.domain.model.SearchResultsResponse
import com.example.musicality.domain.model.TypedSearchResult

/**
 * Extension function to get the type parameter string for search API
 */
fun SearchResultType.toTypeParam(): String {
    return when (this) {
        SearchResultType.SONGS -> "EgWKAQIIAWoQEAMQBBAFEBAQChAJEBUQEQ%3D%3D"
        SearchResultType.ARTISTS -> "EgWKAQIgAWoQEAMQBBAFEBAQChAJEBUQEQ%3D%3D"
        SearchResultType.VIDEOS -> "EgWKAQIQAWoQEAMQBBAFEBAQChAJEBUQEQ%3D%3D"
        SearchResultType.ALBUMS -> "EgWKAQIYAWoQEAMQBBAFEBAQChAJEBUQEQ%3D%3D"
        SearchResultType.COMMUNITY_PLAYLISTS -> "EgeKAQQoAEABahAQAxAEEAUQEBAKEAkQFRAR"
        SearchResultType.FEATURED_PLAYLISTS -> "EgeKAQQoADgBahIQCRAKEAUQAxAEEBUQDhAQEBE%3D"
    }
}

/**
 * Parse initial search response to domain model
 */
fun Map<String, Any>.parseSearchResultsResponse(searchType: SearchResultType): SearchResultsResponse {
    val results = mutableListOf<TypedSearchResult>()
    var continuationToken: String? = null

    try {
        // Navigate to musicShelfRenderer for initial search
        val contents = this["contents"] as? Map<*, *>
        val tabbedSearchResultsRenderer = contents?.get("tabbedSearchResultsRenderer") as? Map<*, *>
        val tabs = tabbedSearchResultsRenderer?.get("tabs") as? List<*>
        val firstTab = tabs?.getOrNull(0) as? Map<*, *>
        val tabRenderer = firstTab?.get("tabRenderer") as? Map<*, *>
        val tabContent = tabRenderer?.get("content") as? Map<*, *>
        val sectionListRenderer = tabContent?.get("sectionListRenderer") as? Map<*, *>
        val sectionContents = sectionListRenderer?.get("contents") as? List<*>
        val firstSection = sectionContents?.getOrNull(0) as? Map<*, *>
        val musicShelfRenderer = firstSection?.get("musicShelfRenderer") as? Map<*, *>

        if (musicShelfRenderer == null) {
            return SearchResultsResponse(results = emptyList(), continuationToken = null)
        }

        // Extract continuation token (store raw, Retrofit will encode)
        try {
            val continuations = musicShelfRenderer["continuations"] as? List<*>
            val firstContinuation = continuations?.getOrNull(0) as? Map<*, *>
            val nextContinuationData = firstContinuation?.get("nextContinuationData") as? Map<*, *>
            continuationToken = nextContinuationData?.get("continuation") as? String
        } catch (e: Exception) {
            // Failed to extract continuation token
        }

        // Extract results from musicShelfRenderer contents
        val shelfContents = musicShelfRenderer["contents"] as? List<*>
        parseShelfContents(shelfContents, searchType, results)

    } catch (e: Exception) {
        android.util.Log.e("SearchResultsMapper", "Failed to parse search results", e)
    }

    return SearchResultsResponse(results = results, continuationToken = continuationToken)
}

/**
 * Parse continuation/pagination response to domain model
 */
fun Map<String, Any>.parseContinuationResponse(searchType: SearchResultType): SearchResultsResponse {
    val results = mutableListOf<TypedSearchResult>()
    var continuationToken: String? = null

    try {
        // Navigate to musicShelfContinuation for pagination
        val continuationContents = this["continuationContents"] as? Map<*, *>
        val musicShelfContinuation = continuationContents?.get("musicShelfContinuation") as? Map<*, *>

        if (musicShelfContinuation == null) {
            return SearchResultsResponse(results = emptyList(), continuationToken = null)
        }

        // Extract continuation token (store raw, Retrofit will encode)
        try {
            val continuations = musicShelfContinuation["continuations"] as? List<*>
            val firstContinuation = continuations?.getOrNull(0) as? Map<*, *>
            val nextContinuationData = firstContinuation?.get("nextContinuationData") as? Map<*, *>
            continuationToken = nextContinuationData?.get("continuation") as? String
        } catch (e: Exception) {
            // Failed to extract continuation token
        }

        // Extract results from musicShelfContinuation contents
        val shelfContents = musicShelfContinuation["contents"] as? List<*>
        parseShelfContents(shelfContents, searchType, results)

    } catch (e: Exception) {
        android.util.Log.e("SearchResultsMapper", "Failed to parse continuation results", e)
    }

    return SearchResultsResponse(results = results, continuationToken = continuationToken)
}

/**
 * Parse shelf contents based on search type
 */
private fun parseShelfContents(
    contents: List<*>?,
    searchType: SearchResultType,
    results: MutableList<TypedSearchResult>
) {
    if (contents == null) return

    for (item in contents) {
        val itemMap = item as? Map<*, *> ?: continue
        val renderer = itemMap["musicResponsiveListItemRenderer"] as? Map<*, *> ?: continue

        try {
            val result = when (searchType) {
                SearchResultType.SONGS -> parseSongResult(renderer)
                SearchResultType.VIDEOS -> parseVideoResult(renderer)
                SearchResultType.ALBUMS -> parseAlbumResult(renderer)
                SearchResultType.ARTISTS -> parseArtistResult(renderer)
                SearchResultType.COMMUNITY_PLAYLISTS -> parseCommunityPlaylistResult(renderer)
                SearchResultType.FEATURED_PLAYLISTS -> parseFeaturedPlaylistResult(renderer)
            }
            if (result != null) {
                results.add(result)
            }
        } catch (e: Exception) {
            android.util.Log.w("SearchResultsMapper", "Failed to extract ${searchType.displayName} item", e)
        }
    }
}

/**
 * Parse song result from renderer
 */
private fun parseSongResult(renderer: Map<*, *>): TypedSearchResult.Song? {
    // Extract videoId
    val playlistItemData = renderer["playlistItemData"] as? Map<*, *>
    val videoId = playlistItemData?.get("videoId") as? String ?: return null

    // Extract name from first flex column
    val flexColumns = renderer["flexColumns"] as? List<*>
    val firstColumn = flexColumns?.getOrNull(0) as? Map<*, *>
    val firstColumnRenderer = firstColumn?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
    val firstText = firstColumnRenderer?.get("text") as? Map<*, *>
    val firstRuns = firstText?.get("runs") as? List<*>
    val firstRun = firstRuns?.getOrNull(0) as? Map<*, *>
    val name = firstRun?.get("text") as? String ?: return null

    // Extract thumbnail
    val thumbnailUrl = extractThumbnail(renderer)

    // Extract artist names and album from second flex column
    val secondColumn = flexColumns?.getOrNull(1) as? Map<*, *>
    val secondColumnRenderer = secondColumn?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
    val secondText = secondColumnRenderer?.get("text") as? Map<*, *>
    val secondColumnRuns = secondText?.get("runs") as? List<*>

    val artistNames = mutableListOf<String>()
    var albumName = ""
    var duration = ""

    if (secondColumnRuns != null) {
        for (run in secondColumnRuns) {
            val runMap = run as? Map<*, *> ?: continue
            val navEndpoint = runMap["navigationEndpoint"] as? Map<*, *>
            val browseEndpoint = navEndpoint?.get("browseEndpoint") as? Map<*, *>
            val configs = browseEndpoint?.get("browseEndpointContextSupportedConfigs") as? Map<*, *>
            val musicConfig = configs?.get("browseEndpointContextMusicConfig") as? Map<*, *>
            val pageType = musicConfig?.get("pageType") as? String

            if (pageType == "MUSIC_PAGE_TYPE_ARTIST") {
                val artistText = runMap["text"] as? String
                if (artistText != null) {
                    artistNames.add(artistText)
                }
            } else if (pageType == "MUSIC_PAGE_TYPE_ALBUM") {
                albumName = runMap["text"] as? String ?: ""
            } else {
                val text = runMap["text"] as? String ?: ""
                if (text.trim().matches(Regex("^\\d+:\\d+$"))) {
                    duration = text.trim()
                }
            }
        }
    }

    // Extract views from third flex column
    val thirdColumn = flexColumns?.getOrNull(2) as? Map<*, *>
    val thirdColumnRenderer = thirdColumn?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
    val thirdText = thirdColumnRenderer?.get("text") as? Map<*, *>
    val thirdRuns = thirdText?.get("runs") as? List<*>
    val thirdRun = thirdRuns?.getOrNull(0) as? Map<*, *>
    val views = thirdRun?.get("text") as? String ?: ""

    return TypedSearchResult.Song(
        id = videoId,
        name = name,
        thumbnailUrl = thumbnailUrl,
        artistName = artistNames.joinToString(", "),
        albumName = albumName,
        duration = duration,
        views = views
    )
}

/**
 * Parse video result from renderer
 */
private fun parseVideoResult(renderer: Map<*, *>): TypedSearchResult.Video? {
    // Extract videoId
    val playlistItemData = renderer["playlistItemData"] as? Map<*, *>
    val videoId = playlistItemData?.get("videoId") as? String ?: return null

    // Extract name
    val flexColumns = renderer["flexColumns"] as? List<*>
    val firstColumn = flexColumns?.getOrNull(0) as? Map<*, *>
    val firstColumnRenderer = firstColumn?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
    val firstText = firstColumnRenderer?.get("text") as? Map<*, *>
    val firstRuns = firstText?.get("runs") as? List<*>
    val firstRun = firstRuns?.getOrNull(0) as? Map<*, *>
    val name = firstRun?.get("text") as? String ?: return null

    // Extract thumbnail
    val thumbnailUrl = extractThumbnail(renderer)

    // Extract channel, views, duration from second flex column
    val secondColumn = flexColumns?.getOrNull(1) as? Map<*, *>
    val secondColumnRenderer = secondColumn?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
    val secondText = secondColumnRenderer?.get("text") as? Map<*, *>
    val secondColumnRuns = secondText?.get("runs") as? List<*>

    val channelName = (secondColumnRuns?.getOrNull(0) as? Map<*, *>)?.get("text") as? String ?: ""
    val views = (secondColumnRuns?.getOrNull(2) as? Map<*, *>)?.get("text") as? String ?: ""
    val duration = (secondColumnRuns?.getOrNull(4) as? Map<*, *>)?.get("text") as? String ?: ""

    return TypedSearchResult.Video(
        id = videoId,
        name = name,
        thumbnailUrl = thumbnailUrl,
        channelName = channelName,
        views = views,
        duration = duration
    )
}

/**
 * Parse album result from renderer
 */
private fun parseAlbumResult(renderer: Map<*, *>): TypedSearchResult.Album? {
    // Extract albumId from navigationEndpoint
    val navEndpoint = renderer["navigationEndpoint"] as? Map<*, *>
    val browseEndpoint = navEndpoint?.get("browseEndpoint") as? Map<*, *>
    val albumId = browseEndpoint?.get("browseId") as? String ?: return null

    // Extract name
    val flexColumns = renderer["flexColumns"] as? List<*>
    val firstColumn = flexColumns?.getOrNull(0) as? Map<*, *>
    val firstColumnRenderer = firstColumn?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
    val firstText = firstColumnRenderer?.get("text") as? Map<*, *>
    val firstRuns = firstText?.get("runs") as? List<*>
    val firstRun = firstRuns?.getOrNull(0) as? Map<*, *>
    val name = firstRun?.get("text") as? String ?: return null

    // Extract thumbnail
    val thumbnailUrl = extractThumbnail(renderer)

    // Extract artist and year from second flex column
    val secondColumn = flexColumns?.getOrNull(1) as? Map<*, *>
    val secondColumnRenderer = secondColumn?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
    val secondText = secondColumnRenderer?.get("text") as? Map<*, *>
    val secondColumnRuns = secondText?.get("runs") as? List<*>

    // runs pattern: [0]=Type, [1]=Separator, [2]=Artist, [3]=Separator, [4]=Year
    val artistName = (secondColumnRuns?.getOrNull(2) as? Map<*, *>)?.get("text") as? String ?: ""
    val year = (secondColumnRuns?.getOrNull(4) as? Map<*, *>)?.get("text") as? String
        ?: (secondColumnRuns?.lastOrNull() as? Map<*, *>)?.get("text") as? String ?: ""

    return TypedSearchResult.Album(
        id = albumId,
        name = name,
        thumbnailUrl = thumbnailUrl,
        artistName = artistName,
        year = year
    )
}

/**
 * Parse artist result from renderer
 */
private fun parseArtistResult(renderer: Map<*, *>): TypedSearchResult.Artist? {
    // Extract artistId from navigationEndpoint
    val navEndpoint = renderer["navigationEndpoint"] as? Map<*, *>
    val browseEndpoint = navEndpoint?.get("browseEndpoint") as? Map<*, *>
    val artistId = browseEndpoint?.get("browseId") as? String ?: return null

    // Extract name
    val flexColumns = renderer["flexColumns"] as? List<*>
    val firstColumn = flexColumns?.getOrNull(0) as? Map<*, *>
    val firstColumnRenderer = firstColumn?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
    val firstText = firstColumnRenderer?.get("text") as? Map<*, *>
    val firstRuns = firstText?.get("runs") as? List<*>
    val firstRun = firstRuns?.getOrNull(0) as? Map<*, *>
    val name = firstRun?.get("text") as? String ?: return null

    // Extract thumbnail (first/smaller for artists)
    val thumbnail = renderer["thumbnail"] as? Map<*, *>
    val musicThumbnailRenderer = thumbnail?.get("musicThumbnailRenderer") as? Map<*, *>
    val thumbnailObj = musicThumbnailRenderer?.get("thumbnail") as? Map<*, *>
    val thumbnails = thumbnailObj?.get("thumbnails") as? List<*>
    val firstThumbnail = thumbnails?.firstOrNull() as? Map<*, *>
    val thumbnailUrl = firstThumbnail?.get("url") as? String ?: ""

    // Extract monthly audience from second flex column
    val secondColumn = flexColumns?.getOrNull(1) as? Map<*, *>
    val secondColumnRenderer = secondColumn?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
    val secondText = secondColumnRenderer?.get("text") as? Map<*, *>
    val secondColumnRuns = secondText?.get("runs") as? List<*>

    // runs pattern: [0]=Type (Artist), [1]=Separator, [2]=Monthly Audience/Subscribers
    val monthlyAudience = if (secondColumnRuns != null && secondColumnRuns.size > 2) {
        (secondColumnRuns[2] as? Map<*, *>)?.get("text") as? String ?: ""
    } else ""

    return TypedSearchResult.Artist(
        id = artistId,
        name = name,
        thumbnailUrl = thumbnailUrl,
        monthlyAudience = monthlyAudience
    )
}

/**
 * Parse community playlist result from renderer
 */
private fun parseCommunityPlaylistResult(renderer: Map<*, *>): TypedSearchResult.CommunityPlaylist? {
    // Extract playlist ID from navigationEndpoint
    val navEndpoint = renderer["navigationEndpoint"] as? Map<*, *>
    val browseEndpoint = navEndpoint?.get("browseEndpoint") as? Map<*, *>
    val playlistId = browseEndpoint?.get("browseId") as? String ?: return null

    // Extract name
    val flexColumns = renderer["flexColumns"] as? List<*>
    val firstColumn = flexColumns?.getOrNull(0) as? Map<*, *>
    val firstColumnRenderer = firstColumn?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
    val firstText = firstColumnRenderer?.get("text") as? Map<*, *>
    val firstRuns = firstText?.get("runs") as? List<*>
    val firstRun = firstRuns?.getOrNull(0) as? Map<*, *>
    val name = firstRun?.get("text") as? String ?: return null

    // Extract thumbnail
    val thumbnailUrl = extractThumbnail(renderer)

    // Extract artist and views from second flex column
    val secondColumn = flexColumns?.getOrNull(1) as? Map<*, *>
    val secondColumnRenderer = secondColumn?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
    val secondText = secondColumnRenderer?.get("text") as? Map<*, *>
    val secondColumnRuns = secondText?.get("runs") as? List<*>

    // runs pattern: [0]=Artist Name, [1]=Separator, [2]=Views
    val artistName = (secondColumnRuns?.getOrNull(0) as? Map<*, *>)?.get("text") as? String ?: ""
    val views = (secondColumnRuns?.getOrNull(2) as? Map<*, *>)?.get("text") as? String ?: ""

    return TypedSearchResult.CommunityPlaylist(
        id = playlistId,
        name = name,
        thumbnailUrl = thumbnailUrl,
        artistName = artistName,
        views = views
    )
}

/**
 * Parse featured playlist result from renderer
 */
private fun parseFeaturedPlaylistResult(renderer: Map<*, *>): TypedSearchResult.FeaturedPlaylist? {
    // Extract playlist ID from navigationEndpoint
    val navEndpoint = renderer["navigationEndpoint"] as? Map<*, *>
    val browseEndpoint = navEndpoint?.get("browseEndpoint") as? Map<*, *>
    val playlistId = browseEndpoint?.get("browseId") as? String ?: return null

    // Extract name
    val flexColumns = renderer["flexColumns"] as? List<*>
    val firstColumn = flexColumns?.getOrNull(0) as? Map<*, *>
    val firstColumnRenderer = firstColumn?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
    val firstText = firstColumnRenderer?.get("text") as? Map<*, *>
    val firstRuns = firstText?.get("runs") as? List<*>
    val firstRun = firstRuns?.getOrNull(0) as? Map<*, *>
    val name = firstRun?.get("text") as? String ?: return null

    // Extract thumbnail
    val thumbnailUrl = extractThumbnail(renderer)

    // Extract artist and song count from second flex column
    val secondColumn = flexColumns?.getOrNull(1) as? Map<*, *>
    val secondColumnRenderer = secondColumn?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
    val secondText = secondColumnRenderer?.get("text") as? Map<*, *>
    val secondColumnRuns = secondText?.get("runs") as? List<*>

    // runs pattern: [0]=Artist Name, [1]=Separator, [2]=Song Count
    val artistName = (secondColumnRuns?.getOrNull(0) as? Map<*, *>)?.get("text") as? String ?: ""
    val songCount = (secondColumnRuns?.getOrNull(2) as? Map<*, *>)?.get("text") as? String ?: ""

    return TypedSearchResult.FeaturedPlaylist(
        id = playlistId,
        name = name,
        thumbnailUrl = thumbnailUrl,
        artistName = artistName,
        songCount = songCount
    )
}

/**
 * Extract thumbnail URL (last/highest resolution) from renderer
 */
private fun extractThumbnail(renderer: Map<*, *>): String {
    val thumbnail = renderer["thumbnail"] as? Map<*, *>
    val musicThumbnailRenderer = thumbnail?.get("musicThumbnailRenderer") as? Map<*, *>
    val thumbnailObj = musicThumbnailRenderer?.get("thumbnail") as? Map<*, *>
    val thumbnails = thumbnailObj?.get("thumbnails") as? List<*>
    val lastThumbnail = thumbnails?.firstOrNull() as? Map<*, *>
    return lastThumbnail?.get("url") as? String ?: ""
}
