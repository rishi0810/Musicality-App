package com.example.musicality.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for the Musicality app
 */
@Database(
    entities = [LikedSongEntity::class, DownloadedSongEntity::class],
    version = 2,
    exportSchema = false
)
abstract class MusicalityDatabase : RoomDatabase() {
    
    abstract fun likedSongDao(): LikedSongDao
    abstract fun downloadedSongDao(): DownloadedSongDao
    
    companion object {
        @Volatile
        private var INSTANCE: MusicalityDatabase? = null
        
        fun getDatabase(context: Context): MusicalityDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicalityDatabase::class.java,
                    "musicality_database"
                )
                    .fallbackToDestructiveMigration() // For development - handles version changes
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

