package com.proj.Musicality.api

import android.util.Log
import com.proj.Musicality.cache.AppCache
import com.proj.Musicality.data.model.ArtistDetails
import com.proj.Musicality.data.model.RelatedArtist
import com.proj.Musicality.data.parser.ArtistParser
import com.proj.Musicality.data.model.RelatedFeed
import com.proj.Musicality.data.parser.NextParser
import com.proj.Musicality.data.parser.RelatedParser
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.data.model.withMissingMetadataFrom

object RelatedRequestResolver {
    private const val TAG = "RelatedRequestResolver"

    suspend fun fetchRelatedFeed(
        videoId: String,
        primaryArtistName: String? = null,
        primaryArtistId: String? = null
    ): RelatedFeed? {
        val initialVisitorId = VisitorManager.ensureBrowseVisitorId()
        if (initialVisitorId.isBlank()) return null

        val firstAttempt = runCatching {
            fetchWithVisitor(videoId, initialVisitorId, primaryArtistName, primaryArtistId)
        }.onFailure {
            Log.e(TAG, "fetchRelatedFeed: request failed for '$videoId'", it)
        }.getOrNull()
        if (firstAttempt != null) {
            return firstAttempt
        }

        val refreshedVisitorId = VisitorManager.refreshBrowseVisitorId()
        if (refreshedVisitorId.isBlank() || refreshedVisitorId == initialVisitorId) return null
        return runCatching {
            fetchWithVisitor(videoId, refreshedVisitorId, primaryArtistName, primaryArtistId)
        }.onFailure {
            Log.e(TAG, "fetchRelatedFeed: retry failed for '$videoId'", it)
        }.getOrNull()
    }

    private suspend fun fetchWithVisitor(
        videoId: String,
        visitorId: String,
        artistName: String?,
        artistId: String?
    ): RelatedFeed? {
        val nextJson = RequestExecutor.executeNextRequest(videoId, visitorId)
        val relatedBrowseId = NextParser.extractRelatedBrowseId(nextJson)
            ?: return null
        val relatedJson = RequestExecutor.executeRelatedBrowseRequest(relatedBrowseId, visitorId)
        val seed = MediaItem(videoId, "", artistName.orEmpty(), artistId, null, null, null, null, null)
        val details = NextParser.extractUpNextQueue(nextJson)?.items?.firstOrNull { it.videoId == videoId }
        val artist = details?.let { seed.withMissingMetadataFrom(it) } ?: seed
        return RelatedParser.extractFeed(relatedJson)?.withPrimaryArtist(artist.artistName, artist.artistId)
    }

    private suspend fun RelatedFeed.withPrimaryArtist(
        artistName: String?,
        artistId: String?
    ): RelatedFeed {
        if (artistName.isNullOrBlank()) return this
        val resolved = resolveArtist(artistName, artistId)
        return copy(artist = resolved)
    }

    private suspend fun resolveArtist(name: String, artistId: String?): RelatedArtist {
        val details = artistId
            ?.takeIf { it.isNotBlank() }
            ?.let { id ->
                val cached = AppCache.browse.get(id) as? ArtistDetails
                if (cached != null) {
                    cached
                } else {
                    runCatching {
                        val json = VisitorManager.executeBrowseRequestWithRecovery(id)
                        ArtistParser.extractArtistDetails(json)?.also { parsed ->
                            AppCache.browse.put(id, parsed)
                        }
                    }.getOrNull()
                }
            }

        return RelatedArtist(
            name = name,
            artistId = artistId,
            thumbnailUrl = details?.thumbnails?.maxByOrNull { it.width }?.url
        )
    }
}
