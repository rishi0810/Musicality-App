package com.proj.Musicality.cache

import android.content.Context
import android.util.Log
import com.proj.Musicality.api.StreamRequestResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Downloads and caches complete audio files for gapless/crossfade playback.
 *
 * Instead of streaming URLs (which can stall mid-crossfade), this cache
 * fetches the entire audio file to disk before playback begins.
 *
 * Maintains an LRU of [MAX_CACHED_FILES] files. Oldest files are evicted
 * when the limit is reached, unless they are in the [pinned] set (current
 * and next track during crossfade).
 */
object AudioFileCache {

    private const val TAG = "AudioFileCache"
    private const val MAX_CACHED_FILES = 5
    private const val CACHE_DIR_NAME = "audio_cache"

    private lateinit var cacheDir: File
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateLock = Any()
    private val inFlightDownloads = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<File?>>()

    /** videoId → File, ordered by access time (most recent last). */
    private val fileMap = LinkedHashMap<String, File>(MAX_CACHED_FILES + 2, 0.75f, true)

    /** Video IDs that should not be evicted (current + next during crossfade). */
    private val pinned = mutableSetOf<String>()

    fun init(context: Context) {
        synchronized(stateLock) {
            cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
            fileMap.clear()
            // Rebuild LRU order from disk so eviction prefers the oldest files first.
            cacheDir.listFiles()
                ?.sortedBy { it.lastModified() }
                ?.forEach { file ->
                    val videoId = file.nameWithoutExtension
                    fileMap[videoId] = file
                }
        }
        Log.d(TAG, "Initialized with ${fileMap.size} cached files")
    }

    /** Pin a videoId so it won't be evicted. Call for current + next track. */
    fun pin(videoId: String) {
        synchronized(stateLock) {
            pinned.add(videoId)
        }
    }

    /** Unpin a videoId (e.g., after song change). */
    fun unpin(videoId: String) {
        synchronized(stateLock) {
            pinned.remove(videoId)
        }
    }

    /** Unpin all and optionally evict everything except [keep]. */
    fun unpinAll() {
        synchronized(stateLock) {
            pinned.clear()
        }
    }

    /**
     * Get the cached file for [videoId], or download it first.
     * Returns the local file path, or null if download fails.
     * Thread-safe — concurrent calls for the same videoId will not duplicate work.
     */
    suspend fun getOrDownload(videoId: String): File? = withContext(Dispatchers.IO) {
        synchronized(stateLock) {
            existingCachedFileLocked(videoId)?.let { existing ->
                Log.d(TAG, "CACHE HIT for '$videoId' (${existing.length() / 1024}KB)")
                return@withContext existing
            }
        }

        val newDownload = downloadScope.async(start = CoroutineStart.LAZY) {
            downloadAndCache(videoId)
        }
        val activeDownload = inFlightDownloads.putIfAbsent(videoId, newDownload)
        val download = if (activeDownload == null) {
            newDownload.apply { start() }
        } else {
            newDownload.cancel()
            activeDownload
        }

        try {
            download.await()
        } finally {
            if (activeDownload == null) {
                inFlightDownloads.remove(videoId, download)
            }
        }
    }

    /** Remove cached file for a specific videoId. */
    fun remove(videoId: String) {
        val removed = synchronized(stateLock) {
            pinned.remove(videoId)
            fileMap.remove(videoId)
        }
        removed?.let { file ->
            runCatching { file.delete() }
        }
    }

    /** Clear all cached files. */
    fun clearAll() {
        val files = synchronized(stateLock) {
            val snapshot = fileMap.values.toList()
            fileMap.clear()
            pinned.clear()
            snapshot
        }
        files.forEach { file -> runCatching { file.delete() } }
    }

    private suspend fun downloadAndCache(videoId: String): File? {
        synchronized(stateLock) {
            existingCachedFileLocked(videoId)?.let { existing ->
                Log.d(TAG, "CACHE HIT for '$videoId' (${existing.length() / 1024}KB)")
                return existing
            }
        }

        val streamUrl = resolveStreamUrl(videoId) ?: return null
        val downloadUrl = streamUrl.withFullRange()
        Log.d(TAG, "Downloading full audio for '$videoId'...")

        val request = Request.Builder().url(downloadUrl).build()
        val file = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed: HTTP ${response.code} for '$videoId'")
                    return@use null
                }
                val body = response.body ?: return@use null
                val ext = extensionFromContentType(response.header("Content-Type"))
                val outputFile = File(cacheDir, "$videoId.$ext")

                body.byteStream().use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Downloaded '$videoId': ${outputFile.length() / 1024}KB")
                outputFile
            }
        }.onFailure {
            Log.e(TAG, "Download exception for '$videoId'", it)
        }.getOrNull()

        if (file != null && file.exists() && file.length() > 0) {
            synchronized(stateLock) {
                fileMap[videoId] = file
                evictIfNeededLocked()
            }
            return file
        }

        return null
    }

    private fun evictIfNeededLocked() {
        while (fileMap.size > MAX_CACHED_FILES) {
            // Find the oldest (first) entry that is not pinned
            val victim = fileMap.entries.firstOrNull { it.key !in pinned }
            if (victim != null) {
                Log.d(TAG, "Evicting '${victim.key}' (${victim.value.length() / 1024}KB)")
                runCatching { victim.value.delete() }
                fileMap.remove(victim.key)
            } else {
                break // All entries are pinned, can't evict
            }
        }
    }

    private fun existingCachedFileLocked(videoId: String): File? {
        val existing = fileMap[videoId] ?: return null
        if (existing.exists() && existing.length() > 0) {
            return existing
        }
        fileMap.remove(videoId)
        runCatching { existing.delete() }
        return null
    }

    private suspend fun resolveStreamUrl(videoId: String): String? {
        val cached = AppCache.getStreamUrl(videoId)
        if (!cached.isNullOrBlank()) return cached

        return runCatching {
            val details = StreamRequestResolver.fetchSongPlaybackDetails(videoId)
            details?.streamUrl?.also { AppCache.putStreamUrl(videoId, it) }
        }.onFailure {
            Log.e(TAG, "Failed to resolve stream URL for '$videoId'", it)
        }.getOrNull()
    }

    private fun String.withFullRange(): String {
        if (contains("range=")) {
            return replace(Regex("range=[^&]*"), "range=0-")
        }
        return if (contains("?")) "$this&range=0-" else "$this?range=0-"
    }

    private fun extensionFromContentType(contentType: String?): String {
        val normalized = contentType?.lowercase(Locale.US).orEmpty()
        return when {
            normalized.contains("audio/webm") -> "webm"
            normalized.contains("audio/mp4") -> "m4a"
            normalized.contains("video/mp4") -> "mp4"
            normalized.contains("audio/mpeg") -> "mp3"
            else -> "m4a"
        }
    }
}
