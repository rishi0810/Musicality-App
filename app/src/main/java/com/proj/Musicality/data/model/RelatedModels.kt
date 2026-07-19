package com.proj.Musicality.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class RelatedFeed(
    val sections: List<HomeSection>,
    val aboutArtist: String?,
    val artist: RelatedArtist? = null
)

@Immutable
data class RelatedArtist(
    val name: String,
    val artistId: String?,
    val thumbnailUrl: String?
)

sealed interface RelatedState {
    data object Idle : RelatedState
    data class Loading(val videoId: String) : RelatedState
    data class Loaded(val videoId: String, val feed: RelatedFeed) : RelatedState
    data class Error(val videoId: String, val message: String) : RelatedState
}
