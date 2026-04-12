package com.proj.Musicality.data.local

import android.content.Context
import com.proj.Musicality.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ListeningHistoryRepository private constructor(
    context: Context
) {
    private val db = SQLiteDatabaseHelper(context.applicationContext)
    private val writeMutex = Mutex()

    suspend fun recordPlayback(item: MediaItem) = withContext(Dispatchers.IO) {
        if (item.videoId.isBlank()) return@withContext
        val safeArtistId = item.artistId?.trim()?.takeIf { it.isNotBlank() } ?: return@withContext
        val safeArtistName = item.artistName
            .substringBefore(',')
            .trim()
            .ifBlank { UNKNOWN_ARTIST_NAME }
        val now = System.currentTimeMillis()
        writeMutex.withLock {
            db.insertListeningEvent(
                ListeningEventDbRecord(
                    id = 0L,
                    videoId = item.videoId,
                    title = item.title,
                    artistName = safeArtistName,
                    artistId = safeArtistId,
                    thumbnailUrl = item.thumbnailUrl,
                    playedAt = now
                )
            )
            db.incrementSongPlayCount(
                videoId = item.videoId,
                title = item.title,
                artistName = safeArtistName,
                artistId = safeArtistId,
                thumbnailUrl = item.thumbnailUrl,
                playedAt = now
            )
            db.incrementArtistPlayCount(
                artistId = safeArtistId,
                artistName = safeArtistName,
                playedAt = now
            )
            db.pruneListeningHistory(MAX_HISTORY_ROWS)
            db.pruneSongPlayCounts(MAX_TOP_SONGS_QUERY)
        }
    }

    suspend fun getSnapshot(): ListeningHistorySnapshot = withContext(Dispatchers.IO) {
        // Serialize snapshot reads with writes so tiering decisions don't see partial history.
        writeMutex.withLock {
            db.purgeRowsMissingArtistId()
            db.normalizeMissingArtistNames(UNKNOWN_ARTIST_NAME)
            ListeningHistorySnapshot(
                recentlyPlayed = db.getRecentlyPlayed(MAX_RECENT_QUERY),
                topSongs = db.getTopPlayedSongs(MAX_TOP_SONGS_QUERY),
                topArtists = db.getTopPlayedArtists(MAX_TOP_ARTISTS_QUERY),
                distinctSongCount = db.getDistinctPlayedSongCount()
            )
        }
    }

    companion object {
        private const val MAX_HISTORY_ROWS = 50
        private const val MAX_RECENT_QUERY = 50
        private const val MAX_TOP_SONGS_QUERY = 50
        private const val MAX_TOP_ARTISTS_QUERY = 25
        private const val UNKNOWN_ARTIST_NAME = "Unknown Artist"

        @Volatile
        private var INSTANCE: ListeningHistoryRepository? = null

        fun getInstance(context: Context): ListeningHistoryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ListeningHistoryRepository(context).also { INSTANCE = it }
            }
        }
    }
}

data class ListeningHistorySnapshot(
    val recentlyPlayed: List<ListeningEventDbRecord>,
    val topSongs: List<SongPlayCountDbRecord>,
    val topArtists: List<ArtistPlayCountDbRecord>,
    val distinctSongCount: Int
)
