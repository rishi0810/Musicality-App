package com.example.musicality.data.mapper

import com.example.musicality.domain.model.QueueSong

/**
 * Extension function to extract playlist ID from the first API response
 * This extracts the playlist ID needed to fetch the full queue
 */
@Suppress("UNCHECKED_CAST")
fun Map<String, Any>.extractPlaylistId(targetVideoId: String): String? {
    try {
        val contents = this["contents"] as? Map<String, Any> ?: return null
        val singleColumnRenderer = contents["singleColumnMusicWatchNextResultsRenderer"] as? Map<String, Any> ?: return null
        val tabbedRenderer = singleColumnRenderer["tabbedRenderer"] as? Map<String, Any> ?: return null
        val watchNextTabbedResultsRenderer = tabbedRenderer["watchNextTabbedResultsRenderer"] as? Map<String, Any> ?: return null
        val tabs = watchNextTabbedResultsRenderer["tabs"] as? List<Map<String, Any>> ?: return null
        
        if (tabs.isEmpty()) return null
        
        val firstTab = tabs[0]
        val tabRenderer = firstTab["tabRenderer"] as? Map<String, Any> ?: return null
        val content = tabRenderer["content"] as? Map<String, Any> ?: return null
        val musicQueueRenderer = content["musicQueueRenderer"] as? Map<String, Any> ?: return null
        val queueContent = musicQueueRenderer["content"] as? Map<String, Any> ?: return null
        val playlistPanelRenderer = queueContent["playlistPanelRenderer"] as? Map<String, Any> ?: return null
        val playlistContents = playlistPanelRenderer["contents"] as? List<Map<String, Any>> ?: return null
        
        for (item in playlistContents) {
            val renderer = item["playlistPanelVideoRenderer"] as? Map<String, Any> ?: continue
            val mainVideoId = renderer["videoId"] as? String ?: continue
            
            val menu = renderer["menu"] as? Map<String, Any> ?: continue
            val menuRenderer = menu["menuRenderer"] as? Map<String, Any> ?: continue
            val menuItems = menuRenderer["items"] as? List<Map<String, Any>> ?: continue
            
            for (menuItem in menuItems) {
                val navItem = menuItem["menuNavigationItemRenderer"] as? Map<String, Any> ?: continue
                val navigationEndpoint = navItem["navigationEndpoint"] as? Map<String, Any> ?: continue
                val watchEndpoint = navigationEndpoint["watchEndpoint"] as? Map<String, Any> ?: continue
                
                val innerVideoId = watchEndpoint["videoId"] as? String
                val playlistId = watchEndpoint["playlistId"] as? String
                
                if (innerVideoId == mainVideoId && innerVideoId == targetVideoId && playlistId != null) {
                    return playlistId
                }
            }
        }
        
        return null
    } catch (e: Exception) {
        android.util.Log.e("QueueMapper", "Error extracting playlist ID", e)
        return null
    }
}

/**
 * Extension function to parse queue songs from the second API response
 */
@Suppress("UNCHECKED_CAST")
fun Map<String, Any>.parseQueueSongs(): List<QueueSong> {
    val results = mutableListOf<QueueSong>()
    
    try {
        val contents = this["contents"] as? Map<String, Any> ?: return results
        val singleColumnRenderer = contents["singleColumnMusicWatchNextResultsRenderer"] as? Map<String, Any> ?: return results
        val tabbedRenderer = singleColumnRenderer["tabbedRenderer"] as? Map<String, Any> ?: return results
        val watchNextTabbedResultsRenderer = tabbedRenderer["watchNextTabbedResultsRenderer"] as? Map<String, Any> ?: return results
        val tabs = watchNextTabbedResultsRenderer["tabs"] as? List<Map<String, Any>> ?: return results
        
        if (tabs.isEmpty()) return results
        
        val firstTab = tabs[0]
        val tabRenderer = firstTab["tabRenderer"] as? Map<String, Any> ?: return results
        val content = tabRenderer["content"] as? Map<String, Any> ?: return results
        val musicQueueRenderer = content["musicQueueRenderer"] as? Map<String, Any> ?: return results
        val queueContent = musicQueueRenderer["content"] as? Map<String, Any> ?: return results
        val playlistPanelRenderer = queueContent["playlistPanelRenderer"] as? Map<String, Any> ?: return results
        val songContents = playlistPanelRenderer["contents"] as? List<Map<String, Any>> ?: return results
        
        for (item in songContents) {
            val renderer = item["playlistPanelVideoRenderer"] as? Map<String, Any> ?: continue
            
            try {
                val videoId = renderer["videoId"] as? String ?: continue
                
                // Extract title
                val titleMap = renderer["title"] as? Map<String, Any>
                val titleRuns = titleMap?.get("runs") as? List<Map<String, Any>>
                val title = titleRuns?.firstOrNull()?.get("text") as? String ?: continue
                
                // Extract artist name
                var artistName = ""
                val longBylineText = renderer["longBylineText"] as? Map<String, Any>
                val bylineRuns = longBylineText?.get("runs") as? List<Map<String, Any>>
                
                if (bylineRuns != null) {
                    val artistParts = mutableListOf<String>()
                    for (run in bylineRuns) {
                        val text = run["text"] as? String ?: continue
                        if (text == " • ") break
                        
                        val navEndpoint = run["navigationEndpoint"] as? Map<String, Any>
                        val browseEndpoint = navEndpoint?.get("browseEndpoint") as? Map<String, Any>
                        val contextConfigs = browseEndpoint?.get("browseEndpointContextSupportedConfigs") as? Map<String, Any>
                        val musicConfig = contextConfigs?.get("browseEndpointContextMusicConfig") as? Map<String, Any>
                        val pageType = musicConfig?.get("pageType") as? String
                        
                        if (pageType == "MUSIC_PAGE_TYPE_ARTIST" || navEndpoint == null) {
                            artistParts.add(text)
                        }
                    }
                    artistName = artistParts.joinToString("").trim()
                }
                
                // Extract thumbnail
                val thumbnailContainer = renderer["thumbnail"] as? Map<String, Any>
                val thumbnails = thumbnailContainer?.get("thumbnails") as? List<Map<String, Any>>
                val thumbnailUrl = if (!thumbnails.isNullOrEmpty()) {
                    thumbnails.last()["url"] as? String ?: ""
                } else ""
                
                // Extract duration
                val lengthText = renderer["lengthText"] as? Map<String, Any>
                val lengthRuns = lengthText?.get("runs") as? List<Map<String, Any>>
                val duration = lengthRuns?.firstOrNull()?.get("text") as? String ?: ""
                
                results.add(
                    QueueSong(
                        videoId = videoId,
                        name = title,
                        singer = artistName,
                        thumbnailUrl = thumbnailUrl,
                        duration = duration
                    )
                )
            } catch (e: Exception) {
                android.util.Log.w("QueueMapper", "Failed to extract song from item", e)
            }
        }
        
        return results
    } catch (e: Exception) {
        android.util.Log.e("QueueMapper", "Error parsing queue songs", e)
        return results
    }
}
