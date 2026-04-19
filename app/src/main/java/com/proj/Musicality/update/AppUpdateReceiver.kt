package com.proj.Musicality.update

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import com.proj.Musicality.R

class AppUpdateReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AppUpdateReceiver"

        const val ACTION_DOWNLOAD_UPDATE = "com.proj.Musicality.action.DOWNLOAD_UPDATE"
        const val EXTRA_APK_URL = "extra_apk_url"
        const val EXTRA_VERSION_CODE = "extra_version_code"
        const val EXTRA_VERSION_NAME = "extra_version_name"

        private const val PREFS = "app_update_prefs"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_DOWNLOAD_VERSION_CODE = "download_version_code"

        private const val INSTALL_CHANNEL_ID = "app_updates_install"
        private const val INSTALL_CHANNEL_NAME = "App update installs"
        private const val INSTALL_NOTIFICATION_ID = 4002
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_DOWNLOAD_UPDATE -> handleDownloadUpdate(context, intent)
            DownloadManager.ACTION_DOWNLOAD_COMPLETE -> handleDownloadComplete(context, intent)
        }
    }

    private fun handleDownloadUpdate(context: Context, intent: Intent) {
        val apkUrl = intent.getStringExtra(EXTRA_APK_URL).orEmpty()
        val versionCode = intent.getIntExtra(EXTRA_VERSION_CODE, -1)
        val versionName = intent.getStringExtra(EXTRA_VERSION_NAME).orEmpty()
        if (apkUrl.isBlank() || versionCode <= 0) return

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Musicality update")
            .setDescription("Downloading $versionName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "app-release-$versionName.apk"
            )

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = runCatching { downloadManager.enqueue(request) }
            .onFailure { Log.e(TAG, "Failed to enqueue update download", it) }
            .getOrNull() ?: return

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DOWNLOAD_ID, downloadId)
            .putInt(KEY_DOWNLOAD_VERSION_CODE, versionCode)
            .apply()
    }

    private fun handleDownloadComplete(context: Context, intent: Intent) {
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val expectedId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId != expectedId) return

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val apkUri = runCatching { downloadManager.getUriForDownloadedFile(downloadId) }
            .onFailure { Log.e(TAG, "Failed to get downloaded APK uri", it) }
            .getOrNull() ?: return

        showInstallNotification(context, apkUri)
    }

    private fun showInstallNotification(context: Context, apkUri: Uri) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureInstallChannel(notificationManager)

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, INSTALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.download_24px)
            .setContentTitle("Update downloaded")
            .setContentText("Tap to install the latest version")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        notificationManager.notify(INSTALL_NOTIFICATION_ID, notification)
    }

    private fun ensureInstallChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val existing = notificationManager.getNotificationChannel(INSTALL_CHANNEL_ID)
        if (existing != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                INSTALL_CHANNEL_ID,
                INSTALL_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }
}
