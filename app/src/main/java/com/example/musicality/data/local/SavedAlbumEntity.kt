package com.example.musicality.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing saved/bookmarked albums
 */
@Entity(tableName = "saved_albums")
data class SavedAlbumEntity(
    @PrimaryKey
    val albumId: String,
    val name: String,
    val artist: String,
    val thumbnailUrl: String,
    val songCount: String,
    val savedAt: Long = System.currentTimeMillis()
)
