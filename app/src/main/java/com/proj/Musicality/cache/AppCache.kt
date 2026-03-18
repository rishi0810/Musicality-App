package com.proj.Musicality.cache

data class CachedStream(val url: String, val expiresAtMillis: Long)

object AppCache {
    val browse = LruCache<String, Any>(30)
    val stream = LruCache<String, CachedStream>(20)
    val search = LruCache<String, Any>(15)
    /** Caches palette extraction results by image URL to avoid redundant Palette.generate() calls. */
    val paletteColors = LruCache<String, Any>(50)

    fun getStreamUrl(videoId: String): String? {
        val cached = stream.get(videoId) ?: return null
        if (System.currentTimeMillis() > cached.expiresAtMillis) return null
        return cached.url
    }

    fun putStreamUrl(videoId: String, url: String) {
        val expire = url.substringAfter("expire=", "")
            .substringBefore("&")
            .toLongOrNull()
            ?.times(1000)
            ?: (System.currentTimeMillis() + 6 * 3600 * 1000)
        stream.put(videoId, CachedStream(url, expire))
    }
}
