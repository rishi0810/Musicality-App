package com.example.musicality.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing liked songs in the local database
 */
@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey
    val videoId: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val duration: String,
    val channelId: String = "",
    val likedAt: Long = System.currentTimeMillis()
)
