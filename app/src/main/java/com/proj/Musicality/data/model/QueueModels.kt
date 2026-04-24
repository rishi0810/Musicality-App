package com.proj.Musicality.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class PlaybackQueue(
    val items: List<MediaItem>,
    val currentIndex: Int,
    val source: QueueSource
)

enum class QueueSource { HOME, SEARCH, ALBUM, PLAYLIST, ARTIST_TOP_SONGS, LIBRARY, SINGLE, UP_NEXT }
