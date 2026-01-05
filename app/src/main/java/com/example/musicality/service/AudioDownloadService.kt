package com.example.musicality.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.NotificationUtil
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.example.musicality.R
import com.example.musicality.util.DownloadUtils

/**
 * Background service for downloading songs using ExoPlayer's DownloadManager.
 * 
 * This service:
 * - Runs as a foreground service to keep downloads active when app is in background
 * - Shows a notification with download progress
 * - Handles URL expiration gracefully (can resume with refreshed URLs)
 * - Supports multiple parallel downloads
 *
 * Based on ExoPlayer's recommended download architecture for handling
 * YouTube-style CDN URLs that expire after a few hours.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description
) {
    
    companion object {
        private const val TAG = "AudioDownloadService"
        private const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 2
    }
    
    private lateinit var notificationHelper: DownloadNotificationHelper
    
    // Listener to handle download state changes
    private val downloadListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            handleDownloadChanged(download, finalException)
        }
        
        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            android.util.Log.d(TAG, "Download removed: ${download.request.id}")
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationHelper = DownloadNotificationHelper(this, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
        
        // Add listener to track download changes
        val downloadManager = DownloadUtils.getDownloadManager(this)
        downloadManager.addListener(downloadListener)
    }
    
    override fun onDestroy() {
        val downloadManager = DownloadUtils.getDownloadManager(this)
        downloadManager.removeListener(downloadListener)
        super.onDestroy()
    }
    
    /**
     * Create the notification channel for download notifications.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.download_channel_description)
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Return the DownloadManager from our DownloadUtils singleton.
     */
    override fun getDownloadManager(): DownloadManager {
        return DownloadUtils.getDownloadManager(this)
    }
    
    /**
     * No scheduler needed - downloads run in foreground service.
     * Could use PlatformScheduler for API 21+ if background scheduling is needed.
     */
    override fun getScheduler(): Scheduler? = null
    
    /**
     * Build the foreground notification showing download progress.
     */
    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        // Filter to only show active downloads
        val activeDownloads = downloads.filter { 
            it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED 
        }
        
        return if (activeDownloads.isNotEmpty()) {
            notificationHelper.buildProgressNotification(
                this,
                R.drawable.download_for_24px,
                null, // No pending intent needed, handled by main app
                getDownloadStatusMessage(activeDownloads),
                activeDownloads,
                notMetRequirements
            )
        } else {
            // Fallback notification when no active downloads
            NotificationCompat.Builder(this, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.download_for_24px)
                .setContentTitle("Downloads")
                .setContentText("Processing downloads...")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }
    
    /**
     * Generate a human-readable status message for the notification.
     */
    private fun getDownloadStatusMessage(downloads: List<Download>): String {
        return when {
            downloads.isEmpty() -> "No active downloads"
            downloads.size == 1 -> {
                val download = downloads[0]
                // Extract song title from the custom data we stored
                val title = download.request.data?.let { String(it) } ?: "Song"
                val progress = download.percentDownloaded.toInt()
                "$title ($progress%)"
            }
            else -> "Downloading ${downloads.size} songs..."
        }
    }
    
    /**
     * Handle download state changes - show completion/failure notifications.
     */
    private fun handleDownloadChanged(download: Download, finalException: Exception?) {
        when (download.state) {
            Download.STATE_COMPLETED -> {
                android.util.Log.d(TAG, "Download completed: ${download.request.id}")
                // Notify listeners that download is complete
                NotificationUtil.setNotification(
                    this,
                    download.request.id.hashCode(),
                    notificationHelper.buildDownloadCompletedNotification(
                        this,
                        R.drawable.download_for_filled_24px,
                        null,
                        download.request.data?.let { String(it) } ?: "Song downloaded"
                    )
                )
            }
            Download.STATE_FAILED -> {
                android.util.Log.e(TAG, "Download failed: ${download.request.id}", finalException)
                NotificationUtil.setNotification(
                    this,
                    download.request.id.hashCode(),
                    notificationHelper.buildDownloadFailedNotification(
                        this,
                        R.drawable.download_for_24px,
                        null,
                        download.request.data?.let { String(it) } ?: "Download failed"
                    )
                )
            }
            else -> {
                android.util.Log.d(TAG, "Download state changed: ${download.request.id} -> ${download.state}")
            }
        }
    }
}
