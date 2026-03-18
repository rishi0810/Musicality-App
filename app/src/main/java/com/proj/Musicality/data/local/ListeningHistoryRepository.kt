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
        val now = System.currentTimeMillis()
        writeMutex.withLock {
            db.insertListeningEvent(
                ListeningEventDbRecord(
                    id = 0L,
                    videoId = item.videoId,
                    title = item.title,
                    artistName = item.artistName,
                    artistId = item.artistId,
                    thumbnailUrl = item.thumbnailUrl,
                    playedAt = now
                )
            )
            db.incrementSongPlayCount(
                videoId = item.videoId,
                title = item.title,
                artistName = item.artistName,
                artistId = item.artistId,
                thumbnailUrl = item.thumbnailUrl,
                playedAt = now
            )
            val safeArtistId = item.artistId?.takeIf { it.isNotBlank() }
            if (safeArtistId != null) {
                db.incrementArtistPlayCount(
                    artistId = safeArtistId,
                    artistName = item.artistName.ifBlank { "Unknown Artist" },
                    playedAt = now
                )
            }
            db.pruneListeningHistory(MAX_HISTORY_ROWS)
        }
    }

    suspend fun getSnapshot(): ListeningHistorySnapshot = withContext(Dispatchers.IO) {
        // Serialize snapshot reads with writes so tiering decisions don't see partial history.
        writeMutex.withLock {
            ListeningHistorySnapshot(
                recentlyPlayed = db.getRecentlyPlayed(MAX_RECENT_QUERY),
                topSongs = db.getTopPlayedSongs(MAX_TOP_SONGS_QUERY),
                topArtists = db.getTopPlayedArtists(MAX_TOP_ARTISTS_QUERY),
                distinctSongCount = db.getDistinctPlayedSongCount()
            )
        }
    }

    companion object {
        private const val MAX_HISTORY_ROWS = 250
        private const val MAX_RECENT_QUERY = 80
        private const val MAX_TOP_SONGS_QUERY = 40
        private const val MAX_TOP_ARTISTS_QUERY = 25

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
