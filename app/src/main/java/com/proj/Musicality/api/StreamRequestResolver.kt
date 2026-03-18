package com.proj.Musicality.api

import android.util.Log
import com.proj.Musicality.data.model.SongPlaybackDetails
import com.proj.Musicality.data.parser.StreamParser

object StreamRequestResolver {
    private const val TAG = "StreamRequestResolver"

    suspend fun fetchSongPlaybackDetails(videoId: String): SongPlaybackDetails? {
        val streamVisitorId = VisitorManager.ensureStreamVisitorId()
        if (streamVisitorId.isBlank()) {
            Log.e(TAG, "fetchSongPlaybackDetails: stream visitor ID unavailable")
            return null
        }

        val reelDetails = runCatching {
            val json = RequestExecutor.executeReelRequest(videoId, streamVisitorId)
            StreamParser.extractSongDetails(json)
        }.onFailure { throwable ->
            Log.e(TAG, "fetchSongPlaybackDetails: reel request failed for '$videoId'", throwable)
        }.getOrNull()

        if (!reelDetails?.streamUrl.isNullOrBlank()) {
            return reelDetails
        }

        // If reel returned no playable URL, refresh stream visitor ID once and retry reel.
        val refreshedStreamVisitorId = VisitorManager.refreshStreamVisitorId()
        if (refreshedStreamVisitorId.isNotBlank() && refreshedStreamVisitorId != streamVisitorId) {
            val refreshedReelDetails = runCatching {
                val json = RequestExecutor.executeReelRequest(videoId, refreshedStreamVisitorId)
                StreamParser.extractSongDetails(json)
            }.onFailure { throwable ->
                Log.e(TAG, "fetchSongPlaybackDetails: reel retry failed for '$videoId'", throwable)
            }.getOrNull()
            if (!refreshedReelDetails?.streamUrl.isNullOrBlank()) {
                return refreshedReelDetails
            }
        }

        Log.d(TAG, "fetchSongPlaybackDetails: reel stream URL missing, trying VR player fallback for '$videoId'")

        val browseVisitorId = resolveBrowseVisitorId()
        if (browseVisitorId.isBlank()) {
            Log.e(TAG, "fetchSongPlaybackDetails: browse visitor ID unavailable for fallback")
            return reelDetails
        }

        return runCatching {
            val fallbackJson = RequestExecutor.executeVrPlayerRequest(videoId, browseVisitorId)
            StreamParser.extractSongDetails(fallbackJson)
        }.onFailure { throwable ->
            Log.e(TAG, "fetchSongPlaybackDetails: fallback request failed for '$videoId'", throwable)
        }.getOrDefault(reelDetails)
    }

    private suspend fun resolveBrowseVisitorId(): String {
        return VisitorManager.ensureBrowseVisitorId()
    }
}
