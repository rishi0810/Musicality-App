package com.proj.Musicality.data.json

import kotlinx.serialization.Serializable

@Serializable
data class VideoFetchResponse(val playerResponse: VideoPlayerResponseData? = null)

@Serializable
data class VideoPlayerResponseData(
    val streamingData: VideoStreamingData? = null,
    val videoDetails: VideoDetailsData? = null
)

@Serializable
data class VideoStreamingData(val adaptiveFormats: List<VideoAdaptiveFormat>? = null)

@Serializable
data class VideoAdaptiveFormat(
    val itag: Int,
    val url: String? = null,
    val mimeType: String? = null,
    val bitrate: Int? = null
)

@Serializable
data class VideoDetailsData(
    val title: String = "",
    val author: String = "",
    val lengthSeconds: String = "0",
    val videoId: String = "",
    val channelId: String = "",
    val viewCount: String = "0",
    val thumbnail: VideoThumbnailDetails? = null
)

@Serializable
data class VideoThumbnailDetails(val thumbnails: List<VideoThumbnailItem>? = null)

@Serializable
data class VideoThumbnailItem(val url: String, val width: Int = 0, val height: Int = 0)
