package com.proj.Musicality.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class SongDbRecord(
    val videoId: String,
    val title: String,
    val artistName: String,
    val artistId: String?,
    val albumName: String?,
    val albumId: String?,
    val thumbnailUrl: String?,
    val thumbnailPath: String?,
    val durationText: String?,
    val filePath: String?,
    val isLiked: Boolean,
    val isDownloaded: Boolean,
    val dateAdded: Long
)

data class VideoDbRecord(
    val videoId: String,
    val title: String,
    val artistName: String,
    val artistId: String?,
    val thumbnailUrl: String?,
    val thumbnailPath: String?,
    val durationText: String?,
    val filePath: String?,
    val isLiked: Boolean,
    val isDownloaded: Boolean,
    val dateAdded: Long
)

data class ArtistDbRecord(
    val artistId: String,
    val name: String,
    val thumbnailUrl: String?,
    val thumbnailPath: String?,
    val isLiked: Boolean,
    val isDownloaded: Boolean,
    val dateAdded: Long
)

data class AlbumDbRecord(
    val albumId: String,
    val title: String,
    val artistName: String?,
    val year: String?,
    val thumbnailUrl: String?,
    val thumbnailPath: String?,
    val isLiked: Boolean,
    val isDownloaded: Boolean,
    val dateAdded: Long
)

data class PlaylistDbRecord(
    val playlistId: String,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val thumbnailPath: String?,
    val isLiked: Boolean,
    val isDownloaded: Boolean,
    val dateAdded: Long
)

data class ListeningEventDbRecord(
    val id: Long,
    val videoId: String,
    val title: String,
    val artistName: String,
    val artistId: String?,
    val thumbnailUrl: String?,
    val playedAt: Long
)

data class SongPlayCountDbRecord(
    val videoId: String,
    val title: String,
    val artistName: String,
    val artistId: String?,
    val thumbnailUrl: String?,
    val playCount: Int,
    val lastPlayedAt: Long
)

data class ArtistPlayCountDbRecord(
    val artistId: String,
    val artistName: String,
    val playCount: Int,
    val lastPlayedAt: Long
)

class SQLiteDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        createLibraryTables(db)
        createListeningHistoryTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createListeningHistoryTables(db)
        }
        if (oldVersion < 3) {
            migrateListeningHistoryTablesToRequireArtistId(db)
        }
    }

    fun getSong(videoId: String): SongDbRecord? = readableDatabase.query(
        "songs",
        null,
        "video_id = ?",
        arrayOf(videoId),
        null,
        null,
        null
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.toSongRecord() else null
    }

    fun getVideo(videoId: String): VideoDbRecord? = readableDatabase.query(
        "videos",
        null,
        "video_id = ?",
        arrayOf(videoId),
        null,
        null,
        null
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.toVideoRecord() else null
    }

    fun getArtist(artistId: String): ArtistDbRecord? = readableDatabase.query(
        "artists",
        null,
        "artist_id = ?",
        arrayOf(artistId),
        null,
        null,
        null
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.toArtistRecord() else null
    }

    fun getAlbum(albumId: String): AlbumDbRecord? = readableDatabase.query(
        "albums",
        null,
        "album_id = ?",
        arrayOf(albumId),
        null,
        null,
        null
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.toAlbumRecord() else null
    }

    fun getPlaylist(playlistId: String): PlaylistDbRecord? = readableDatabase.query(
        "playlists",
        null,
        "playlist_id = ?",
        arrayOf(playlistId),
        null,
        null,
        null
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.toPlaylistRecord() else null
    }

    fun upsertSong(record: SongDbRecord) {
        writableDatabase.insertWithOnConflict(
            "songs",
            null,
            ContentValues().apply {
                put("video_id", record.videoId)
                put("title", record.title)
                put("artist_name", record.artistName)
                put("artist_id", record.artistId)
                put("album_name", record.albumName)
                put("album_id", record.albumId)
                put("thumbnail_url", record.thumbnailUrl)
                put("thumbnail_path", record.thumbnailPath)
                put("duration_text", record.durationText)
                put("file_path", record.filePath)
                put("is_liked", record.isLiked.toInt())
                put("is_downloaded", record.isDownloaded.toInt())
                put("date_added", record.dateAdded)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun upsertVideo(record: VideoDbRecord) {
        writableDatabase.insertWithOnConflict(
            "videos",
            null,
            ContentValues().apply {
                put("video_id", record.videoId)
                put("title", record.title)
                put("artist_name", record.artistName)
                put("artist_id", record.artistId)
                put("thumbnail_url", record.thumbnailUrl)
                put("thumbnail_path", record.thumbnailPath)
                put("duration_text", record.durationText)
                put("file_path", record.filePath)
                put("is_liked", record.isLiked.toInt())
                put("is_downloaded", record.isDownloaded.toInt())
                put("date_added", record.dateAdded)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun upsertArtist(record: ArtistDbRecord) {
        writableDatabase.insertWithOnConflict(
            "artists",
            null,
            ContentValues().apply {
                put("artist_id", record.artistId)
                put("name", record.name)
                put("thumbnail_url", record.thumbnailUrl)
                put("thumbnail_path", record.thumbnailPath)
                put("is_liked", record.isLiked.toInt())
                put("is_downloaded", record.isDownloaded.toInt())
                put("date_added", record.dateAdded)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun upsertAlbum(record: AlbumDbRecord) {
        writableDatabase.insertWithOnConflict(
            "albums",
            null,
            ContentValues().apply {
                put("album_id", record.albumId)
                put("title", record.title)
                put("artist_name", record.artistName)
                put("year", record.year)
                put("thumbnail_url", record.thumbnailUrl)
                put("thumbnail_path", record.thumbnailPath)
                put("is_liked", record.isLiked.toInt())
                put("is_downloaded", record.isDownloaded.toInt())
                put("date_added", record.dateAdded)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun upsertPlaylist(record: PlaylistDbRecord) {
        writableDatabase.insertWithOnConflict(
            "playlists",
            null,
            ContentValues().apply {
                put("playlist_id", record.playlistId)
                put("title", record.title)
                put("author", record.author)
                put("thumbnail_url", record.thumbnailUrl)
                put("thumbnail_path", record.thumbnailPath)
                put("is_liked", record.isLiked.toInt())
                put("is_downloaded", record.isDownloaded.toInt())
                put("date_added", record.dateAdded)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun deleteArtist(artistId: String): Int {
        return writableDatabase.delete(
            "artists",
            "artist_id = ?",
            arrayOf(artistId)
        )
    }

    fun deleteAlbum(albumId: String): Int {
        return writableDatabase.delete(
            "albums",
            "album_id = ?",
            arrayOf(albumId)
        )
    }

    fun deletePlaylist(playlistId: String): Int {
        return writableDatabase.delete(
            "playlists",
            "playlist_id = ?",
            arrayOf(playlistId)
        )
    }

    fun getLikedSongs(): List<SongDbRecord> = readableDatabase.query(
        "songs",
        null,
        "is_liked = 1",
        null,
        null,
        null,
        "date_added DESC"
    ).use { cursor -> cursor.readAllSongs() }

    fun getDownloadedSongs(): List<SongDbRecord> = readableDatabase.query(
        "songs",
        null,
        "is_downloaded = 1",
        null,
        null,
        null,
        "date_added DESC"
    ).use { cursor -> cursor.readAllSongs() }

    fun getDownloadedVideos(): List<VideoDbRecord> = readableDatabase.query(
        "videos",
        null,
        "is_downloaded = 1",
        null,
        null,
        null,
        "date_added DESC"
    ).use { cursor -> cursor.readAllVideos() }

    fun getArtists(): List<ArtistDbRecord> = readableDatabase.query(
        "artists",
        null,
        null,
        null,
        null,
        null,
        "date_added DESC"
    ).use { cursor -> cursor.readAllArtists() }

    fun getAlbums(): List<AlbumDbRecord> = readableDatabase.query(
        "albums",
        null,
        null,
        null,
        null,
        null,
        "date_added DESC"
    ).use { cursor -> cursor.readAllAlbums() }

    fun getPlaylists(): List<PlaylistDbRecord> = readableDatabase.query(
        "playlists",
        null,
        null,
        null,
        null,
        null,
        "date_added DESC"
    ).use { cursor -> cursor.readAllPlaylists() }

    fun getMediaState(videoId: String): Pair<Boolean, Boolean> {
        val song = getSong(videoId)
        if (song != null) return song.isLiked to song.isDownloaded

        val video = getVideo(videoId)
        if (video != null) return video.isLiked to video.isDownloaded

        return false to false
    }

    fun insertListeningEvent(record: ListeningEventDbRecord) {
        writableDatabase.insert(
            "listening_history",
            null,
            ContentValues().apply {
                put("video_id", record.videoId)
                put("title", record.title)
                put("artist_name", record.artistName)
                put("artist_id", record.artistId)
                put("thumbnail_url", record.thumbnailUrl)
                put("played_at", record.playedAt)
            }
        )
    }

    fun incrementSongPlayCount(
        videoId: String,
        title: String,
        artistName: String,
        artistId: String?,
        thumbnailUrl: String?,
        playedAt: Long
    ) {
        writableDatabase.execSQL(
            """
            INSERT INTO song_play_counts (
                video_id,
                title,
                artist_name,
                artist_id,
                thumbnail_url,
                play_count,
                last_played_at
            ) VALUES (?, ?, ?, ?, ?, 1, ?)
            ON CONFLICT(video_id) DO UPDATE SET
                title = excluded.title,
                artist_name = excluded.artist_name,
                artist_id = excluded.artist_id,
                thumbnail_url = COALESCE(excluded.thumbnail_url, song_play_counts.thumbnail_url),
                play_count = song_play_counts.play_count + 1,
                last_played_at = excluded.last_played_at
            """.trimIndent(),
            arrayOf(videoId, title, artistName, artistId, thumbnailUrl, playedAt)
        )
    }

    fun incrementArtistPlayCount(
        artistId: String,
        artistName: String,
        playedAt: Long
    ) {
        writableDatabase.execSQL(
            """
            INSERT INTO artist_play_counts (
                artist_id,
                artist_name,
                play_count,
                last_played_at
            ) VALUES (?, ?, 1, ?)
            ON CONFLICT(artist_id) DO UPDATE SET
                artist_name = excluded.artist_name,
                play_count = artist_play_counts.play_count + 1,
                last_played_at = excluded.last_played_at
            """.trimIndent(),
            arrayOf(artistId, artistName, playedAt)
        )
    }

    fun getRecentlyPlayed(limit: Int): List<ListeningEventDbRecord> = readableDatabase.query(
        "listening_history",
        null,
        null,
        null,
        null,
        null,
        "played_at DESC",
        limit.coerceAtLeast(1).toString()
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toListeningEventRecord())
        }
    }

    fun getTopPlayedSongs(limit: Int): List<SongPlayCountDbRecord> = readableDatabase.query(
        "song_play_counts",
        null,
        null,
        null,
        null,
        null,
        "play_count DESC, last_played_at DESC",
        limit.coerceAtLeast(1).toString()
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toSongPlayCountRecord())
        }
    }

    fun getTopPlayedArtists(limit: Int): List<ArtistPlayCountDbRecord> = readableDatabase.query(
        "artist_play_counts",
        null,
        null,
        null,
        null,
        null,
        "play_count DESC, last_played_at DESC",
        limit.coerceAtLeast(1).toString()
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toArtistPlayCountRecord())
        }
    }

    fun getDistinctPlayedSongCount(): Int = readableDatabase.rawQuery(
        "SELECT COUNT(DISTINCT video_id) FROM listening_history",
        null
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }

    fun normalizeMissingArtistNames(fallbackArtistName: String) {
        writableDatabase.execSQL(
            """
            UPDATE listening_history
            SET artist_name = ?
            WHERE trim(COALESCE(artist_name, '')) = ''
            """.trimIndent(),
            arrayOf(fallbackArtistName)
        )
        writableDatabase.execSQL(
            """
            UPDATE song_play_counts
            SET artist_name = ?
            WHERE trim(COALESCE(artist_name, '')) = ''
            """.trimIndent(),
            arrayOf(fallbackArtistName)
        )
        writableDatabase.execSQL(
            """
            UPDATE listening_history
            SET artist_name = trim(substr(artist_name, 1, instr(artist_name || ',', ',') - 1))
            WHERE instr(artist_name, ',') > 0
            """.trimIndent()
        )
        writableDatabase.execSQL(
            """
            UPDATE song_play_counts
            SET artist_name = trim(substr(artist_name, 1, instr(artist_name || ',', ',') - 1))
            WHERE instr(artist_name, ',') > 0
            """.trimIndent()
        )
        writableDatabase.execSQL(
            """
            UPDATE artist_play_counts
            SET artist_name = ?
            WHERE trim(COALESCE(artist_name, '')) = ''
            """.trimIndent(),
            arrayOf(fallbackArtistName)
        )
    }

    fun purgeRowsMissingArtistId() {
        writableDatabase.execSQL(
            """
            DELETE FROM listening_history
            WHERE trim(COALESCE(artist_id, '')) = ''
            """.trimIndent()
        )
        writableDatabase.execSQL(
            """
            DELETE FROM song_play_counts
            WHERE trim(COALESCE(artist_id, '')) = ''
            """.trimIndent()
        )
    }

    fun pruneSongPlayCounts(keepTop: Int) {
        val safeKeep = keepTop.coerceAtLeast(1)
        writableDatabase.execSQL(
            """
            DELETE FROM song_play_counts
            WHERE video_id IN (
                SELECT video_id FROM song_play_counts
                ORDER BY play_count DESC, last_played_at DESC
                LIMIT -1 OFFSET ?
            )
            """.trimIndent(),
            arrayOf(safeKeep)
        )
    }

    fun pruneListeningHistory(keepLatest: Int) {
        val safeKeep = keepLatest.coerceAtLeast(1)
        writableDatabase.execSQL(
            """
            DELETE FROM listening_history
            WHERE id IN (
                SELECT id FROM listening_history
                ORDER BY played_at DESC
                LIMIT -1 OFFSET ?
            )
            """.trimIndent(),
            arrayOf(safeKeep)
        )
    }

    private fun createLibraryTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS songs (
                video_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                artist_name TEXT NOT NULL,
                artist_id TEXT,
                album_name TEXT,
                album_id TEXT,
                thumbnail_url TEXT,
                thumbnail_path TEXT,
                duration_text TEXT,
                file_path TEXT,
                is_liked INTEGER NOT NULL DEFAULT 0,
                is_downloaded INTEGER NOT NULL DEFAULT 0,
                date_added INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS videos (
                video_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                artist_name TEXT NOT NULL,
                artist_id TEXT,
                thumbnail_url TEXT,
                thumbnail_path TEXT,
                duration_text TEXT,
                file_path TEXT,
                is_liked INTEGER NOT NULL DEFAULT 0,
                is_downloaded INTEGER NOT NULL DEFAULT 0,
                date_added INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS artists (
                artist_id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                thumbnail_url TEXT,
                thumbnail_path TEXT,
                is_liked INTEGER NOT NULL DEFAULT 0,
                is_downloaded INTEGER NOT NULL DEFAULT 0,
                date_added INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS albums (
                album_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                artist_name TEXT,
                year TEXT,
                thumbnail_url TEXT,
                thumbnail_path TEXT,
                is_liked INTEGER NOT NULL DEFAULT 0,
                is_downloaded INTEGER NOT NULL DEFAULT 0,
                date_added INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlists (
                playlist_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                author TEXT,
                thumbnail_url TEXT,
                thumbnail_path TEXT,
                is_liked INTEGER NOT NULL DEFAULT 0,
                is_downloaded INTEGER NOT NULL DEFAULT 0,
                date_added INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_songs_date_added ON songs(date_added)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_videos_date_added ON videos(date_added)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_artists_date_added ON artists(date_added)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_albums_date_added ON albums(date_added)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_playlists_date_added ON playlists(date_added)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_songs_liked ON songs(is_liked)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_songs_downloaded ON songs(is_downloaded)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_videos_downloaded ON videos(is_downloaded)")
    }

    private fun createListeningHistoryTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS listening_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                video_id TEXT NOT NULL,
                title TEXT NOT NULL,
                artist_name TEXT NOT NULL,
                artist_id TEXT NOT NULL,
                thumbnail_url TEXT,
                played_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS song_play_counts (
                video_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                artist_name TEXT NOT NULL,
                artist_id TEXT NOT NULL,
                thumbnail_url TEXT,
                play_count INTEGER NOT NULL DEFAULT 0,
                last_played_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS artist_play_counts (
                artist_id TEXT PRIMARY KEY,
                artist_name TEXT NOT NULL,
                play_count INTEGER NOT NULL DEFAULT 0,
                last_played_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_listening_history_played_at ON listening_history(played_at DESC)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_listening_history_video_id ON listening_history(video_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_song_play_counts_play_count ON song_play_counts(play_count DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_artist_play_counts_play_count ON artist_play_counts(play_count DESC)")
    }

    private fun migrateListeningHistoryTablesToRequireArtistId(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS listening_history_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                video_id TEXT NOT NULL,
                title TEXT NOT NULL,
                artist_name TEXT NOT NULL,
                artist_id TEXT NOT NULL,
                thumbnail_url TEXT,
                played_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO listening_history_new (video_id, title, artist_name, artist_id, thumbnail_url, played_at)
            SELECT
                video_id,
                title,
                trim(substr(COALESCE(artist_name, ''), 1, instr(COALESCE(artist_name, '') || ',', ',') - 1)),
                trim(artist_id),
                thumbnail_url,
                played_at
            FROM listening_history
            WHERE trim(COALESCE(artist_id, '')) <> ''
            """.trimIndent()
        )
        db.execSQL("DROP TABLE IF EXISTS listening_history")
        db.execSQL("ALTER TABLE listening_history_new RENAME TO listening_history")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS song_play_counts_new (
                video_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                artist_name TEXT NOT NULL,
                artist_id TEXT NOT NULL,
                thumbnail_url TEXT,
                play_count INTEGER NOT NULL DEFAULT 0,
                last_played_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO song_play_counts_new (video_id, title, artist_name, artist_id, thumbnail_url, play_count, last_played_at)
            SELECT
                video_id,
                title,
                trim(substr(COALESCE(artist_name, ''), 1, instr(COALESCE(artist_name, '') || ',', ',') - 1)),
                trim(artist_id),
                thumbnail_url,
                play_count,
                last_played_at
            FROM song_play_counts
            WHERE trim(COALESCE(artist_id, '')) <> ''
            """.trimIndent()
        )
        db.execSQL("DROP TABLE IF EXISTS song_play_counts")
        db.execSQL("ALTER TABLE song_play_counts_new RENAME TO song_play_counts")

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_listening_history_played_at ON listening_history(played_at DESC)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_listening_history_video_id ON listening_history(video_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_song_play_counts_play_count ON song_play_counts(play_count DESC)")
    }

    private fun Cursor.readAllSongs(): List<SongDbRecord> = buildList {
        while (moveToNext()) add(toSongRecord())
    }

    private fun Cursor.readAllVideos(): List<VideoDbRecord> = buildList {
        while (moveToNext()) add(toVideoRecord())
    }

    private fun Cursor.readAllArtists(): List<ArtistDbRecord> = buildList {
        while (moveToNext()) add(toArtistRecord())
    }

    private fun Cursor.readAllAlbums(): List<AlbumDbRecord> = buildList {
        while (moveToNext()) add(toAlbumRecord())
    }

    private fun Cursor.readAllPlaylists(): List<PlaylistDbRecord> = buildList {
        while (moveToNext()) add(toPlaylistRecord())
    }

    private fun Cursor.toSongRecord(): SongDbRecord = SongDbRecord(
        videoId = getStringOrEmpty("video_id"),
        title = getStringOrEmpty("title"),
        artistName = getStringOrEmpty("artist_name"),
        artistId = getStringOrNull("artist_id"),
        albumName = getStringOrNull("album_name"),
        albumId = getStringOrNull("album_id"),
        thumbnailUrl = getStringOrNull("thumbnail_url"),
        thumbnailPath = getStringOrNull("thumbnail_path"),
        durationText = getStringOrNull("duration_text"),
        filePath = getStringOrNull("file_path"),
        isLiked = getIntOrZero("is_liked") == 1,
        isDownloaded = getIntOrZero("is_downloaded") == 1,
        dateAdded = getLongOrZero("date_added")
    )

    private fun Cursor.toVideoRecord(): VideoDbRecord = VideoDbRecord(
        videoId = getStringOrEmpty("video_id"),
        title = getStringOrEmpty("title"),
        artistName = getStringOrEmpty("artist_name"),
        artistId = getStringOrNull("artist_id"),
        thumbnailUrl = getStringOrNull("thumbnail_url"),
        thumbnailPath = getStringOrNull("thumbnail_path"),
        durationText = getStringOrNull("duration_text"),
        filePath = getStringOrNull("file_path"),
        isLiked = getIntOrZero("is_liked") == 1,
        isDownloaded = getIntOrZero("is_downloaded") == 1,
        dateAdded = getLongOrZero("date_added")
    )

    private fun Cursor.toArtistRecord(): ArtistDbRecord = ArtistDbRecord(
        artistId = getStringOrEmpty("artist_id"),
        name = getStringOrEmpty("name"),
        thumbnailUrl = getStringOrNull("thumbnail_url"),
        thumbnailPath = getStringOrNull("thumbnail_path"),
        isLiked = getIntOrZero("is_liked") == 1,
        isDownloaded = getIntOrZero("is_downloaded") == 1,
        dateAdded = getLongOrZero("date_added")
    )

    private fun Cursor.toAlbumRecord(): AlbumDbRecord = AlbumDbRecord(
        albumId = getStringOrEmpty("album_id"),
        title = getStringOrEmpty("title"),
        artistName = getStringOrNull("artist_name"),
        year = getStringOrNull("year"),
        thumbnailUrl = getStringOrNull("thumbnail_url"),
        thumbnailPath = getStringOrNull("thumbnail_path"),
        isLiked = getIntOrZero("is_liked") == 1,
        isDownloaded = getIntOrZero("is_downloaded") == 1,
        dateAdded = getLongOrZero("date_added")
    )

    private fun Cursor.toPlaylistRecord(): PlaylistDbRecord = PlaylistDbRecord(
        playlistId = getStringOrEmpty("playlist_id"),
        title = getStringOrEmpty("title"),
        author = getStringOrNull("author"),
        thumbnailUrl = getStringOrNull("thumbnail_url"),
        thumbnailPath = getStringOrNull("thumbnail_path"),
        isLiked = getIntOrZero("is_liked") == 1,
        isDownloaded = getIntOrZero("is_downloaded") == 1,
        dateAdded = getLongOrZero("date_added")
    )

    private fun Cursor.toListeningEventRecord(): ListeningEventDbRecord = ListeningEventDbRecord(
        id = getLongOrZero("id"),
        videoId = getStringOrEmpty("video_id"),
        title = getStringOrEmpty("title"),
        artistName = getStringOrEmpty("artist_name"),
        artistId = getStringOrNull("artist_id"),
        thumbnailUrl = getStringOrNull("thumbnail_url"),
        playedAt = getLongOrZero("played_at")
    )

    private fun Cursor.toSongPlayCountRecord(): SongPlayCountDbRecord = SongPlayCountDbRecord(
        videoId = getStringOrEmpty("video_id"),
        title = getStringOrEmpty("title"),
        artistName = getStringOrEmpty("artist_name"),
        artistId = getStringOrNull("artist_id"),
        thumbnailUrl = getStringOrNull("thumbnail_url"),
        playCount = getIntOrZero("play_count"),
        lastPlayedAt = getLongOrZero("last_played_at")
    )

    private fun Cursor.toArtistPlayCountRecord(): ArtistPlayCountDbRecord = ArtistPlayCountDbRecord(
        artistId = getStringOrEmpty("artist_id"),
        artistName = getStringOrEmpty("artist_name"),
        playCount = getIntOrZero("play_count"),
        lastPlayedAt = getLongOrZero("last_played_at")
    )

    private fun Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndex(column)
        if (index < 0 || isNull(index)) return null
        return getString(index)
    }

    private fun Cursor.getStringOrEmpty(column: String): String = getStringOrNull(column).orEmpty()

    private fun Cursor.getIntOrZero(column: String): Int {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) 0 else getInt(index)
    }

    private fun Cursor.getLongOrZero(column: String): Long {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) 0L else getLong(index)
    }

    private fun Boolean.toInt(): Int = if (this) 1 else 0

    companion object {
        private const val DATABASE_NAME = "musicality_library.db"
        private const val DATABASE_VERSION = 3
    }
}
