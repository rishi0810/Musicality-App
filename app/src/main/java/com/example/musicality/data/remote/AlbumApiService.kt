package com.example.musicality.data.remote

import com.example.musicality.data.model.AlbumBrowseRequestDto
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Retrofit API service for fetching album details via YouTube Music browse API
 */
interface AlbumApiService {
    
    /**
     * Get album details including songs, artist, and related albums
     * 
     * @param request Album browse request with browseId (album ID)
     * @return Generic map containing the raw response
     */
    @POST("browse?prettyPrint=false")
    @Headers(
        "accept: */*",
        "accept-language: en-US,en;q=0.9",
        "content-type: application/json",
        "origin: https://music.youtube.com",
        "priority: u=1, i",
        "x-youtube-client-name: 67",
        "x-youtube-client-version: 1.20251210.03.00"
    )
    suspend fun getAlbum(@Body request: AlbumBrowseRequestDto): Map<String, Any>
}
