package com.example.musicality.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request body for YouTube Music search API
 */
@JsonClass(generateAdapter = true)
data class SearchRequestDto(
    @Json(name = "input")
    val input: String,
    
    @Json(name = "context")
    val context: SearchContextDto
)

@JsonClass(generateAdapter = true)
data class SearchContextDto(
    @Json(name = "client")
    val client: SearchClientDto
)

@JsonClass(generateAdapter = true)
data class SearchClientDto(
    @Json(name = "clientName")
    val clientName: String = "WEB_REMIX",
    
    @Json(name = "clientVersion")
    val clientVersion: String = "1.20251229.03.00"
)

/**
 * Request body for full YouTube Music search API (with type filtering)
 */
@JsonClass(generateAdapter = true)
data class FullSearchRequestDto(
    @Json(name = "query")
    val query: String,
    
    @Json(name = "params")
    val params: String,
    
    @Json(name = "context")
    val context: SearchContextDto,
    
    @Json(name = "inlineSettingStatus")
    val inlineSettingStatus: String = "INLINE_SETTING_STATUS_ON"
)

/**
 * Request body for search pagination (continuation)
 */
@JsonClass(generateAdapter = true)
data class SearchPaginationRequestDto(
    @Json(name = "context")
    val context: SearchContextDto
)
