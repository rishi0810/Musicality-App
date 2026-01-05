package com.example.musicality.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing saved/bookmarked artists
 */
@Entity(tableName = "saved_artists")
data class SavedArtistEntity(
    @PrimaryKey
    val artistId: String,
    val name: String,
    val thumbnailUrl: String,
    val monthlyListeners: String,
    val savedAt: Long = System.currentTimeMillis()
)
