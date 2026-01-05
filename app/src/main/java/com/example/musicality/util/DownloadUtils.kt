package com.example.musicality.util

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import java.io.File
import java.util.concurrent.Executors

/**
 * Singleton utility class for managing ExoPlayer's download infrastructure.
 * 
 * This provides:
 * - A shared cache for downloaded songs
 * - A DownloadManager for handling large file downloads from YouTube-style CDN URLs
 * - CacheDataSource.Factory for playing downloaded files offline
 * 
 * Key features:
 * - Handles URL expiration by allowing resume with refreshed URLs
 * - Supports pause/resume functionality
 * - Integrates with existing ExoPlayer cache for seamless playback
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object DownloadUtils {
    
    private const val DOWNLOAD_DIR = "exo_downloads"
    private const val DATABASE_NAME = "exo_download_database"
    
    @Volatile
    private var cache: Cache? = null
    
    @Volatile
    private var downloadManager: DownloadManager? = null
    
    @Volatile
    private var databaseProvider: StandaloneDatabaseProvider? = null
    
    /**
     * Get or create the shared download cache.
     * Uses NoOpCacheEvictor since we want to keep all downloaded songs permanently.
     */
    @Synchronized
    fun getCache(context: Context): Cache {
        if (cache == null) {
            val cacheDir = File(context.getExternalFilesDir(null), DOWNLOAD_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            cache = SimpleCache(
                cacheDir,
                NoOpCacheEvictor(), // Never evict - keep all downloads
                getDatabaseProvider(context)
            )
        }
        return cache!!
    }
    
    /**
     * Get or create the database provider for cache metadata.
     */
    @Synchronized
    private fun getDatabaseProvider(context: Context): StandaloneDatabaseProvider {
        if (databaseProvider == null) {
            databaseProvider = StandaloneDatabaseProvider(context)
        }
        return databaseProvider!!
    }
    
    /**
     * Get or create the DownloadManager for background downloads.
     * 
     * The DownloadManager handles:
     * - Chunked downloads for large files
     * - Automatic retry on network errors
     * - Resume support when URLs are refreshed
     * - Progress tracking
     */
    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        if (downloadManager == null) {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36")
                .setConnectTimeoutMs(30_000)
                .setReadTimeoutMs(60_000)
                .setAllowCrossProtocolRedirects(true)
            
            downloadManager = DownloadManager(
                context,
                getDatabaseProvider(context),
                getCache(context),
                httpDataSourceFactory,
                Executors.newFixedThreadPool(3) // 3 parallel downloads max
            )
            
            // Set max parallel downloads
            downloadManager!!.maxParallelDownloads = 3
        }
        return downloadManager!!
    }
    
    /**
     * Create a CacheDataSource.Factory for playing downloaded content.
     * 
     * This factory:
     * - First checks cache for downloaded content
     * - Falls back to network if content not cached
     * - Can be used with ExoPlayer for seamless offline/online playback
     */
    fun getCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36")
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(60_000)
            .setAllowCrossProtocolRedirects(true)
        
        return CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(null) // Don't write to cache during playback (only during download)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
    
    /**
     * Check if a content is fully downloaded by its content ID (videoId).
     */
    fun isFullyDownloaded(context: Context, contentId: String): Boolean {
        val downloadManager = getDownloadManager(context)
        val download = downloadManager.downloadIndex.getDownload(contentId)
        return download?.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
    }
    
    /**
     * Get download progress (0.0 to 1.0) for a content ID.
     * Returns -1 if no download exists.
     */
    fun getDownloadProgress(context: Context, contentId: String): Float {
        val downloadManager = getDownloadManager(context)
        val download = downloadManager.downloadIndex.getDownload(contentId) ?: return -1f
        return download.percentDownloaded / 100f
    }
    
    /**
     * Remove a download and its cached content.
     */
    fun removeDownload(context: Context, contentId: String) {
        val downloadManager = getDownloadManager(context)
        downloadManager.removeDownload(contentId)
    }
    
    /**
     * Release all resources when app is destroyed.
     * Should be called from Application.onTerminate() or similar.
     */
    @Synchronized
    fun release() {
        downloadManager?.release()
        downloadManager = null
        cache?.release()
        cache = null
        databaseProvider = null
    }
}
