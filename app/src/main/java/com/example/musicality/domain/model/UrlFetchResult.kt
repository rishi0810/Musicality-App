package com.example.musicality.domain.model

/**
 * Represents the result of fetching a URL with custom headers.
 * Contains response headers and file download information.
 */
data class UrlFetchResult(
    val success: Boolean,
    val responseHeaders: Map<String, String>,
    val fileCode: String,
    val fileName: String,
    val filePath: String,
    val contentLength: Long,
    val contentType: String,
    val errorMessage: String? = null
)

/**
 * Represents the response headers expected from the audio endpoint.
 */
data class AudioResponseHeaders(
    val lastModified: String? = null,
    val contentType: String? = null,
    val date: String? = null,
    val expires: String? = null,
    val cacheControl: String? = null,
    val acceptRanges: String? = null,
    val contentLength: Long = 0L,
    val connection: String? = null,
    val altSvc: String? = null,
    val vary: String? = null,
    val crossOriginResourcePolicy: String? = null,
    val xContentTypeOptions: String? = null,
    val server: String? = null
)
