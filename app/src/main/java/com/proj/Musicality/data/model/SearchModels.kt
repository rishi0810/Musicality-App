package com.proj.Musicality.data.model

import androidx.compose.runtime.Immutable
import com.proj.Musicality.data.json.Thumbnail

enum class SuggestionType { SONG, VIDEO, ALBUM, ARTIST, PLAYLIST, SUGGESTION, UNKNOWN }

@Immutable
data class SearchSuggestionResult(
    val type: SuggestionType,
    val title: String,
    val id: String? = null,
    val subtitle: String? = null,
    val thumbnails: List<Thumbnail>? = null,
    val artists: List<ArtistInfo>? = null,
    val album: AlbumInfo? = null
)

@Immutable
data class ArtistInfo(val name: String, val id: String)
@Immutable
data class AlbumInfo(val name: String?, val id: String)

@Immutable
data class SongResult(
    val title: String,
    val videoId: String,
    val artist: String,
    val artistId: String?,
    val album: String?,
    val albumId: String?,
    val duration: String?,
    val plays: String?,
    val thumb: String?
)

@Immutable
data class VideoResult(
    val title: String,
    val videoId: String,
    val artist: String,
    val artistId: String?,
    val views: String?,
    val duration: String?,
    val thumb: String?
)

@Immutable
data class ArtistResult(
    val name: String,
    val artistId: String,
    val subscribers: String?,
    val thumb: String?
)

@Immutable
data class AlbumResult(
    val title: String,
    val albumId: String,
    val artist: String,
    val year: String?,
    val thumb: String?
)

@Immutable
data class PlaylistResult(
    val title: String,
    val playlistId: String,
    val author: String,
    val countOrViews: String?,
    val thumb: String?
)
