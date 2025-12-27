package com.example.musicality.data.remote

import com.example.musicality.data.model.PlayerRequestDto
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit API service for YouTube Music Player endpoints
 */
interface PlayerApiService {
    
    /**
     * Get song playback information
     * @param request Player request with videoId and context
     * @return Generic map containing player response
     */
    @POST("player")
    @Headers(
        "Content-Type: application/json",
        "X-Goog-Api-Format-Version: 1",
        "X-YouTube-Client-Name: 3",
        "X-YouTube-Client-Version: 20.10.38",
        "X-Origin: https://music.youtube.com",
        "Referer: https://music.youtube.com/",
        "User-Agent: com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip"
    )
    suspend fun getPlayerInfo(
        @Body request: PlayerRequestDto,
        @Query("prettyPrint") prettyPrint: String = "false"
    ): Map<String, Any>
}
