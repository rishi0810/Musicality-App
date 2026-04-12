package com.proj.Musicality.data.model

import androidx.compose.runtime.Immutable
import com.proj.Musicality.data.json.Thumbnail

@Immutable
data class ArtistDetails(
    val name: String,
    val description: String?,
    val viewCount: String?,
    val thumbnails: List<Thumbnail>,
    val topSongs: List<ArtistSong>,
    val albums: List<ArtistContent>,
    val singles: List<ArtistContent>,
    val videos: List<ArtistVideo>,
    val livePerformances: List<ArtistVideo>,
    val featuredOn: List<ArtistContent>,
    val playlists: List<ArtistContent>,
    val similarArtists: List<ArtistRelated>
)

@Immutable
data class ArtistSong(
    val title: String,
    val videoId: String,
    val image: String?,
    val views: String?,
    val plays: String?,
    val album: String?
)

@Immutable
data class ArtistContent(
    val title: String,
    val browseId: String,
    val year: String?,
    val image: String?,
    val type: String
)

@Immutable
data class ArtistVideo(
    val title: String,
    val videoId: String,
    val image: String?,
    val views: String?
)

@Immutable
data class ArtistRelated(
    val name: String,
    val browseId: String,
    val subscribers: String?,
    val image: String?
)

@Immutable
data class AlbumPage(
    val title: String,
    val description: String?,
    val artist: ArtistTiny,
    val year: String?,
    val trackCount: Int?,
    val duration: String?,
    val thumbnail: String?,
    val tracks: List<Track>
)

@Immutable
data class ArtistTiny(val name: String, val id: String?)

@Immutable
data class Track(
    val title: String,
    val videoId: String?,
    val duration: String?,
    val plays: String?,
    val index: Int,
    val artistName: String? = null,
    val artistId: String? = null
)

@Immutable
data class PlaylistPage(
    val playlistId: String,
    val title: String,
    val description: String?,
    val author: String?,
    val year: String?,
    val viewCount: String?,
    val trackCount: Int?,
    val duration: String?,
    val thumbnails: List<Thumbnail>,
    val tracks: List<PlaylistTrack>
)

@Immutable
data class PlaylistTrack(
    val title: String,
    val videoId: String,
    val playlistSetVideoId: String?,
    val artists: List<PlaylistArtist>,
    val duration: String?,
    val albumOrDate: String?,
    val thumbnailUrl: String?,
    val musicVideoType: String?
)

@Immutable
data class PlaylistArtist(
    val name: String,
    val id: String?
)
