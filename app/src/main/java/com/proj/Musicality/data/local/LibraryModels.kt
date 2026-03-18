package com.proj.Musicality.data.local

import androidx.compose.runtime.Immutable
import com.proj.Musicality.data.model.MediaItem

enum class SavedFilter {
    ARTIST,
    PLAYLIST,
    ALBUM
}

enum class DateSortOrder {
    NEWEST,
    OLDEST
}

enum class LibraryCollectionType {
    LIKED,
    TOP_SONGS,
    DOWNLOADED
}

enum class SavedEntryType {
    ARTIST,
    PLAYLIST,
    ALBUM
}

@Immutable
data class SavedEntry(
    val type: SavedEntryType,
    val id: String,
    val title: String,
    val subtitle: String?,
    val thumbnailUrl: String?,
    val year: String? = null,
    val dateAdded: Long
)

@Immutable
data class MediaLibraryState(
    val isLiked: Boolean = false,
    val isDownloaded: Boolean = false
)

@Immutable
data class LibrarySnapshot(
    val likedSongs: List<MediaItem> = emptyList(),
    val topSongs: List<MediaItem> = emptyList(),
    val downloadedMedia: List<MediaItem> = emptyList(),
    val artists: List<SavedEntry> = emptyList(),
    val playlists: List<SavedEntry> = emptyList(),
    val albums: List<SavedEntry> = emptyList()
) {
    fun entriesFor(filter: SavedFilter): List<SavedEntry> = when (filter) {
        SavedFilter.ARTIST -> artists
        SavedFilter.PLAYLIST -> playlists
        SavedFilter.ALBUM -> albums
    }
}
