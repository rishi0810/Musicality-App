package com.example.musicality.domain.model

/**
 * Domain model for album details
 */
data class AlbumDetail(
    val albumThumbnail: String,
    val albumName: String,
    val artist: String,
    val artistThumbnail: String,
    val count: String,
    val duration: String,
    val songs: List<AlbumSong>,
    val moreAlbums: List<RelatedAlbum>
)

/**
 * Domain model for a song in an album
 */
data class AlbumSong(
    val videoId: String,
    val title: String,
    val viewCount: String,
    val duration: String
)

/**
 * Domain model for related albums in "Explore More" section
 */
data class RelatedAlbum(
    val albumId: String,
    val albumName: String,
    val albumImg: String,
    val albumArtist: String
)
