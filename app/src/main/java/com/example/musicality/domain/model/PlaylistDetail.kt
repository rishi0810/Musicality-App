package com.example.musicality.domain.model

/**
 * Domain model for playlist details
 */
data class PlaylistDetail(
    val thumbnailImg: String,
    val playlistName: String,
    val totalTracks: Int,
    val totalTime: String,
    val songs: List<PlaylistSong>
)

/**
 * Domain model for a song in a playlist
 */
data class PlaylistSong(
    val videoId: String,
    val songName: String,
    val thumbnail: String,
    val duration: String,
    val artists: String
)
