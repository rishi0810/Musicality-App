package com.proj.Musicality

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.proj.Musicality.api.IpLocationRepository
import com.proj.Musicality.api.VisitorManager
import com.proj.Musicality.viewmodel.HomePrefetchManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath

class MusalityApplication : Application(), SingletonImageLoader.Factory {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        VisitorManager.loadFromPrefs(this)
        GlobalScope.launch(Dispatchers.IO) {
            IpLocationRepository.fetchAndCacheOnce(this@MusalityApplication)
        }
        GlobalScope.launch(Dispatchers.IO) {
            HomePrefetchManager.prefetch(this@MusalityApplication)
        }
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").path.toPath())
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .crossfade(200)
            .build()
    }
}
