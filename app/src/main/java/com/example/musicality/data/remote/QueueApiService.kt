package com.example.musicality.data.remote

import com.example.musicality.data.model.QueueRequestDto
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Retrofit API service for fetching related songs queue
 */
interface QueueApiService {
    
    /**
     * Get next/related songs for a video
     * This API is called twice:
     * 1. First with videoId to get the playlistId
     * 2. Then with playlistId to get the full queue
     * 
     * @param request Queue request with either videoId or playlistId
     * @return Generic map containing the response
     */
    @POST("next?prettyPrint=false")
    @Headers(
        "accept: */*",
        "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36",
        "x-youtube-client-name: 67",
        "x-youtube-client-version: 1.20251210.03.00",
        "Content-Type: application/json"
    )
    suspend fun getNext(@Body request: QueueRequestDto): Map<String, Any>
}
