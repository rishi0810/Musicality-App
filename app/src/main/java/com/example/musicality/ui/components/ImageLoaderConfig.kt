package com.example.musicality.ui.components

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.util.DebugLogger
import okio.Path.Companion.toOkioPath

/**
 * Provides a centralized, optimized ImageLoader configuration for the app.
 * 
 * Features:
 * - Crossfade transitions for smoother UX
 * - Memory cache (25% of available memory)
 * - Disk cache (250MB max) for offline access
 * - Aggressive caching policies to minimize network requests
 */
object ImageLoaderConfig {
    
    @Volatile
    private var imageLoader: ImageLoader? = null
    
    /**
     * Get the singleton ImageLoader instance.
     * Thread-safe initialization.
     */
    fun getImageLoader(context: Context): ImageLoader {
        return imageLoader ?: synchronized(this) {
            imageLoader ?: createImageLoader(context).also { imageLoader = it }
        }
    }
    
    private fun createImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            // Smooth crossfade transition (300ms)
            .crossfade(true)
            .crossfade(300)
            
            // Memory cache: 25% of available heap
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            
            // Disk cache: 250MB in cache directory
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(250L * 1024 * 1024) // 250MB
                    .build()
            }
            
            // Caching policies
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            
            .build()
    }
}
