package com.example.musicality.data.mapper

import android.util.Log
import com.example.musicality.domain.model.PlaylistDetail
import com.example.musicality.domain.model.PlaylistSong

private const val TAG = "PlaylistMapper"

/**
 * Extension function to parse playlist details from raw YouTube Music browse API response
 */
fun Map<String, Any>.parsePlaylistDetails(): PlaylistDetail {
    var thumbnailImg = ""
    var playlistName = ""
    var totalTracks = 0
    var totalTime = ""
    val songs = mutableListOf<PlaylistSong>()
    
    try {
        // --- 1. Extract Playlist Thumbnail from microformat ---
        try {
            val microformat = this["microformat"] as? Map<*, *>
            val microformatDataRenderer = microformat?.get("microformatDataRenderer") as? Map<*, *>
            val thumbnail = microformatDataRenderer?.get("thumbnail") as? Map<*, *>
            val thumbnails = thumbnail?.get("thumbnails") as? List<*>
            if (thumbnails != null && thumbnails.isNotEmpty()) {
                val lastThumbnail = thumbnails.last() as? Map<*, *>
                thumbnailImg = (lastThumbnail?.get("url") as? String) ?: ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract playlist thumbnail: ${e.message}")
        }
        
        // --- 2. Extract Playlist Name and Metadata ---
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
            
            // Extract playlist name from title
            try {
                val title = headerRenderer?.get("title") as? Map<*, *>
                val runs = title?.get("runs") as? List<*>
                val firstRun = runs?.getOrNull(0) as? Map<*, *>
                playlistName = (firstRun?.get("text") as? String) ?: ""
            } catch (e: Exception) {
                Log.w(TAG, "Failed to extract playlist name: ${e.message}")
            }
            
            // Extract total tracks and total time from secondSubtitle
            try {
                val secondSubtitle = headerRenderer?.get("secondSubtitle") as? Map<*, *>
                val subtitleRuns = secondSubtitle?.get("runs") as? List<*>
                if (subtitleRuns != null) {
                    for (run in subtitleRuns) {
                        val runMap = run as? Map<*, *>
                        val text = (runMap?.get("text") as? String) ?: ""
                        
                        // Check for tracks
                        if (text.lowercase().contains("track")) {
                            val match = Regex("(\\d+)").find(text)
                            if (match != null) {
                                totalTracks = match.groupValues[1].toIntOrNull() ?: 0
                            }
                        }
                        
                        // Check for duration
                        if (text.contains("minute") || text.contains("hour") || text.contains("second")) {
                            totalTime = text.trim()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to extract playlist metadata: ${e.message}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract header renderer: ${e.message}")
        }
        
        // --- 3. Extract Songs ---
        try {
            val contents = this["contents"] as? Map<*, *>
            val twoColumnRenderer = contents?.get("twoColumnBrowseResultsRenderer") as? Map<*, *>
            val secondaryContents = twoColumnRenderer?.get("secondaryContents") as? Map<*, *>
            val secSectionListRenderer = secondaryContents?.get("sectionListRenderer") as? Map<*, *>
            val secContents = secSectionListRenderer?.get("contents") as? List<*>
            val firstSecContent = secContents?.getOrNull(0) as? Map<*, *>
            val musicPlaylistShelfRenderer = firstSecContent?.get("musicPlaylistShelfRenderer") as? Map<*, *>
            val songItems = musicPlaylistShelfRenderer?.get("contents") as? List<*>
            
            songItems?.forEach { item ->
                try {
                    val itemMap = item as? Map<*, *>
                    val renderer = itemMap?.get("musicResponsiveListItemRenderer") as? Map<*, *>
                    
                    if (renderer != null) {
                        // Video ID
                        val playlistItemData = renderer["playlistItemData"] as? Map<*, *>
                        val videoId = (playlistItemData?.get("videoId") as? String) ?: ""
                        
                        // Song Name
                        val flexColumns = renderer["flexColumns"] as? List<*>
                        val firstFlexCol = flexColumns?.getOrNull(0) as? Map<*, *>
                        val firstFlexRenderer = firstFlexCol?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
                        val firstFlexText = firstFlexRenderer?.get("text") as? Map<*, *>
                        val firstFlexRuns = firstFlexText?.get("runs") as? List<*>
                        val titleRun = firstFlexRuns?.getOrNull(0) as? Map<*, *>
                        val songName = (titleRun?.get("text") as? String) ?: ""
                        
                        // Song Thumbnail
                        val thumbnailObj = renderer["thumbnail"] as? Map<*, *>
                        val musicThumbnailRenderer = thumbnailObj?.get("musicThumbnailRenderer") as? Map<*, *>
                        val thumbInner = musicThumbnailRenderer?.get("thumbnail") as? Map<*, *>
                        val thumbList = thumbInner?.get("thumbnails") as? List<*>
                        val firstThumb = thumbList?.getOrNull(0) as? Map<*, *>
                        val thumbnail = (firstThumb?.get("url") as? String) ?: ""
                        
                        // Duration
                        val fixedColumns = renderer["fixedColumns"] as? List<*>
                        val firstFixedCol = fixedColumns?.getOrNull(0) as? Map<*, *>
                        val fixedColRenderer = firstFixedCol?.get("musicResponsiveListItemFixedColumnRenderer") as? Map<*, *>
                        val fixedText = fixedColRenderer?.get("text") as? Map<*, *>
                        val fixedRuns = fixedText?.get("runs") as? List<*>
                        val durationRun = fixedRuns?.getOrNull(0) as? Map<*, *>
                        val duration = (durationRun?.get("text") as? String) ?: ""
                        
                        // Artists - concatenate all runs from flexColumns[1]
                        val secondFlexCol = flexColumns?.getOrNull(1) as? Map<*, *>
                        val secondFlexRenderer = secondFlexCol?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
                        val secondFlexText = secondFlexRenderer?.get("text") as? Map<*, *>
                        val artistRuns = secondFlexText?.get("runs") as? List<*>
                        val artists = artistRuns
                            ?.filterIsInstance<Map<*, *>>()
                            ?.mapNotNull { it["text"] as? String }
                            ?.joinToString("") ?: ""
                        
                        if (videoId.isNotBlank() && songName.isNotBlank()) {
                            songs.add(
                                PlaylistSong(
                                    videoId = videoId,
                                    songName = songName,
                                    thumbnail = thumbnail,
                                    duration = duration,
                                    artists = artists
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to extract song: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract songs from playlist: ${e.message}")
        }
        
    } catch (e: Exception) {
        Log.e(TAG, "Error parsing playlist details: ${e.message}")
    }
    
    return PlaylistDetail(
        thumbnailImg = thumbnailImg,
        playlistName = playlistName,
        totalTracks = totalTracks,
        totalTime = totalTime,
        songs = songs
    )
}
