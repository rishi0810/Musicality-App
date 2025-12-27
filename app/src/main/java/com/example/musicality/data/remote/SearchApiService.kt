package com.example.musicality.data.remote

import com.example.musicality.data.model.SearchRequestDto
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Retrofit API service interface for YouTube Music Search endpoints
 */
interface SearchApiService {
    
    /**
     * Search for music content using YouTube Music API
     * @param request The search request body containing query and context
     * @return Generic map containing raw YouTube Music response
     */
    @POST("music/get_search_suggestions?prettyPrint=false")
    @Headers(
        "accept: */*",
        "accept-language: en-US,en;q=0.9",
        "content-type: application/json",
        "origin: https://music.youtube.com",
        "priority: u=1, i",
        "referer: https://music.youtube.com/",
        "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36",
        "x-origin: https://music.youtube.com"
    )
    suspend fun search(@Body request: SearchRequestDto): Map<String, Any>
}
