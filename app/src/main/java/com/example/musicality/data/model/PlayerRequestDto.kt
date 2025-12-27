package com.example.musicality.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request body for YouTube Music player API
 */
@JsonClass(generateAdapter = true)
data class PlayerRequestDto(
    @Json(name = "context")
    val context: PlayerContextDto,
    
    @Json(name = "videoId")
    val videoId: String
)

@JsonClass(generateAdapter = true)
data class PlayerContextDto(
    @Json(name = "client")
    val client: PlayerClientDto
)

@JsonClass(generateAdapter = true)
data class PlayerClientDto(
    @Json(name = "clientName")
    val clientName: String = "ANDROID",
    
    @Json(name = "clientVersion")
    val clientVersion: String = "20.10.38",
    
    @Json(name = "gl")
    val gl: String = "US",
    
    @Json(name = "hl")
    val hl: String = "en"
)
