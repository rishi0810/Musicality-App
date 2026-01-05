package com.example.musicality.di

import com.example.musicality.data.remote.TodoApiService
import com.example.musicality.data.repository.TodoRepositoryImpl
import com.example.musicality.domain.repository.TodoRepository
import com.example.musicality.util.NetworkConstants
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Network module for providing Retrofit and API service instances
 * This follows the Dependency Injection pattern for better testability
 */
object NetworkModule {
    
    /**
     * Provides OkHttpClient with logging interceptor
     */
    private fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(NetworkConstants.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(NetworkConstants.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(NetworkConstants.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * Provides Moshi instance for JSON parsing
     */
    private fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
    
    /**
     * Provides Retrofit instance
     */
    private fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(NetworkConstants.BASE_URL)
            .client(provideOkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(provideMoshi()))
            .build()
    }
    
    /**
     * Provides TodoApiService instance
     */
    fun provideTodoApiService(): TodoApiService {
        return provideRetrofit().create(TodoApiService::class.java)
    }
    
    /**
     * Provides TodoRepository instance
     */
    fun provideTodoRepository(): TodoRepository {
        return TodoRepositoryImpl(provideTodoApiService())
    }
    
    /**
     * Provides Retrofit instance for Search API
     */
    private fun provideSearchRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(NetworkConstants.SEARCH_BASE_URL)
            .client(provideOkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(provideMoshi()))
            .build()
    }
    
    /**
     * Provides SearchApiService instance
     */
    fun provideSearchApiService(): com.example.musicality.data.remote.SearchApiService {
        return provideSearchRetrofit().create(com.example.musicality.data.remote.SearchApiService::class.java)
    }
    
    /**
     * Provides SearchRepository instance
     */
    fun provideSearchRepository(): com.example.musicality.domain.repository.SearchRepository {
        return com.example.musicality.data.repository.SearchRepositoryImpl(provideSearchApiService())
    }
    
    /**
     * Provides PlayerApiService instance (uses same base URL as search)
     */
    fun providePlayerApiService(): com.example.musicality.data.remote.PlayerApiService {
        return provideSearchRetrofit().create(com.example.musicality.data.remote.PlayerApiService::class.java)
    }
    
    /**
     * Provides PlayerRepository instance
     */
    fun providePlayerRepository(): com.example.musicality.domain.repository.PlayerRepository {
        return com.example.musicality.data.repository.PlayerRepositoryImpl(providePlayerApiService())
    }
    
    /**
     * Provides QueueApiService instance (uses same base URL as search/player)
     */
    fun provideQueueApiService(): com.example.musicality.data.remote.QueueApiService {
        return provideSearchRetrofit().create(com.example.musicality.data.remote.QueueApiService::class.java)
    }
    
    /**
     * Provides QueueRepository instance
     */
    fun provideQueueRepository(): com.example.musicality.domain.repository.QueueRepository {
        return com.example.musicality.data.repository.QueueRepositoryImpl(provideQueueApiService())
    }
    
    /**
     * Provides AlbumApiService instance (uses same base URL as search/player/queue)
     */
    fun provideAlbumApiService(): com.example.musicality.data.remote.AlbumApiService {
        return provideSearchRetrofit().create(com.example.musicality.data.remote.AlbumApiService::class.java)
    }
    
    /**
     * Provides AlbumRepository instance
     */
    fun provideAlbumRepository(): com.example.musicality.domain.repository.AlbumRepository {
        return com.example.musicality.data.repository.AlbumRepositoryImpl(provideAlbumApiService())
    }
    
    /**
     * Provides PlaylistApiService instance (uses same base URL as search/player/queue/album)
     */
    fun providePlaylistApiService(): com.example.musicality.data.remote.PlaylistApiService {
        return provideSearchRetrofit().create(com.example.musicality.data.remote.PlaylistApiService::class.java)
    }
    
    /**
     * Provides PlaylistRepository instance
     */
    fun providePlaylistRepository(): com.example.musicality.domain.repository.PlaylistRepository {
        return com.example.musicality.data.repository.PlaylistRepositoryImpl(providePlaylistApiService())
    }
    
    /**
     * Provides ArtistApiService instance (uses same base URL as search/player/queue/album/playlist)
     */
    fun provideArtistApiService(): com.example.musicality.data.remote.ArtistApiService {
        return provideSearchRetrofit().create(com.example.musicality.data.remote.ArtistApiService::class.java)
    }
    
    /**
     * Provides ArtistRepository instance
     */
    fun provideArtistRepository(): com.example.musicality.domain.repository.ArtistRepository {
        return com.example.musicality.data.repository.ArtistRepositoryImpl(provideArtistApiService())
    }
}
