package com.example.musicality.domain.model

/**
 * Domain model for artist details
 */
data class ArtistDetail(
    val artistName: String,
    val monthlyAudience: String,
    val artistThumbnail: String,
    val about: ArtistAbout,
    val topSongs: List<ArtistTopSong>,
    val albums: List<ArtistAlbum>,
    val featuredPlaylists: List<ArtistPlaylist>,
    val artistPlaylists: List<ArtistPlaylist>,
    val similarArtists: List<SimilarArtist>
)

/**
 * About section for artist
 */
data class ArtistAbout(
    val views: String,
    val description: String
)

/**
 * Top song from artist
 */
data class ArtistTopSong(
    val videoId: String,
    val songName: String,
    val artistName: String,
    val plays: String,
    val albumName: String,
    val thumbnail: String,
    val isExplicit: Boolean
)

/**
 * Album from artist
 */
data class ArtistAlbum(
    val albumName: String,
    val year: String,
    val browseId: String,
    val thumbnail: String,
    val isExplicit: Boolean
)

/**
 * Playlist (featured or by artist)
 */
data class ArtistPlaylist(
    val name: String,
    val thumbnailImg: String,
    val views: String,
    val artistName: String,
    val playlistId: String
)

/**
 * Similar artist
 */
data class SimilarArtist(
    val artistName: String,
    val thumbnail: String,
    val monthlyAudience: String,
    val artistId: String
)
