package com.proj.Musicality.data.parser

import android.net.Uri
import android.util.Log
import com.proj.Musicality.data.json.PlayerResponse
import com.proj.Musicality.data.json.StreamResponse
import com.proj.Musicality.data.model.SongPlaybackDetails

object StreamParser {
    private const val TAG = "StreamParser"
    private val preferredAudioItags = listOf(251, 140, 250, 249, 139)
    private val audioItags = preferredAudioItags.toSet()

    fun extractSongDetails(jsonResponse: String): SongPlaybackDetails {
        Log.d(TAG, "extractSongDetails: parsing response (${jsonResponse.length} chars)")

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

        // Do not retry a response that explicitly marks the video as unplayable.
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
        Log.d(TAG, "extractSongDetails: ${formats.size} formats found")
        formats.forEach { fmt ->
            Log.d(TAG, "  format: itag=${fmt.itag}, mime=${fmt.mimeType ?: "unknown"}, hasUrl=${!fmt.url.isNullOrBlank()}")
        }

        val directAudioFormats = formats.filter { format ->
            !format.url.isNullOrBlank() &&
                (format.mimeType?.startsWith("audio/") == true || format.itag in audioItags)
        }
        val bestFormat = preferredAudioItags.asSequence()
            .mapNotNull { itag -> directAudioFormats.firstOrNull { it.itag == itag } }
            .firstOrNull()
            ?: directAudioFormats.maxByOrNull { it.itag }

        if (bestFormat != null) {
            Log.d(TAG, "extractSongDetails: selected itag=${bestFormat.itag}, mime=${bestFormat.mimeType ?: "unknown"}")
        } else {
            Log.e(TAG, "extractSongDetails: no direct audio format found")
            Log.e(TAG, "extractSongDetails: available formats: ${formats.map { "${it.itag}:${it.mimeType ?: "unknown"}:${!it.url.isNullOrBlank()}" }}")
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
