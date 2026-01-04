package com.example.musicality.data.mapper

import com.example.musicality.domain.model.SongPlaybackInfo

/**
 * Target itag for Opus audio format (highest quality audio-only stream)
 * itag 251: audio/webm; codecs="opus" - 160kbps
 */
private const val TARGET_ITAG_OPUS = 251

/**
 * Fallback itag for AAC audio format
 * itag 140: audio/mp4; codecs="mp4a.40.2" - 128kbps
 */
private const val FALLBACK_ITAG_AAC = 140

/**
 * Extension function to parse YouTube Music player response
 * Prioritizes itag 251 (Opus) for best audio quality with seeking support
 */
fun Map<String, Any>.parsePlayerResponse(): SongPlaybackInfo? {
    // Check playability status
    val playabilityStatus = this["playabilityStatus"] as? Map<*, *>
    val status = playabilityStatus?.get("status") as? String
    
    if (status != "OK") {
        android.util.Log.e("PlayerMapper", "Playback not OK. Status: $status")
        return null
    }
    
    // Get streaming data
    val streamingData = this["streamingData"] as? Map<*, *> ?: run {
        android.util.Log.e("PlayerMapper", "No streamingData found")
        return null
    }
    
    // Get adaptive formats
    val adaptiveFormats = streamingData["adaptiveFormats"] as? List<*> ?: run {
        android.util.Log.e("PlayerMapper", "No adaptiveFormats found")
        return null
    }
    
    // Filter audio formats (no width property = audio only)
    val audioFormats = adaptiveFormats.mapNotNull { format ->
        val formatMap = format as? Map<*, *>
        if (formatMap != null && formatMap["width"] == null) {
            formatMap
        } else {
            null
        }
    }
    
    if (audioFormats.isEmpty()) {
        android.util.Log.e("PlayerMapper", "No audio formats found")
        return null
    }
    
    // Priority 1: Find itag 251 (Opus - best quality)
    var selectedFormat = audioFormats.find { format ->
        (format["itag"] as? Number)?.toInt() == TARGET_ITAG_OPUS
    }
    
    // Priority 2: Find itag 140 (AAC - good compatibility)
    if (selectedFormat == null) {
        selectedFormat = audioFormats.find { format ->
            (format["itag"] as? Number)?.toInt() == FALLBACK_ITAG_AAC
        }
        android.util.Log.d("PlayerMapper", "itag 251 not found, falling back to itag 140")
    }
    
    // Priority 3: Highest bitrate audio as final fallback
    if (selectedFormat == null) {
        selectedFormat = audioFormats.maxByOrNull { format ->
            (format["bitrate"] as? Number)?.toLong() ?: 0L
        }
        android.util.Log.d("PlayerMapper", "No preferred itag found, using highest bitrate audio")
    }
    
    if (selectedFormat == null) {
        android.util.Log.e("PlayerMapper", "Could not select any audio format")
        return null
    }
    
    // Extract stream URL
    val mainUrl = selectedFormat["url"] as? String ?: run {
        android.util.Log.e("PlayerMapper", "No URL found in selected format")
        return null
    }
    
    // Extract additional stream metadata for ExoPlayer
    val mimeType = selectedFormat["mimeType"] as? String ?: "audio/webm; codecs=\"opus\""
    val contentLength = (selectedFormat["contentLength"] as? String)?.toLongOrNull() ?: 0L
    val itag = (selectedFormat["itag"] as? Number)?.toInt() ?: 0
    val bitrate = (selectedFormat["bitrate"] as? Number)?.toLong() ?: 0L
    
    android.util.Log.d("PlayerMapper", "Selected format - itag: $itag, mimeType: $mimeType, contentLength: $contentLength, bitrate: $bitrate")
    
    // Get video details
    val videoDetails = this["videoDetails"] as? Map<*, *> ?: return null
    
    val videoId = videoDetails["videoId"] as? String ?: ""
    val title = videoDetails["title"] as? String ?: ""
    val lengthSeconds = videoDetails["lengthSeconds"] as? String ?: "0"
    val author = videoDetails["author"] as? String ?: ""
    val viewCount = videoDetails["viewCount"] as? String ?: "0"
    val channelId = videoDetails["channelId"] as? String ?: ""
    
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
        viewCount = viewCount,
        channelId = channelId,
        mimeType = mimeType,
        contentLength = contentLength
    )
}

