package com.proj.Musicality.automotive

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.proj.Musicality.cache.AudioFileCache
import com.proj.Musicality.data.local.LibraryRepository
import com.proj.Musicality.data.local.ListeningHistoryRepository
import com.proj.Musicality.data.model.MediaItem as AppMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class MusicalityLibraryCallback(
    private val serviceScope: CoroutineScope,
    private val libraryRepository: LibraryRepository,
    private val listeningHistoryRepository: ListeningHistoryRepository
) : MediaLibrarySession.Callback {

    companion object {
        private const val TAG = "MusicLibCallback"

        const val ROOT_ID = "musicality_root"
        const val FOLDER_LIKED = "folder|liked"
        const val FOLDER_DOWNLOADED = "folder|downloaded"
        const val FOLDER_RECENT = "folder|recent"
        const val FOLDER_TOP = "folder|top"

        private const val TRACK_PREFIX = "tr|"

        fun trackId(folderKey: String, videoId: String): String =
            "$TRACK_PREFIX$folderKey|$videoId"

        fun extractFolderKey(mediaId: String): String? {
            if (!mediaId.startsWith(TRACK_PREFIX)) return null
            val rest = mediaId.removePrefix(TRACK_PREFIX)
            val sep = rest.indexOf('|')
            return if (sep <= 0) null else rest.substring(0, sep)
        }

        fun extractVideoId(mediaId: String): String? {
            if (!mediaId.startsWith(TRACK_PREFIX)) return null
            val rest = mediaId.removePrefix(TRACK_PREFIX)
            val sep = rest.indexOf('|')
            return if (sep < 0 || sep == rest.lastIndex) null else rest.substring(sep + 1)
        }
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val root = browsableItem(
            mediaId = ROOT_ID,
            title = "Musicality",
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        )
        return Futures.immediateFuture(LibraryResult.ofItem(root, params))
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val future = SettableFuture.create<LibraryResult<MediaItem>>()
        serviceScope.launch {
            val item = buildItemForMediaId(mediaId)
            if (item != null) {
                future.set(LibraryResult.ofItem(item, null))
            } else {
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
        }
        return future
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        serviceScope.launch {
            val children = childrenOf(parentId)
            val paged = paginate(children, page, pageSize)
            future.set(LibraryResult.ofItemList(ImmutableList.copyOf(paged), params))
        }
        return future
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
        val future = SettableFuture.create<MutableList<MediaItem>>()
        serviceScope.launch {
            val resolved = mediaItems.mapNotNull { resolveForPlayback(it) }.toMutableList()
            Log.d(TAG, "onAddMediaItems: ${mediaItems.size} in → ${resolved.size} out")
            future.set(resolved)
        }
        return future
    }

    // ── Browse tree ──

    private suspend fun childrenOf(parentId: String): List<MediaItem> = when (parentId) {
        ROOT_ID -> rootFolders()
        FOLDER_LIKED, FOLDER_DOWNLOADED, FOLDER_RECENT, FOLDER_TOP ->
            folderTracks(parentId)
        else -> emptyList()
    }

    private fun rootFolders(): List<MediaItem> = listOf(
        browsableItem(
            mediaId = FOLDER_LIKED,
            title = "Liked Songs",
            mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST
        ),
        browsableItem(
            mediaId = FOLDER_DOWNLOADED,
            title = "Downloaded",
            mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST
        ),
        browsableItem(
            mediaId = FOLDER_RECENT,
            title = "Recently Played",
            mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST
        ),
        browsableItem(
            mediaId = FOLDER_TOP,
            title = "Top Songs",
            mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST
        )
    )

    private suspend fun folderTracks(folderId: String): List<MediaItem> {
        val items = appItemsForFolder(folderId)
        val key = folderKey(folderId) ?: return emptyList()
        return items.map { playableItem(trackId(key, it.videoId), it) }
    }

    private suspend fun appItemsForFolder(folderId: String): List<AppMediaItem> =
        withContext(Dispatchers.IO) {
            when (folderId) {
                FOLDER_LIKED -> {
                    libraryRepository.refresh()
                    libraryRepository.snapshot.value.likedSongs
                }
                FOLDER_DOWNLOADED -> {
                    libraryRepository.refresh()
                    libraryRepository.snapshot.value.downloadedMedia
                }
                FOLDER_RECENT -> listeningHistoryRepository.getSnapshot()
                    .recentlyPlayed.map { it.toAppMediaItem() }
                FOLDER_TOP -> listeningHistoryRepository.getSnapshot()
                    .topSongs.map { it.toAppMediaItem() }
                else -> emptyList()
            }
        }

    private fun folderKey(folderId: String): String? = when (folderId) {
        FOLDER_LIKED -> "liked"
        FOLDER_DOWNLOADED -> "downloaded"
        FOLDER_RECENT -> "recent"
        FOLDER_TOP -> "top"
        else -> null
    }

    private fun folderIdFromKey(key: String): String? = when (key) {
        "liked" -> FOLDER_LIKED
        "downloaded" -> FOLDER_DOWNLOADED
        "recent" -> FOLDER_RECENT
        "top" -> FOLDER_TOP
        else -> null
    }

    private fun folderTitle(folderId: String): String = when (folderId) {
        FOLDER_LIKED -> "Liked Songs"
        FOLDER_DOWNLOADED -> "Downloaded"
        FOLDER_RECENT -> "Recently Played"
        FOLDER_TOP -> "Top Songs"
        else -> "Musicality"
    }

    private suspend fun buildItemForMediaId(mediaId: String): MediaItem? = when (mediaId) {
        ROOT_ID -> browsableItem(
            mediaId = ROOT_ID,
            title = "Musicality",
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        )
        FOLDER_LIKED, FOLDER_DOWNLOADED, FOLDER_RECENT, FOLDER_TOP -> browsableItem(
            mediaId = mediaId,
            title = folderTitle(mediaId),
            mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST
        )
        else -> {
            val key = extractFolderKey(mediaId)
            val videoId = extractVideoId(mediaId)
            if (key == null || videoId.isNullOrBlank()) {
                null
            } else {
                val folderId = folderIdFromKey(key)
                val appItem = folderId?.let { lookupAppItem(it, videoId) }
                appItem?.let { playableItem(mediaId, it) }
            }
        }
    }

    private suspend fun lookupAppItem(folderId: String, videoId: String): AppMediaItem? {
        val items = appItemsForFolder(folderId)
        return items.firstOrNull { it.videoId == videoId }
    }

    // ── Playback resolution ──

    private suspend fun resolveForPlayback(input: MediaItem): MediaItem? {
        val mediaId = input.mediaId.takeIf { it.isNotBlank() } ?: return null
        val folderKey = extractFolderKey(mediaId)
        val videoId = extractVideoId(mediaId) ?: mediaId

        val appItem: AppMediaItem = if (folderKey != null) {
            val folderId = folderIdFromKey(folderKey)
            folderId?.let { lookupAppItem(it, videoId) } ?: return null
        } else {
            findAppItemAcrossFolders(videoId) ?: return null
        }

        Log.d(TAG, "resolveForPlayback: videoId='$videoId' title='${appItem.title}'")
        val file = AudioFileCache.getOrDownload(videoId) ?: run {
            Log.e(TAG, "resolveForPlayback: no audio file for '$videoId'")
            return null
        }

        AudioFileCache.pin(videoId)

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(file.toURI().toString())
            .setMediaMetadata(buildTrackMetadata(appItem))
            .build()
    }

    private suspend fun findAppItemAcrossFolders(videoId: String): AppMediaItem? {
        libraryRepository.refresh()
        val snap = libraryRepository.snapshot.value
        (snap.likedSongs + snap.downloadedMedia).firstOrNull { it.videoId == videoId }
            ?.let { return it }
        val history = listeningHistoryRepository.getSnapshot()
        history.recentlyPlayed.firstOrNull { it.videoId == videoId }
            ?.let { return it.toAppMediaItem() }
        history.topSongs.firstOrNull { it.videoId == videoId }
            ?.let { return it.toAppMediaItem() }
        return null
    }

    // ── Item builders ──

    private fun browsableItem(
        mediaId: String,
        title: String,
        mediaType: Int
    ): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(mediaType)
                .build()
        )
        .build()

    private fun playableItem(mediaId: String, appItem: AppMediaItem): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(buildTrackMetadata(appItem))
            .build()

    private fun buildTrackMetadata(appItem: AppMediaItem): MediaMetadata =
        MediaMetadata.Builder()
            .setTitle(appItem.title)
            .setArtist(appItem.artistName)
            .setAlbumTitle(appItem.albumName)
            .setArtworkUri(appItem.thumbnailUrl?.let { Uri.parse(it) })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()

    private fun <T> paginate(list: List<T>, page: Int, pageSize: Int): List<T> {
        if (pageSize <= 0) return list
        val start = (page.coerceAtLeast(0)) * pageSize
        if (start >= list.size) return emptyList()
        val end = minOf(start + pageSize, list.size)
        return list.subList(start, end)
    }
}
