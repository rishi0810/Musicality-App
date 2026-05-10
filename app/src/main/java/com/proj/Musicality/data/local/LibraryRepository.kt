package com.proj.Musicality.data.local

import android.content.Context
import android.util.Log
import com.proj.Musicality.api.StreamRequestResolver
import com.proj.Musicality.api.VisitorManager
import com.proj.Musicality.cache.AppCache
import com.proj.Musicality.config.AppConfig
import com.proj.Musicality.data.model.MediaItem
import java.io.File
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class LibraryRepository private constructor(
    context: Context
) {
    private val appContext = context.applicationContext
    private val db = SQLiteDatabaseHelper(appContext)
    private val writeMutex = Mutex()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val audioDir = File(appContext.filesDir, "library_audio").apply { mkdirs() }
    private val thumbDir = File(appContext.filesDir, "library_thumbs").apply { mkdirs() }
    private val playedDir = File(appContext.filesDir, "played_cache").apply { mkdirs() }

    private val _snapshot = MutableStateFlow(LibrarySnapshot())
    val snapshot: StateFlow<LibrarySnapshot> = _snapshot.asStateFlow()
    private val _downloadStates = MutableStateFlow<Map<String, MediaDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, MediaDownloadState>> = _downloadStates.asStateFlow()

    init {
        repositoryScope.launch {
            _snapshot.value = loadSnapshot()
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _snapshot.value = loadSnapshot()
    }

    suspend fun toggleLike(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val now = System.currentTimeMillis()
            val isVideo = item.isVideo()
            val nextLiked = if (isVideo) {
                val existing = db.getVideo(item.videoId)
                val liked = !(existing?.isLiked ?: false)
                db.upsertVideo(
                    VideoDbRecord(
                        videoId = item.videoId,
                        title = item.title,
                        artistName = item.artistName,
                        artistId = item.artistId,
                        thumbnailUrl = item.thumbnailUrl,
                        thumbnailPath = existing?.thumbnailPath ?: persistThumbnail("video_${item.videoId}", item.thumbnailUrl),
                        durationText = item.durationText,
                        filePath = existing?.filePath,
                        isLiked = liked,
                        isDownloaded = existing?.isDownloaded ?: false,
                        dateAdded = if (liked) now else (existing?.dateAdded ?: now)
                    )
                )
                liked
            } else {
                val existing = db.getSong(item.videoId)
                val liked = !(existing?.isLiked ?: false)
                db.upsertSong(
                    SongDbRecord(
                        videoId = item.videoId,
                        title = item.title,
                        artistName = item.artistName,
                        artistId = item.artistId,
                        albumName = item.albumName,
                        albumId = item.albumId,
                        thumbnailUrl = item.thumbnailUrl,
                        thumbnailPath = existing?.thumbnailPath ?: persistThumbnail("song_${item.videoId}", item.thumbnailUrl),
                        durationText = item.durationText,
                        filePath = existing?.filePath,
                        isLiked = liked,
                        isDownloaded = existing?.isDownloaded ?: false,
                        dateAdded = if (liked) now else (existing?.dateAdded ?: now)
                    )
                )
                liked
            }

            _snapshot.value = loadSnapshot()
            nextLiked
        }
    }

    suspend fun download(item: MediaItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            writeMutex.withLock {
                val now = System.currentTimeMillis()

                val playedEntry = db.getPlayedCacheEntry(item.videoId)
                val playedFile = playedEntry?.let { File(it.filePath) }
                    ?.takeIf { it.exists() && it.length() > 0 }

                if (playedFile != null) {
                    Log.d(TAG, "download: reusing played cache file for '${item.videoId}' (${playedFile.length() / 1024}KB)")
                    val thumbnailPath = persistThumbnail("media_${item.videoId}", item.thumbnailUrl)
                    val filePath = playedFile.absolutePath
                    if (item.isVideo()) {
                        val existing = db.getVideo(item.videoId)
                        db.upsertVideo(
                            VideoDbRecord(
                                videoId = item.videoId,
                                title = item.title,
                                artistName = item.artistName,
                                artistId = item.artistId,
                                thumbnailUrl = item.thumbnailUrl,
                                thumbnailPath = thumbnailPath ?: existing?.thumbnailPath,
                                durationText = item.durationText,
                                filePath = filePath,
                                isLiked = existing?.isLiked ?: false,
                                isDownloaded = true,
                                dateAdded = now
                            )
                        )
                    } else {
                        val existing = db.getSong(item.videoId)
                        db.upsertSong(
                            SongDbRecord(
                                videoId = item.videoId,
                                title = item.title,
                                artistName = item.artistName,
                                artistId = item.artistId,
                                albumName = item.albumName,
                                albumId = item.albumId,
                                thumbnailUrl = item.thumbnailUrl,
                                thumbnailPath = thumbnailPath ?: existing?.thumbnailPath,
                                durationText = item.durationText,
                                filePath = filePath,
                                isLiked = existing?.isLiked ?: false,
                                isDownloaded = true,
                                dateAdded = now
                            )
                        )
                    }
                    _snapshot.value = loadSnapshot()
                    setDownloadState(
                        item.videoId,
                        MediaDownloadState(progress = 1f, isDownloading = false, isDownloaded = true)
                    )
                    return@withLock
                }

                setDownloadState(
                    item.videoId,
                    MediaDownloadState(
                        progress = 0f,
                        isDownloading = true,
                        isDownloaded = false
                    )
                )
                val streamUrl = resolveStreamUrl(item.videoId)
                val downloadUrl = streamUrl.withFullRange()
                val request = Request.Builder().url(downloadUrl).build()
                val fileExtension = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Download failed with HTTP ${response.code}")
                    }
                    val body = response.body ?: error("Download body is empty")
                    val ext = extensionFromContentType(response.header("Content-Type"))
                    val outputFile = File(audioDir, "${sanitize(item.videoId)}.$ext")
                    val totalBytes = body.contentLength().takeIf { it > 0L }
                    var downloadedBytes = 0L
                    var lastEmittedProgress = 0f
                    body.byteStream().use { input ->
                        outputFile.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                if (totalBytes != null) {
                                    downloadedBytes += read
                                    val nextProgress = (downloadedBytes.toFloat() / totalBytes.toFloat())
                                        .coerceIn(0f, 1f)
                                    if (
                                        nextProgress >= 1f ||
                                        nextProgress - lastEmittedProgress >= 0.01f
                                    ) {
                                        lastEmittedProgress = nextProgress
                                        setDownloadState(
                                            item.videoId,
                                            MediaDownloadState(
                                                progress = nextProgress,
                                                isDownloading = true,
                                                isDownloaded = false
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    outputFile.extension
                }

                val mediaFilePath = File(audioDir, "${sanitize(item.videoId)}.$fileExtension").absolutePath
                val thumbnailPath = persistThumbnail("media_${item.videoId}", item.thumbnailUrl)
                if (item.isVideo()) {
                    val existing = db.getVideo(item.videoId)
                    db.upsertVideo(
                        VideoDbRecord(
                            videoId = item.videoId,
                            title = item.title,
                            artistName = item.artistName,
                            artistId = item.artistId,
                            thumbnailUrl = item.thumbnailUrl,
                            thumbnailPath = thumbnailPath ?: existing?.thumbnailPath,
                            durationText = item.durationText,
                            filePath = mediaFilePath,
                            isLiked = existing?.isLiked ?: false,
                            isDownloaded = true,
                            dateAdded = now
                        )
                    )
                } else {
                    val existing = db.getSong(item.videoId)
                    db.upsertSong(
                        SongDbRecord(
                            videoId = item.videoId,
                            title = item.title,
                            artistName = item.artistName,
                            artistId = item.artistId,
                            albumName = item.albumName,
                            albumId = item.albumId,
                            thumbnailUrl = item.thumbnailUrl,
                            thumbnailPath = thumbnailPath ?: existing?.thumbnailPath,
                            durationText = item.durationText,
                            filePath = mediaFilePath,
                            isLiked = existing?.isLiked ?: false,
                            isDownloaded = true,
                            dateAdded = now
                        )
                    )
                }

                _snapshot.value = loadSnapshot()
                setDownloadState(
                    item.videoId,
                    MediaDownloadState(
                        progress = 1f,
                        isDownloading = false,
                        isDownloaded = true
                    )
                )
            }
        }.onFailure {
            setDownloadState(item.videoId, null)
        }
    }

    suspend fun rememberArtist(
        artistId: String,
        name: String,
        thumbnailUrl: String?
    ) = withContext(Dispatchers.IO) {
        if (artistId.isBlank()) return@withContext
        writeMutex.withLock {
            val existing = db.getArtist(artistId)
            val now = System.currentTimeMillis()
            db.upsertArtist(
                ArtistDbRecord(
                    artistId = artistId,
                    name = name.ifBlank { "Artist" },
                    thumbnailUrl = thumbnailUrl,
                    thumbnailPath = existing?.thumbnailPath ?: persistThumbnail("artist_$artistId", thumbnailUrl),
                    isLiked = existing?.isLiked ?: false,
                    isDownloaded = existing?.isDownloaded ?: false,
                    dateAdded = existing?.dateAdded ?: now
                )
            )
            _snapshot.value = loadSnapshot()
        }
    }

    suspend fun rememberAlbum(
        albumId: String,
        title: String,
        artistName: String?,
        year: String?,
        thumbnailUrl: String?
    ) = withContext(Dispatchers.IO) {
        if (albumId.isBlank()) return@withContext
        writeMutex.withLock {
            val existing = db.getAlbum(albumId)
            val now = System.currentTimeMillis()
            db.upsertAlbum(
                AlbumDbRecord(
                    albumId = albumId,
                    title = title.ifBlank { "Album" },
                    artistName = artistName,
                    year = year,
                    thumbnailUrl = thumbnailUrl,
                    thumbnailPath = existing?.thumbnailPath ?: persistThumbnail("album_$albumId", thumbnailUrl),
                    isLiked = existing?.isLiked ?: false,
                    isDownloaded = existing?.isDownloaded ?: false,
                    dateAdded = existing?.dateAdded ?: now
                )
            )
            _snapshot.value = loadSnapshot()
        }
    }

    suspend fun rememberPlaylist(
        playlistId: String,
        title: String,
        author: String?,
        thumbnailUrl: String?
    ) = withContext(Dispatchers.IO) {
        if (playlistId.isBlank()) return@withContext
        writeMutex.withLock {
            val existing = db.getPlaylist(playlistId)
            val now = System.currentTimeMillis()
            db.upsertPlaylist(
                PlaylistDbRecord(
                    playlistId = playlistId,
                    title = title.ifBlank { "Playlist" },
                    author = author,
                    thumbnailUrl = thumbnailUrl,
                    thumbnailPath = existing?.thumbnailPath ?: persistThumbnail("playlist_$playlistId", thumbnailUrl),
                    isLiked = existing?.isLiked ?: false,
                    isDownloaded = existing?.isDownloaded ?: false,
                    dateAdded = existing?.dateAdded ?: now
                )
            )
            _snapshot.value = loadSnapshot()
        }
    }

    suspend fun removeSavedEntry(entry: SavedEntry) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            when (entry.type) {
                SavedEntryType.ARTIST -> db.deleteArtist(entry.id)
                SavedEntryType.ALBUM -> db.deleteAlbum(entry.id)
                SavedEntryType.PLAYLIST -> db.deletePlaylist(entry.id)
            }
            _snapshot.value = loadSnapshot()
        }
    }

    suspend fun removeFromCollection(
        collectionType: LibraryCollectionType,
        item: MediaItem
    ) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val song = db.getSong(item.videoId)
            val video = db.getVideo(item.videoId)

            when (collectionType) {
                LibraryCollectionType.LIKED,
                LibraryCollectionType.TOP_SONGS -> {
                    if (song != null) {
                        db.upsertSong(song.copy(isLiked = false))
                    } else if (video != null) {
                        db.upsertVideo(video.copy(isLiked = false))
                    }
                }
                LibraryCollectionType.DOWNLOADED -> {
                    if (song != null) {
                        song.filePath?.let { path ->
                            val playedRef = db.getPlayedCacheEntry(item.videoId)
                            val sharedByPlayed = playedRef != null && playedRef.filePath == path
                            if (!sharedByPlayed) runCatching { File(path).delete() }
                        }
                        db.upsertSong(
                            song.copy(
                                isDownloaded = false,
                                filePath = null
                            )
                        )
                        setDownloadState(song.videoId, null)
                    } else if (video != null) {
                        video.filePath?.let { path ->
                            val playedRef = db.getPlayedCacheEntry(item.videoId)
                            val sharedByPlayed = playedRef != null && playedRef.filePath == path
                            if (!sharedByPlayed) runCatching { File(path).delete() }
                        }
                        db.upsertVideo(
                            video.copy(
                                isDownloaded = false,
                                filePath = null
                            )
                        )
                        setDownloadState(video.videoId, null)
                    }
                }
                LibraryCollectionType.PLAYED -> {
                    val entry = db.getPlayedCacheEntry(item.videoId)
                    if (entry != null) {
                        val dlSong = db.getSong(item.videoId)
                        val dlVideo = db.getVideo(item.videoId)
                        val sharedByDownload =
                            (dlSong?.isDownloaded == true && dlSong.filePath == entry.filePath) ||
                            (dlVideo?.isDownloaded == true && dlVideo.filePath == entry.filePath)
                        if (!sharedByDownload) runCatching { File(entry.filePath).delete() }
                        db.deletePlayedCacheEntry(item.videoId)
                    }
                }
            }

            _snapshot.value = loadSnapshot()
        }
    }

    suspend fun cachePlayedSong(item: MediaItem, sourceFile: File) = withContext(Dispatchers.IO) {
        if (!AppConfig.playedCacheEnabled.value) {
            Log.d(TAG, "PlayedCache: skipped '${item.videoId}' — feature disabled")
            return@withContext
        }
        if (!sourceFile.exists()) {
            Log.w(TAG, "PlayedCache: skipped '${item.videoId}' — source file missing: ${sourceFile.absolutePath}")
            return@withContext
        }

        Log.d(TAG, "PlayedCache: acquiring lock for '${item.videoId}'")
        writeMutex.withLock {
            Log.d(TAG, "PlayedCache: lock acquired for '${item.videoId}'")
            val existing = db.getPlayedCacheEntry(item.videoId)
            if (existing != null) {
                Log.d(TAG, "PlayedCache: already cached '${item.videoId}', updating timestamp")
                db.upsertPlayedCacheEntry(existing.copy(cachedAt = System.currentTimeMillis()))
                _snapshot.value = loadSnapshot()
                return@withLock
            }

            val dlSong = db.getSong(item.videoId)
            val dlVideo = db.getVideo(item.videoId)
            val downloadedFile = (dlSong?.takeIf { it.isDownloaded }?.filePath
                ?: dlVideo?.takeIf { it.isDownloaded }?.filePath)
                ?.let { File(it) }
                ?.takeIf { it.exists() && it.length() > 0 }

            val destFile: File
            if (downloadedFile != null) {
                Log.d(TAG, "PlayedCache: reusing downloaded file for '${item.videoId}'")
                destFile = downloadedFile
            } else {
                if (sourceFile.length() > PLAYED_CACHE_MAX_FILE_BYTES) {
                    Log.d(TAG, "PlayedCache: skipped '${item.videoId}' — file too large (${sourceFile.length() / 1024}KB)")
                    return@withLock
                }
                val ext = sourceFile.extension.ifBlank { "m4a" }
                destFile = File(playedDir, "${sanitize(item.videoId)}.$ext")
                Log.d(TAG, "PlayedCache: copying ${sourceFile.length() / 1024}KB → ${destFile.absolutePath}")
                runCatching { sourceFile.copyTo(destFile, overwrite = true) }
                    .onFailure {
                        Log.e(TAG, "PlayedCache: copy FAILED for '${item.videoId}'", it)
                        return@withLock
                    }
            }

            val thumbnailPath = persistThumbnail("played_${item.videoId}", item.thumbnailUrl)

            db.upsertPlayedCacheEntry(
                PlayedCacheDbRecord(
                    videoId = item.videoId,
                    title = item.title,
                    artistName = item.artistName,
                    artistId = item.artistId,
                    albumName = item.albumName,
                    albumId = item.albumId,
                    thumbnailUrl = item.thumbnailUrl,
                    thumbnailPath = thumbnailPath,
                    durationText = item.durationText,
                    filePath = destFile.absolutePath,
                    fileSizeBytes = destFile.length(),
                    cachedAt = System.currentTimeMillis()
                )
            )
            Log.d(TAG, "PlayedCache: saved '${item.videoId}' (${destFile.length() / 1024}KB)")

            evictPlayedCacheIfNeeded()
            _snapshot.value = loadSnapshot()
        }
    }

    suspend fun clearPlayedCache() = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val entries = db.getPlayedCacheSongs()
            entries.forEach { entry ->
                val dlSong = db.getSong(entry.videoId)
                val dlVideo = db.getVideo(entry.videoId)
                val sharedByDownload =
                    (dlSong?.isDownloaded == true && dlSong.filePath == entry.filePath) ||
                    (dlVideo?.isDownloaded == true && dlVideo.filePath == entry.filePath)
                if (!sharedByDownload) runCatching { File(entry.filePath).delete() }
            }
            db.clearPlayedCache()
            _snapshot.value = loadSnapshot()
        }
    }

    fun getPlayedCacheSizeMb(): Float {
        val bytes = db.getPlayedCacheTotalSizeBytes()
        return bytes / (1024f * 1024f)
    }

    private fun evictPlayedCacheIfNeeded() {
        val limitBytes = AppConfig.playedCacheLimitMb.value * 1024L * 1024L
        var totalBytes = db.getPlayedCacheTotalSizeBytes()
        while (totalBytes > limitBytes) {
            val oldest = db.getOldestPlayedCacheEntries(1).firstOrNull() ?: break
            val dlSong = db.getSong(oldest.videoId)
            val dlVideo = db.getVideo(oldest.videoId)
            val sharedByDownload =
                (dlSong?.isDownloaded == true && dlSong.filePath == oldest.filePath) ||
                (dlVideo?.isDownloaded == true && dlVideo.filePath == oldest.filePath)
            if (!sharedByDownload) runCatching { File(oldest.filePath).delete() }
            db.deletePlayedCacheEntry(oldest.videoId)
            totalBytes -= oldest.fileSizeBytes
        }
    }

    fun findLocalAudioFile(videoId: String): File? {
        val song = db.getSong(videoId)
        if (song != null && song.isDownloaded && !song.filePath.isNullOrBlank()) {
            val f = File(song.filePath)
            if (f.exists() && f.length() > 0) return f
        }
        val video = db.getVideo(videoId)
        if (video != null && video.isDownloaded && !video.filePath.isNullOrBlank()) {
            val f = File(video.filePath)
            if (f.exists() && f.length() > 0) return f
        }
        val played = db.getPlayedCacheEntry(videoId)
        if (played != null) {
            val f = File(played.filePath)
            if (f.exists() && f.length() > 0) return f
        }
        return null
    }

    fun observeMediaState(videoId: String): Flow<MediaLibraryState> {
        return snapshot.map {
            val (liked, downloaded) = withContext(Dispatchers.IO) {
                db.getMediaState(videoId)
            }
            MediaLibraryState(isLiked = liked, isDownloaded = downloaded)
        }.distinctUntilChanged()
    }

    private fun loadSnapshot(): LibrarySnapshot {
        val likedSongs = db.getLikedSongs()
            .sortedByDescending { it.dateAdded }
            .map { it.toMediaItem() }
        val topSongs = likedSongs

        val downloadedSongs = db.getDownloadedSongs()
        val downloadedVideos = db.getDownloadedVideos()
        val downloadedMedia = buildList<Pair<Long, MediaItem>> {
            downloadedSongs.forEach { add(it.dateAdded to it.toMediaItem()) }
            downloadedVideos.forEach { add(it.dateAdded to it.toMediaItem()) }
        }.sortedByDescending { it.first }
            .map { it.second }

        val artists = db.getArtists().map { row ->
            SavedEntry(
                type = SavedEntryType.ARTIST,
                id = row.artistId,
                title = row.name,
                subtitle = "Artist",
                thumbnailUrl = displayThumbnailUrl(row.thumbnailUrl, row.thumbnailPath),
                dateAdded = row.dateAdded
            )
        }

        val playlists = db.getPlaylists().map { row ->
            SavedEntry(
                type = SavedEntryType.PLAYLIST,
                id = row.playlistId,
                title = row.title,
                subtitle = row.author ?: "Playlist",
                thumbnailUrl = displayThumbnailUrl(row.thumbnailUrl, row.thumbnailPath),
                dateAdded = row.dateAdded
            )
        }

        val albums = db.getAlbums().map { row ->
            SavedEntry(
                type = SavedEntryType.ALBUM,
                id = row.albumId,
                title = row.title,
                subtitle = row.artistName ?: "Album",
                thumbnailUrl = displayThumbnailUrl(row.thumbnailUrl, row.thumbnailPath),
                year = row.year,
                dateAdded = row.dateAdded
            )
        }

        val playedSongs = db.getPlayedCacheSongs().map { row ->
            MediaItem(
                videoId = row.videoId,
                title = row.title,
                artistName = row.artistName,
                artistId = row.artistId,
                albumName = row.albumName,
                albumId = row.albumId,
                thumbnailUrl = displayThumbnailUrl(row.thumbnailUrl, row.thumbnailPath),
                durationText = row.durationText,
                musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
            )
        }

        return LibrarySnapshot(
            likedSongs = likedSongs,
            topSongs = topSongs,
            downloadedMedia = downloadedMedia,
            playedSongs = playedSongs,
            artists = artists,
            playlists = playlists,
            albums = albums
        )
    }

    private suspend fun resolveStreamUrl(videoId: String): String {
        val cached = AppCache.getStreamUrl(videoId)
        if (!cached.isNullOrBlank()) return cached

        VisitorManager.ensureStreamVisitorId()
        val details = StreamRequestResolver.fetchSongPlaybackDetails(videoId)
        val resolved = details?.streamUrl
        check(!resolved.isNullOrBlank()) { "No stream URL available for $videoId." }
        AppCache.putStreamUrl(videoId, resolved)
        return resolved
    }

    private fun String.withFullRange(): String {
        if (contains("range=")) {
            return replace(Regex("range=[^&]*"), "range=0-")
        }
        return if (contains("?")) "$this&range=0-" else "$this?range=0-"
    }

    private fun extensionFromContentType(contentType: String?): String {
        val normalized = contentType?.lowercase(Locale.US).orEmpty()
        return when {
            normalized.contains("audio/webm") -> "webm"
            normalized.contains("audio/mp4") -> "m4a"
            normalized.contains("video/mp4") -> "mp4"
            normalized.contains("audio/mpeg") -> "mp3"
            else -> "m4a"
        }
    }

    private fun displayThumbnailUrl(remoteUrl: String?, localPath: String?): String? {
        if (!localPath.isNullOrBlank()) {
            val file = File(localPath)
            if (file.exists()) return file.toURI().toString()
        }
        return remoteUrl
    }

    private suspend fun persistThumbnail(key: String, remoteUrl: String?): String? {
        if (remoteUrl.isNullOrBlank()) return null
        return runCatching {
            val existing = File(thumbDir, "${sanitize(key)}.jpg")
            if (existing.exists()) return existing.absolutePath

            val request = Request.Builder().url(remoteUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                existing.outputStream().use { output ->
                    body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }
                existing.absolutePath
            }
        }.getOrNull()
    }

    private fun sanitize(value: String): String {
        val decoded = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
        return decoded.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun setDownloadState(videoId: String, state: MediaDownloadState?) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            if (state == null) remove(videoId) else put(videoId, state)
        }
    }

    private fun MediaItem.isVideo(): Boolean {
        return musicVideoType?.contains("OMV", ignoreCase = true) == true
    }

    private fun SongDbRecord.toMediaItem(): MediaItem = MediaItem(
        videoId = videoId,
        title = title,
        artistName = artistName,
        artistId = artistId,
        albumName = albumName,
        albumId = albumId,
        thumbnailUrl = displayThumbnailUrl(thumbnailUrl, thumbnailPath),
        durationText = durationText,
        musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
    )

    private fun VideoDbRecord.toMediaItem(): MediaItem = MediaItem(
        videoId = videoId,
        title = title,
        artistName = artistName,
        artistId = artistId,
        albumName = null,
        albumId = null,
        thumbnailUrl = displayThumbnailUrl(thumbnailUrl, thumbnailPath),
        durationText = durationText,
        musicVideoType = "MUSIC_VIDEO_TYPE_OMV"
    )

    companion object {
        private const val TAG = "LibraryRepository"
        private const val PLAYED_CACHE_MAX_FILE_BYTES = 15L * 1024 * 1024

        @Volatile
        private var INSTANCE: LibraryRepository? = null

        fun getInstance(context: Context): LibraryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LibraryRepository(context).also { INSTANCE = it }
            }
        }
    }
}
