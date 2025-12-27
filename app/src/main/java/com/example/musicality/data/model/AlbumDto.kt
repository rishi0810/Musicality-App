package com.example.musicality.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request body for YouTube Music browse API to get album details
 */
@JsonClass(generateAdapter = true)
data class AlbumBrowseRequestDto(
    @Json(name = "context")
    val context: AlbumContextDto,
    
    @Json(name = "browseId")
    val browseId: String
)

@JsonClass(generateAdapter = true)
data class AlbumContextDto(
    @Json(name = "client")
    val client: AlbumClientDto
)

@JsonClass(generateAdapter = true)
data class AlbumClientDto(
    @Json(name = "clientName")
    val clientName: String = "WEB_REMIX",
    
    @Json(name = "clientVersion")
    val clientVersion: String = "1.20251210.03.00"
)
