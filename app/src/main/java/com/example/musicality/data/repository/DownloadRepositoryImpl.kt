package com.example.musicality.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.example.musicality.data.local.DownloadedSongDao
import com.example.musicality.data.local.DownloadedSongEntity
import com.example.musicality.domain.model.DownloadedSong
import com.example.musicality.domain.repository.DownloadRepository
import com.example.musicality.service.AudioDownloadService
import com.example.musicality.util.DownloadUtils
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
 * Implementation of DownloadRepository.
 * 
 * Uses ExoPlayer's DownloadManager for robust audio downloads and
 * OkHttp for thumbnail downloads with JPEG compression.
 * 
 * Key features:
 * - Handles YouTube CDN URL expiration via resume with fresh URLs
 * - Compresses thumbnails to minimize storage
 * - Tracks download progress
 * - Supports parallel downloads
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
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
        private const val THUMBNAIL_DIR = "thumbnails"
        private const val THUMBNAIL_QUALITY = 80 // JPEG quality 0-100
        private const val THUMBNAIL_MAX_SIZE = 226 // Max dimension in pixels
        
        // YouTube Android client headers (matching UrlFetchRepositoryImpl)
        private const val USER_AGENT = "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip"
        private const val ACCEPT = "*/*"
        private const val ACCEPT_ENCODING = "gzip, deflate, br"
        private const val CONNECTION = "keep-alive"
    }
    
    /**
     * Get or create the downloads directory.
     */
    private fun getDownloadDir(): File {
        val dir = File(context.getExternalFilesDir(null), DOWNLOAD_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    /**
     * Get or create the thumbnails directory.
     */
    private fun getThumbnailDir(): File {
        val dir = File(context.getExternalFilesDir(null), THUMBNAIL_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    /**
     * Get file extension from MIME type.
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
     * Sanitize filename for file system.
     */
    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)
    }
    
    /**
     * Download and compress thumbnail to local storage.
     * Returns the file path and size.
     */
    private suspend fun downloadThumbnail(videoId: String, thumbnailUrl: String): Pair<String?, Long> = withContext(Dispatchers.IO) {
        try {
            if (thumbnailUrl.isEmpty()) return@withContext Pair(null, 0L)
            
            val thumbnailFile = File(getThumbnailDir(), "${videoId}.jpg")
            
            // Skip if already exists
            if (thumbnailFile.exists() && thumbnailFile.length() > 0) {
                return@withContext Pair(thumbnailFile.absolutePath, thumbnailFile.length())
            }
            
            val request = Request.Builder()
                .url(thumbnailUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36")
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                android.util.Log.e(TAG, "Thumbnail download failed: HTTP ${response.code}")
                return@withContext Pair(null, 0L)
            }
            
            val body = response.body ?: return@withContext Pair(null, 0L)
            
            // Decode the image
            val originalBitmap = BitmapFactory.decodeStream(body.byteStream())
                ?: return@withContext Pair(null, 0L)
            
            // Scale down if needed to save storage
            val scaledBitmap = scaleBitmap(originalBitmap, THUMBNAIL_MAX_SIZE)
            
            // Save as compressed JPEG
            FileOutputStream(thumbnailFile).use { outputStream ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, outputStream)
            }
            
            // Clean up bitmaps
            if (originalBitmap != scaledBitmap) {
                originalBitmap.recycle()
            }
            scaledBitmap.recycle()
            
            val fileSize = thumbnailFile.length()
            android.util.Log.d(TAG, "Thumbnail saved: ${thumbnailFile.absolutePath} (${fileSize / 1024} KB)")
            
            Pair(thumbnailFile.absolutePath, fileSize)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Thumbnail download error", e)
            Pair(null, 0L)
        }
    }
    
    /**
     * Scale bitmap to fit within maxSize while maintaining aspect ratio.
     */
    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }
        
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        
        if (width > height) {
            newWidth = maxSize
            newHeight = (maxSize / ratio).toInt()
        } else {
            newHeight = maxSize
            newWidth = (maxSize * ratio).toInt()
        }
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
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
            
            // 1. Download thumbnail first (quick operation)
            val (thumbnailFilePath, thumbnailFileSize) = downloadThumbnail(videoId, thumbnailUrl)
            
            // 2. Create filename for audio - use videoId for consistent lookup
            val extension = getExtensionFromMimeType(mimeType)
            val filename = "${videoId}${extension}"
            val file = File(getDownloadDir(), filename)
            
            // 3. If file already exists and is valid, skip downloading audio
            if (file.exists() && file.length() > 0) {
                android.util.Log.d(TAG, "Audio file already exists: ${file.absolutePath}")
                val existingSong = downloadedSongDao.getDownloadedSong(videoId)
                if (existingSong != null) {
                    return@withContext Result.success(existingSong.toDomain())
                }
            }
            
            // 4. Prepare download URL - append range=0- for full file download
            // This tells YouTube CDN to return the entire file from the beginning
            val downloadUrl = if (url.contains("range=")) {
                // Replace existing range parameter with range=0-
                url.replace(Regex("&range=\\d+-\\d*"), "&range=0-")
            } else {
                // Append range parameter
                "${url}&range=0-"
            }
            
            android.util.Log.d(TAG, "Download URL with range: ${downloadUrl.take(100)}...")
            
            // 5. Download audio using OkHttp with YouTube Android client headers
            val request = Request.Builder()
                .url(downloadUrl)
                .header("Accept", ACCEPT)
                .header("Accept-Encoding", ACCEPT_ENCODING)
                .header("Connection", CONNECTION)
                .header("User-Agent", USER_AGENT)
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                android.util.Log.e(TAG, "Download failed: HTTP ${response.code}")
                return@withContext Result.failure(Exception("Download failed: HTTP ${response.code}"))
            }
            
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
            android.util.Log.d(TAG, "Audio download complete: ${fileSize / 1024} KB")
            
            // 5. Save to database
            val entity = DownloadedSongEntity(
                videoId = videoId,
                title = title,
                author = author,
                thumbnailUrl = thumbnailUrl,
                thumbnailFilePath = thumbnailFilePath,
                duration = duration,
                channelId = channelId,
                filePath = file.absolutePath,
                fileSize = fileSize,
                thumbnailFileSize = thumbnailFileSize,
                mimeType = mimeType
            )
            
            downloadedSongDao.insertDownloadedSong(entity)
            
            Result.success(entity.toDomain())
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Download error", e)
            Result.failure(e)
        }
    }
    
    override fun startDownload(
        videoId: String,
        url: String,
        title: String,
        mimeType: String
    ) {
        // Remove the range parameter if present - let DownloadManager handle chunking
        val cleanUrl = url.replace(Regex("&range=\\d+-\\d+"), "")
        
        // Determine MIME type for ExoPlayer
        val exoMimeType = when {
            mimeType.contains("opus") || mimeType.contains("webm") -> MimeTypes.AUDIO_WEBM
            mimeType.contains("mp4") || mimeType.contains("m4a") -> MimeTypes.AUDIO_MP4
            mimeType.contains("mp3") -> MimeTypes.AUDIO_MPEG
            else -> MimeTypes.AUDIO_WEBM
        }
        
        val downloadRequest = DownloadRequest.Builder(videoId, Uri.parse(cleanUrl))
            .setMimeType(exoMimeType)
            .setData(title.toByteArray()) // Store title for notification
            .build()
        
        // Send to download service
        DownloadService.sendAddDownload(
            context,
            AudioDownloadService::class.java,
            downloadRequest,
            /* foreground= */ true
        )
        
        android.util.Log.d(TAG, "Started ExoPlayer download for: $title ($videoId)")
    }
    
    override fun resumeDownloadWithFreshUrl(videoId: String, freshUrl: String) {
        // Remove the range parameter
        val cleanUrl = freshUrl.replace(Regex("&range=\\d+-\\d+"), "")
        
        // Get existing download to get the MIME type
        val downloadManager = DownloadUtils.getDownloadManager(context)
        val existingDownload = downloadManager.downloadIndex.getDownload(videoId)
        
        val mimeType = existingDownload?.request?.mimeType ?: MimeTypes.AUDIO_WEBM
        val title = existingDownload?.request?.data?.let { String(it) } ?: "Song"
        
        val downloadRequest = DownloadRequest.Builder(videoId, Uri.parse(cleanUrl))
            .setMimeType(mimeType)
            .setData(title.toByteArray())
            .build()
        
        // This will resume from where it left off using the same videoId
        DownloadService.sendAddDownload(
            context,
            AudioDownloadService::class.java,
            downloadRequest,
            /* foreground= */ true
        )
        
        android.util.Log.d(TAG, "Resumed download with fresh URL for: $videoId")
    }
    
    override suspend fun deleteDownloadedSong(videoId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Get file paths before deleting from DB
            val song = downloadedSongDao.getDownloadedSong(videoId)
            
            // Delete audio file
            song?.filePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                    android.util.Log.d(TAG, "Deleted audio file: $path")
                }
            }
            
            // Delete thumbnail file
            song?.thumbnailFilePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                    android.util.Log.d(TAG, "Deleted thumbnail: $path")
                }
            }
            
            // Remove from ExoPlayer's download cache
            DownloadUtils.removeDownload(context, videoId)
            
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
    
    override fun getDownloadProgress(videoId: String): Float {
        return DownloadUtils.getDownloadProgress(context, videoId)
    }
    
    override fun getTotalStorageUsed(): Flow<Long> {
        return downloadedSongDao.getTotalStorageUsed()
    }
    
    override fun getDownloadedSongsCount(): Flow<Int> {
        return downloadedSongDao.getDownloadedSongsCount()
    }
    
    /**
     * Extension function to convert entity to domain model.
     */
    private fun DownloadedSongEntity.toDomain(): DownloadedSong {
        return DownloadedSong(
            videoId = videoId,
            title = title,
            author = author,
            thumbnailUrl = thumbnailUrl,
            thumbnailFilePath = thumbnailFilePath,
            duration = duration,
            channelId = channelId,
            filePath = filePath,
            fileSize = fileSize,
            thumbnailFileSize = thumbnailFileSize,
            mimeType = mimeType,
            downloadedAt = downloadedAt
        )
    }
}
