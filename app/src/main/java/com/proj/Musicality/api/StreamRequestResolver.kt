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

        val firstAttempt = runCatching {
            val json = RequestExecutor.executePlayerRequest(videoId, streamVisitorId)
            StreamParser.extractSongDetails(json)
        }.onFailure { throwable ->
            Log.e(TAG, "fetchSongPlaybackDetails: player request failed for '$videoId'", throwable)
        }.getOrNull()

        if (!firstAttempt?.streamUrl.isNullOrBlank()) {
            return firstAttempt
        }

        val refreshedVisitorId = VisitorManager.refreshStreamVisitorId()
        if (refreshedVisitorId.isBlank() || refreshedVisitorId == streamVisitorId) {
            return firstAttempt
        }

        return runCatching {
            val json = RequestExecutor.executePlayerRequest(videoId, refreshedVisitorId)
            StreamParser.extractSongDetails(json)
        }.onFailure { throwable ->
            Log.e(TAG, "fetchSongPlaybackDetails: player retry failed for '$videoId'", throwable)
        }.getOrDefault(firstAttempt)
    }
}
