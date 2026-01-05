package com.example.musicality.di

import android.content.Context
import com.example.musicality.data.local.MusicalityDatabase
import com.example.musicality.data.repository.DownloadRepositoryImpl
import com.example.musicality.data.repository.LikedSongsRepositoryImpl
import com.example.musicality.domain.repository.DownloadRepository
import com.example.musicality.domain.repository.LikedSongsRepository

/**
 * Database module for providing Room database and repository instances.
 * 
 * Provides singleton instances of:
 * - LikedSongsRepository - for managing liked songs
 * - DownloadRepository - for managing downloaded songs
 */
object DatabaseModule {
    
    @Volatile
    private var database: MusicalityDatabase? = null
    
    @Volatile
    private var likedSongsRepository: LikedSongsRepository? = null
    
    @Volatile
    private var downloadRepository: DownloadRepository? = null
    
    /**
     * Initialize the database with application context.
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
     * Get the database instance.
     */
    private fun getDatabase(context: Context): MusicalityDatabase {
        initialize(context)
        return database!!
    }
    
    /**
     * Provides LikedSongsRepository instance.
     */
    fun provideLikedSongsRepository(context: Context): LikedSongsRepository {
        initialize(context)
        return likedSongsRepository ?: synchronized(this) {
            likedSongsRepository ?: LikedSongsRepositoryImpl(
                database!!.likedSongDao()
            ).also { likedSongsRepository = it }
        }
    }
    
    /**
     * Provides DownloadRepository instance.
     */
    fun provideDownloadRepository(context: Context): DownloadRepository {
        initialize(context)
        return downloadRepository ?: synchronized(this) {
            downloadRepository ?: DownloadRepositoryImpl(
                context = context.applicationContext,
                downloadedSongDao = database!!.downloadedSongDao()
            ).also { downloadRepository = it }
        }
    }
}
