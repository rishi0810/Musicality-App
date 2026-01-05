package com.example.musicality.data.repository

import android.content.Context
import com.example.musicality.domain.model.UrlFetchResult
import com.example.musicality.domain.repository.UrlFetchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Implementation of UrlFetchRepository that uses OkHttp to fetch URLs
 * with custom YouTube Android client headers and saves responses locally.
 */
class UrlFetchRepositoryImpl(
    private val context: Context
) : UrlFetchRepository {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        private const val DOWNLOAD_DIR = "url_downloads"
        
        // YouTube Android client headers
        private const val USER_AGENT = "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip"
        private const val ACCEPT = "*/*"
        private const val ACCEPT_ENCODING = "gzip, deflate, br"
        private const val CONNECTION = "keep-alive"
    }

    override suspend fun fetchAndSave(url: String): Result<UrlFetchResult> = withContext(Dispatchers.IO) {
        try {
            // Generate a random file code
            val fileCode = UUID.randomUUID().toString().take(8)
            
            // Build request with required headers
            val request = Request.Builder()
                .url(url)
                .header("Accept", ACCEPT)
                .header("Accept-Encoding", ACCEPT_ENCODING)
                .header("Connection", CONNECTION)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            // Execute request
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("HTTP Error: ${response.code} - ${response.message}")
                )
            }

            // Extract response headers
            val headers = mutableMapOf<String, String>()
            response.headers.forEach { (name, value) ->
                headers[name] = value
            }

            // Get content type and determine file extension
            val contentType = response.header("Content-Type") ?: "application/octet-stream"
            val fileExtension = getFileExtension(contentType)
            val fileName = "download_${fileCode}${fileExtension}"

            // Create download directory
            val downloadDir = File(context.getExternalFilesDir(null), DOWNLOAD_DIR)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            // Save response body to file
            val filePath = File(downloadDir, fileName).absolutePath
            val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L

            response.body?.let { body ->
                FileOutputStream(filePath).use { fos ->
                    body.byteStream().use { inputStream ->
                        inputStream.copyTo(fos)
                    }
                }
            }

            val result = UrlFetchResult(
                success = true,
                responseHeaders = headers,
                fileCode = fileCode,
                fileName = fileName,
                filePath = filePath,
                contentLength = contentLength,
                contentType = contentType
            )

            Result.success(result)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Determines file extension based on content type
     */
    private fun getFileExtension(contentType: String): String {
        return when {
            contentType.contains("audio/webm") -> ".webm"
            contentType.contains("audio/mp4") -> ".m4a"
            contentType.contains("audio/mpeg") -> ".mp3"
            contentType.contains("audio/ogg") -> ".ogg"
            contentType.contains("audio/aac") -> ".aac"
            contentType.contains("audio/") -> ".audio"
            contentType.contains("video/webm") -> ".webm"
            contentType.contains("video/mp4") -> ".mp4"
            contentType.contains("video/") -> ".video"
            else -> ".bin"
        }
    }
}
