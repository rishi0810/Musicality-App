package com.example.musicality.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing saved/bookmarked playlists
 */
@Entity(tableName = "saved_playlists")
data class SavedPlaylistEntity(
    @PrimaryKey
    val playlistId: String,
    val name: String,
    val thumbnailUrl: String,
    val trackCount: Int,
    val savedAt: Long = System.currentTimeMillis()
)
