package com.example.musicality.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request body for YouTube Music "next" API to get related songs
 * Used for both getting playlist ID and fetching queue
 */
@JsonClass(generateAdapter = true)
data class QueueRequestDto(
    @Json(name = "context")
    val context: QueueContextDto,
    
    @Json(name = "videoId")
    val videoId: String? = null,
    
    @Json(name = "playlistId")
    val playlistId: String? = null
)

@JsonClass(generateAdapter = true)
data class QueueContextDto(
    @Json(name = "client")
    val client: QueueClientDto
)

@JsonClass(generateAdapter = true)
data class QueueClientDto(
    @Json(name = "clientName")
    val clientName: String = "WEB_REMIX",
    
    @Json(name = "clientVersion")
    val clientVersion: String = "1.20251210.03.00"
)
