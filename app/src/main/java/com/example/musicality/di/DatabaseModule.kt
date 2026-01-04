package com.example.musicality.di

import android.content.Context
import com.example.musicality.data.local.MusicalityDatabase
import com.example.musicality.data.repository.LikedSongsRepositoryImpl
import com.example.musicality.domain.repository.LikedSongsRepository

/**
 * Database module for providing Room database and repository instances
 */
object DatabaseModule {
    
    @Volatile
    private var database: MusicalityDatabase? = null
    
    @Volatile
    private var likedSongsRepository: LikedSongsRepository? = null
    
    /**
     * Initialize the database with application context
     * Should be called in Application.onCreate()
     */
    fun initialize(context: Context) {
        if (database == null) {
            synchronized(this) {
                if (database == null) {
                    database = MusicalityDatabase.getDatabase(context.applicationContext)
                }
            }
        }
    }
    
    /**
     * Provides LikedSongsRepository instance
     */
    fun provideLikedSongsRepository(context: Context): LikedSongsRepository {
        initialize(context)
        return likedSongsRepository ?: synchronized(this) {
            likedSongsRepository ?: LikedSongsRepositoryImpl(
                database!!.likedSongDao()
            ).also { likedSongsRepository = it }
        }
    }
}
