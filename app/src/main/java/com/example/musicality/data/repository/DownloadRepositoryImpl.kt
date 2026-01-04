package com.example.musicality.data.repository

import android.content.Context
import com.example.musicality.data.local.DownloadedSongDao
import com.example.musicality.data.local.DownloadedSongEntity
import com.example.musicality.domain.model.DownloadedSong
import com.example.musicality.domain.repository.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Implementation of DownloadRepository using OkHttp for downloading
 * and Room for metadata storage
 */
class DownloadRepositoryImpl(
    private val context: Context,
    private val downloadedSongDao: DownloadedSongDao,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()
) : DownloadRepository {
    
    companion object {
        private const val TAG = "DownloadRepository"
        private const val DOWNLOAD_DIR = "downloaded_songs"
    }
    
    /**
     * Get or create the downloads directory
     */
    private fun getDownloadDir(): File {
        val dir = File(context.getExternalFilesDir(null), DOWNLOAD_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Get file extension from MIME type
     */
    private fun getExtensionFromMimeType(mimeType: String): String {
        return when {
            mimeType.contains("opus") || mimeType.contains("webm") -> ".opus"
            mimeType.contains("mp4") || mimeType.contains("m4a") -> ".m4a"
            mimeType.contains("mp3") -> ".mp3"
            else -> ".opus"
        }
    }
    
    /**
     * Sanitize filename for file system
     */
    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)
    }
    
    override suspend fun downloadSong(
        videoId: String,
        url: String,
        title: String,
        author: String,
        thumbnailUrl: String,
        duration: String,
        channelId: String,
        mimeType: String
    ): Result<DownloadedSong> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d(TAG, "Starting download for: $title ($videoId)")
            
            // Create filename
            val extension = getExtensionFromMimeType(mimeType)
            val sanitizedTitle = sanitizeFilename(title)
            val filename = "${sanitizedTitle}_$videoId$extension"
            val file = File(getDownloadDir(), filename)
            
            // If file already exists and is valid, skip downloading
            if (file.exists() && file.length() > 0) {
                android.util.Log.d(TAG, "File already exists: ${file.absolutePath}")
                val existingSong = downloadedSongDao.getDownloadedSong(videoId)
                if (existingSong != null) {
                    return@withContext Result.success(existingSong.toDomain())
                }
            }
            
            // Build the request
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36")
                .build()
            
            // Execute the request
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                android.util.Log.e(TAG, "Download failed: HTTP ${response.code}")
                return@withContext Result.failure(Exception("Download failed: HTTP ${response.code}"))
            }
            
            // Download the file
            val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
            val contentLength = body.contentLength()
            
            android.util.Log.d(TAG, "Downloading ${contentLength / 1024} KB to ${file.absolutePath}")
            
            FileOutputStream(file).use { outputStream ->
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // Log progress every 1MB
                        if (totalBytesRead % (1024 * 1024) < 8192) {
                            android.util.Log.d(TAG, "Downloaded: ${totalBytesRead / 1024} KB")
                        }
                    }
                }
            }
            
            val fileSize = file.length()
            android.util.Log.d(TAG, "Download complete: ${fileSize / 1024} KB")
            
            // Save to database
            val entity = DownloadedSongEntity(
                videoId = videoId,
                title = title,
                author = author,
                thumbnailUrl = thumbnailUrl,
                duration = duration,
                channelId = channelId,
                filePath = file.absolutePath,
                fileSize = fileSize,
                mimeType = mimeType
            )
            
            downloadedSongDao.insertDownloadedSong(entity)
            
            Result.success(entity.toDomain())
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Download error", e)
            Result.failure(e)
        }
    }
    
    override suspend fun deleteDownloadedSong(videoId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Get the file path
            val filePath = downloadedSongDao.getFilePath(videoId)
            
            // Delete the file
            filePath?.let {
                val file = File(it)
                if (file.exists()) {
                    file.delete()
                    android.util.Log.d(TAG, "Deleted file: $it")
                }
            }
            
            // Delete from database
            downloadedSongDao.deleteByVideoId(videoId)
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Delete error", e)
            Result.failure(e)
        }
    }
    
    override suspend fun isDownloaded(videoId: String): Boolean {
        return downloadedSongDao.isDownloaded(videoId)
    }
    
    override fun isDownloadedFlow(videoId: String): Flow<Boolean> {
        return downloadedSongDao.isDownloadedFlow(videoId)
    }
    
    override fun getAllDownloadedSongsFlow(): Flow<List<DownloadedSong>> {
        return downloadedSongDao.getAllDownloadedSongsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getDownloadedSong(videoId: String): DownloadedSong? {
        return downloadedSongDao.getDownloadedSong(videoId)?.toDomain()
    }
    
    override suspend fun getFilePath(videoId: String): String? {
        return downloadedSongDao.getFilePath(videoId)
    }
    
    /**
     * Extension function to convert entity to domain model
     */
    private fun DownloadedSongEntity.toDomain(): DownloadedSong {
        return DownloadedSong(
            videoId = videoId,
            title = title,
            author = author,
            thumbnailUrl = thumbnailUrl,
            duration = duration,
            channelId = channelId,
            filePath = filePath,
            fileSize = fileSize,
            mimeType = mimeType,
            downloadedAt = downloadedAt
        )
    }
}
