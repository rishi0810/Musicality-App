package com.proj.Musicality.data.parser

import android.net.Uri
import android.util.Log
import com.proj.Musicality.data.json.PlayerResponse
import com.proj.Musicality.data.json.StreamResponse
import com.proj.Musicality.data.model.SongPlaybackDetails

object StreamParser {
    private const val TAG = "StreamParser"

    fun extractSongDetails(jsonResponse: String): SongPlaybackDetails {
        Log.d(TAG, "extractSongDetails: parsing response (${jsonResponse.length} chars)")
        Log.d(TAG, "extractSongDetails: response (first 500): ${jsonResponse.take(500)}")

        val response = JsonParser.instance.decodeFromString<StreamResponse>(jsonResponse)
        val player = response.playerResponse
            ?: PlayerResponse(
                streamingData = response.streamingData,
                videoDetails = response.videoDetails,
                playabilityStatus = response.playabilityStatus
            )

        val hasPlayerResponse = response.playerResponse != null
        val hasStreamingData = player.streamingData != null
        val hasVideoDetails = player.videoDetails != null
        Log.d(TAG, "extractSongDetails: playerResponse=$hasPlayerResponse, streamingData=$hasStreamingData, videoDetails=$hasVideoDetails")

        // Short-circuit on explicit playability errors so we don't waste a retry
        // + VR fallback on something YT already said is unplayable.
        val playability = player.playabilityStatus
        if (playability?.status != null && playability.status != "OK") {
            Log.w(TAG, "extractSongDetails: playability=${playability.status} reason='${playability.reason}' — returning empty details")
            return SongPlaybackDetails(
                streamUrl = null,
                expiry = null,
                viewCount = player.videoDetails?.viewCount ?: "0",
                lengthSeconds = player.videoDetails?.lengthSeconds?.toLongOrNull() ?: 0L,
                channelId = player.videoDetails?.channelId ?: "",
                description = player.videoDetails?.shortDescription ?: ""
            )
        }

        val streamingData = player.streamingData
        val formats = (streamingData?.adaptiveFormats ?: emptyList()) + (streamingData?.formats ?: emptyList())
        val details = player.videoDetails
        Log.d(TAG, "extractSongDetails: ${formats.size} adaptive formats found")
        formats.forEach { fmt ->
            Log.d(TAG, "  format: itag=${fmt.itag}, url=${if (fmt.url != null) "${fmt.url.take(80)}..." else "NULL"}")
        }

        val bestFormat = formats.find { it.itag == 251 }
            ?: formats.find { it.itag == 140 }

        if (bestFormat != null) {
            Log.d(TAG, "extractSongDetails: selected itag=${bestFormat.itag}, url=${bestFormat.url?.take(100)}...")
        } else {
            Log.e(TAG, "extractSongDetails: NO suitable format found (no itag 251 or 140)!")
            Log.e(TAG, "extractSongDetails: available itags: ${formats.map { it.itag }}")
        }

        return SongPlaybackDetails(
            streamUrl = bestFormat?.url,
            expiry = bestFormat?.url?.let(::extractUrlExpiryEpoch),
            viewCount = details?.viewCount ?: "0",
            lengthSeconds = details?.lengthSeconds?.toLongOrNull() ?: 0L,
            channelId = details?.channelId ?: "",
            description = details?.shortDescription ?: ""
        )
    }

    private fun extractUrlExpiryEpoch(url: String): Long? {
        val expireParam = runCatching {
            Uri.parse(url).getQueryParameter("expire")
        }.getOrNull()
        return expireParam?.toLongOrNull()
    }
}
