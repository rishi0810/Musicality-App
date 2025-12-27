package com.example.musicality.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Object for Todo API response
 * This represents the raw JSON structure from the API
 */
@JsonClass(generateAdapter = true)
data class TodoDto(
    @Json(name = "userId")
    val userId: Int,
    
    @Json(name = "id")
    val id: Int,
    
    @Json(name = "title")
    val title: String,
    
    @Json(name = "completed")
    val completed: Boolean
)
