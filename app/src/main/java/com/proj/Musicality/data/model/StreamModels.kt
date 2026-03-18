package com.proj.Musicality.data.model

data class SongPlaybackDetails(
    val streamUrl: String?,
    val expiry: Long?,
    val viewCount: String,
    val lengthSeconds: Long,
    val channelId: String,
    val description: String
)

data class VideoFetchData(
    val videoId: String,
    val title: String,
    val author: String,
    val channelId: String,
    val lengthSeconds: Long,
    val viewCount: String,
    val thumbnailUrl: String?,
    val streamUrl: String,
    val mimeType: String,
    val bitrate: Int
)
