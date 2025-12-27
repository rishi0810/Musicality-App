package com.example.musicality.domain.model

/**
 * Domain model for a song in the queue
 */
data class QueueSong(
    val videoId: String,
    val name: String,
    val singer: String,
    val thumbnailUrl: String,
    val duration: String
)
