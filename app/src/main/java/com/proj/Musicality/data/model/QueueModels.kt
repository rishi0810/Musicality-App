package com.proj.Musicality.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class PlaybackQueue(
    val items: List<MediaItem>,
    val currentIndex: Int,
    val source: QueueSource,
    val searchQuery: String? = null
)

enum class QueueSource {
    HOME,
    SEARCH,
    ALBUM,
    PLAYLIST,
    ARTIST_TOP_SONGS,
    LIKED_SONGS,
    TOP_SONGS,
    LIBRARY,
    DOWNLOADED,
    SINGLE,
    UP_NEXT,
    PLAYED
}
