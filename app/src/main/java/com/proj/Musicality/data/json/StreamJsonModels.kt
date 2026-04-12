package com.proj.Musicality.data.json

import kotlinx.serialization.Serializable

@Serializable
data class StreamResponse(
    val playerResponse: PlayerResponse? = null,
    val streamingData: StreamingData? = null,
    val videoDetails: VideoDetails? = null,
    val playabilityStatus: PlayabilityStatus? = null
)

@Serializable
data class PlayerResponse(
    val streamingData: StreamingData? = null,
    val videoDetails: VideoDetails? = null,
    val playabilityStatus: PlayabilityStatus? = null
)

@Serializable
data class PlayabilityStatus(
    val status: String? = null,
    val reason: String? = null
)

@Serializable
data class VideoDetails(
    val viewCount: String = "0",
    val lengthSeconds: String = "0",
    val channelId: String = "",
    val shortDescription: String = ""
)

@Serializable
data class StreamingData(
    val adaptiveFormats: List<AdaptiveFormat> = emptyList(),
    val formats: List<AdaptiveFormat> = emptyList(),
    val expiresInSeconds: String? = null
)

@Serializable
data class AdaptiveFormat(val itag: Int, val url: String? = null)
