package com.example.musicality.data.mapper

import com.example.musicality.domain.model.SongPlaybackInfo

/**
 * Extension function to parse YouTube Music player response
 */
fun Map<String, Any>.parsePlayerResponse(): SongPlaybackInfo? {
    // Check playability status
    val playabilityStatus = this["playabilityStatus"] as? Map<*, *>
    val status = playabilityStatus?.get("status") as? String
    
    if (status != "OK") {
        return null
    }
    
    // Get streaming data
    val streamingData = this["streamingData"] as? Map<*, *> ?: return null
    
    // Get adaptive formats
    val adaptiveFormats = streamingData["adaptiveFormats"] as? List<*> ?: return null
    
    // Filter audio formats (no width property)
    val audioFormats = adaptiveFormats.mapNotNull { format ->
        val formatMap = format as? Map<*, *>
        if (formatMap != null && formatMap["width"] == null) {
            formatMap
        } else {
            null
        }
    }
    
    if (audioFormats.isEmpty()) {
        return null
    }
    
    // Get best audio format (highest bitrate)
    val bestAudio = audioFormats.maxByOrNull { format ->
        (format["bitrate"] as? Number)?.toLong() ?: 0L
    } ?: return null
    
    val mainUrl = bestAudio["url"] as? String ?: return null
    
    // Get video details
    val videoDetails = this["videoDetails"] as? Map<*, *> ?: return null
    
    val videoId = videoDetails["videoId"] as? String ?: ""
    val title = videoDetails["title"] as? String ?: ""
    val lengthSeconds = videoDetails["lengthSeconds"] as? String ?: "0"
    val author = videoDetails["author"] as? String ?: ""
    val viewCount = videoDetails["viewCount"] as? String ?: "0"
    
    // Get thumbnail URL
    val thumbnail = videoDetails["thumbnail"] as? Map<*, *>
    val thumbnails = thumbnail?.get("thumbnails") as? List<*>
    val lastThumbnail = thumbnails?.lastOrNull() as? Map<*, *>
    val thumbnailUrl = lastThumbnail?.get("url") as? String ?: ""
    
    return SongPlaybackInfo(
        mainUrl = mainUrl,
        videoId = videoId,
        title = title,
        lengthSeconds = lengthSeconds,
        thumbnailUrl = thumbnailUrl,
        author = author,
        viewCount = viewCount
    )
}
