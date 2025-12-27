package com.example.musicality.data.mapper

import android.util.Log
import com.example.musicality.domain.model.AlbumDetail
import com.example.musicality.domain.model.AlbumSong
import com.example.musicality.domain.model.RelatedAlbum

private const val TAG = "AlbumMapper"

/**
 * Extension function to parse album details from raw YouTube Music browse API response
 */
fun Map<String, Any>.parseAlbumDetails(): AlbumDetail {
    var albumThumbnail = ""
    var albumName = ""
    var artist = ""
    var count = ""
    var duration = ""
    val songs = mutableListOf<AlbumSong>()
    val moreAlbums = mutableListOf<RelatedAlbum>()
    
    try {
        val contents = this["contents"] as? Map<*, *>
        val twoColumnRenderer = contents?.get("twoColumnBrowseResultsRenderer") as? Map<*, *>
        val tabs = twoColumnRenderer?.get("tabs") as? List<*>
        val firstTab = tabs?.getOrNull(0) as? Map<*, *>
        val tabRenderer = firstTab?.get("tabRenderer") as? Map<*, *>
        val tabContent = tabRenderer?.get("content") as? Map<*, *>
        val sectionListRenderer = tabContent?.get("sectionListRenderer") as? Map<*, *>
        val sectionContents = sectionListRenderer?.get("contents") as? List<*>
        val firstSection = sectionContents?.getOrNull(0) as? Map<*, *>
        val headerRenderer = firstSection?.get("musicResponsiveHeaderRenderer") as? Map<*, *>
        
        // Extract album thumbnail
        try {
            val thumbnail = headerRenderer?.get("thumbnail") as? Map<*, *>
            val musicThumbnailRenderer = thumbnail?.get("musicThumbnailRenderer") as? Map<*, *>
            val thumbnailObj = musicThumbnailRenderer?.get("thumbnail") as? Map<*, *>
            val thumbnails = thumbnailObj?.get("thumbnails") as? List<*>
            if (thumbnails != null && thumbnails.isNotEmpty()) {
                val lastThumbnail = thumbnails.last() as? Map<*, *>
                albumThumbnail = (lastThumbnail?.get("url") as? String) ?: ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract album thumbnail: ${e.message}")
        }
        
        // Extract album name from title
        try {
            val title = headerRenderer?.get("title") as? Map<*, *>
            val runs = title?.get("runs") as? List<*>
            val firstRun = runs?.getOrNull(0) as? Map<*, *>
            albumName = (firstRun?.get("text") as? String) ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract album name: ${e.message}")
        }
        
        // Extract artist name from straplineTextOne
        try {
            val straplineTextOne = headerRenderer?.get("straplineTextOne") as? Map<*, *>
            val runs = straplineTextOne?.get("runs") as? List<*>
            val firstRun = runs?.getOrNull(0) as? Map<*, *>
            artist = (firstRun?.get("text") as? String) ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract artist name: ${e.message}")
        }
        
        // Extract count and duration from secondSubtitle
        try {
            val secondSubtitle = headerRenderer?.get("secondSubtitle") as? Map<*, *>
            val subtitleRuns = secondSubtitle?.get("runs") as? List<*>
            if (subtitleRuns != null) {
                // Filter out delimiter runs (those containing " • ")
                val stats = subtitleRuns
                    .filterIsInstance<Map<*, *>>()
                    .map { it["text"] as? String ?: "" }
                    .filter { it.isNotBlank() && it != " • " }
                
                // stats[0] is typically "12 songs", stats[1] is typically "41 minutes"
                if (stats.isNotEmpty()) {
                    count = stats[0]
                }
                if (stats.size >= 2) {
                    duration = stats[1]
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract count/duration: ${e.message}")
        }
        
        // Extract songs from musicShelfRenderer in secondaryContents
        try {
            val secondaryContents = twoColumnRenderer?.get("secondaryContents") as? Map<*, *>
            val secSectionListRenderer = secondaryContents?.get("sectionListRenderer") as? Map<*, *>
            val secContents = secSectionListRenderer?.get("contents") as? List<*>
            val firstSecContent = secContents?.getOrNull(0) as? Map<*, *>
            val musicShelfRenderer = firstSecContent?.get("musicShelfRenderer") as? Map<*, *>
            val songItems = musicShelfRenderer?.get("contents") as? List<*>
            
            songItems?.forEach { item ->
                try {
                    val itemMap = item as? Map<*, *>
                    val renderer = itemMap?.get("musicResponsiveListItemRenderer") as? Map<*, *>
                    
                    if (renderer != null) {
                        val playlistItemData = renderer["playlistItemData"] as? Map<*, *>
                        val videoId = playlistItemData?.get("videoId") as? String ?: ""
                        
                        val flexColumns = renderer["flexColumns"] as? List<*>
                        
                        // Title from first flex column
                        val firstFlexCol = flexColumns?.getOrNull(0) as? Map<*, *>
                        val firstFlexRenderer = firstFlexCol?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
                        val firstFlexText = firstFlexRenderer?.get("text") as? Map<*, *>
                        val firstFlexRuns = firstFlexText?.get("runs") as? List<*>
                        val titleRun = firstFlexRuns?.getOrNull(0) as? Map<*, *>
                        val title = titleRun?.get("text") as? String ?: ""
                        
                        // View count from third flex column
                        val thirdFlexCol = flexColumns?.getOrNull(2) as? Map<*, *>
                        val thirdFlexRenderer = thirdFlexCol?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
                        val thirdFlexText = thirdFlexRenderer?.get("text") as? Map<*, *>
                        val thirdFlexRuns = thirdFlexText?.get("runs") as? List<*>
                        val viewCountRun = thirdFlexRuns?.getOrNull(0) as? Map<*, *>
                        val viewCount = viewCountRun?.get("text") as? String ?: ""
                        
                        // Duration from fixed column
                        val fixedColumns = renderer["fixedColumns"] as? List<*>
                        val firstFixedCol = fixedColumns?.getOrNull(0) as? Map<*, *>
                        val fixedColRenderer = firstFixedCol?.get("musicResponsiveListItemFixedColumnRenderer") as? Map<*, *>
                        val fixedText = fixedColRenderer?.get("text") as? Map<*, *>
                        val fixedRuns = fixedText?.get("runs") as? List<*>
                        val durationRun = fixedRuns?.getOrNull(0) as? Map<*, *>
                        val songDuration = durationRun?.get("text") as? String ?: ""
                        
                        if (videoId.isNotBlank() && title.isNotBlank()) {
                            songs.add(
                                AlbumSong(
                                    videoId = videoId,
                                    title = title,
                                    viewCount = viewCount,
                                    duration = songDuration
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to extract song from item: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract songs: ${e.message}")
        }
        
        // Extract moreAlbums from "Releases for you" carousel
        try {
            val secondaryContents = twoColumnRenderer?.get("secondaryContents") as? Map<*, *>
            val secSectionListRenderer = secondaryContents?.get("sectionListRenderer") as? Map<*, *>
            val secContents = secSectionListRenderer?.get("contents") as? List<*>
            
            secContents?.forEach { shelf ->
                try {
                    val shelfMap = shelf as? Map<*, *>
                    val carousel = shelfMap?.get("musicCarouselShelfRenderer") as? Map<*, *>
                    
                    if (carousel != null) {
                        // Check the header title
                        val header = carousel["header"] as? Map<*, *>
                        val basicHeader = header?.get("musicCarouselShelfBasicHeaderRenderer") as? Map<*, *>
                        val headerTitle = basicHeader?.get("title") as? Map<*, *>
                        val headerRuns = headerTitle?.get("runs") as? List<*>
                        val headerFirstRun = headerRuns?.getOrNull(0) as? Map<*, *>
                        val shelfTitle = headerFirstRun?.get("text") as? String ?: ""
                        
                        // Extract albums from any carousel (not just "Releases for you")
                        val carouselContents = carousel["contents"] as? List<*>
                        
                        carouselContents?.forEach { carouselItem ->
                            try {
                                val carouselItemMap = carouselItem as? Map<*, *>
                                val albumData = carouselItemMap?.get("musicTwoRowItemRenderer") as? Map<*, *>
                                
                                if (albumData != null) {
                                    // Album thumbnail
                                    val thumbnailRenderer = albumData["thumbnailRenderer"] as? Map<*, *>
                                    val musicThumbRenderer = thumbnailRenderer?.get("musicThumbnailRenderer") as? Map<*, *>
                                    val thumbObj = musicThumbRenderer?.get("thumbnail") as? Map<*, *>
                                    val thumbList = thumbObj?.get("thumbnails") as? List<*>
                                    val lastThumb = thumbList?.lastOrNull() as? Map<*, *>
                                    val albumImg = lastThumb?.get("url") as? String ?: ""
                                    
                                    // Album name
                                    val albumTitle = albumData["title"] as? Map<*, *>
                                    val albumTitleRuns = albumTitle?.get("runs") as? List<*>
                                    val albumTitleRun = albumTitleRuns?.getOrNull(0) as? Map<*, *>
                                    val relatedAlbumName = albumTitleRun?.get("text") as? String ?: ""
                                    
                                    // Album artist (usually at index 2 in subtitle runs)
                                    val subtitle = albumData["subtitle"] as? Map<*, *>
                                    val subtitleRuns = subtitle?.get("runs") as? List<*>
                                    val artistRun = subtitleRuns?.getOrNull(2) as? Map<*, *>
                                    val albumArtist = artistRun?.get("text") as? String ?: ""
                                    
                                    // Album ID from navigation endpoint
                                    val navEndpoint = albumData["navigationEndpoint"] as? Map<*, *>
                                    val browseEndpoint = navEndpoint?.get("browseEndpoint") as? Map<*, *>
                                    val albumId = browseEndpoint?.get("browseId") as? String ?: ""
                                    
                                    if (albumId.isNotBlank() && relatedAlbumName.isNotBlank()) {
                                        moreAlbums.add(
                                            RelatedAlbum(
                                                albumId = albumId,
                                                albumName = relatedAlbumName,
                                                albumImg = albumImg,
                                                albumArtist = albumArtist
                                            )
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to extract album from carousel: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse carousel shelf: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract moreAlbums: ${e.message}")
        }
        
    } catch (e: Exception) {
        Log.e(TAG, "Error parsing album details: ${e.message}")
    }
    
    return AlbumDetail(
        albumThumbnail = albumThumbnail,
        albumName = albumName,
        artist = artist,
        count = count,
        duration = duration,
        songs = songs,
        moreAlbums = moreAlbums
    )
}
