package com.proj.Musicality.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.proj.Musicality.MainActivity
import com.proj.Musicality.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val MANIFEST_URL =
        "https://rishi0810.github.io/static-api/app-update/android.json"
    private const val CHANNEL_ID = "app_updates"
    private const val CHANNEL_NAME = "App updates"
    private const val PREFS = "app_update_prefs"
    private const val KEY_LAST_NOTIFIED_VERSION_CODE = "last_notified_version_code"
    private const val UPDATE_NOTIFICATION_ID = 4001

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient()

    @Serializable
    data class UpdateManifest(
        @SerialName("latestVersionCode") val latestVersionCode: Int,
        @SerialName("latestVersionName") val latestVersionName: String,
        @SerialName("apkUrl") val apkUrl: String
    )

    suspend fun checkAndNotify(context: Context) {
        checkForUpdate(context, notify = true)
    }

    suspend fun checkForUpdate(
        context: Context,
        notify: Boolean = true
    ): UpdateManifest? = withContext(Dispatchers.IO) {
        runCatching {
            val manifest = fetchManifest() ?: return@runCatching null
            val currentVersionCode = currentVersionCode(context)

            if (manifest.latestVersionCode <= currentVersionCode) return@runCatching null
            if (manifest.apkUrl.isBlank()) return@runCatching null

            if (notify) {
                showUpdateNotificationOnce(context, manifest)
            }

            manifest
        }.onFailure { error ->
            Log.e(TAG, "checkForUpdate failed", error)
        }.getOrNull()
    }

    fun downloadUpdate(context: Context, manifest: UpdateManifest) {
        val downloadIntent = Intent(context, AppUpdateReceiver::class.java).apply {
            action = AppUpdateReceiver.ACTION_DOWNLOAD_UPDATE
            putExtra(AppUpdateReceiver.EXTRA_APK_URL, manifest.apkUrl)
            putExtra(AppUpdateReceiver.EXTRA_VERSION_CODE, manifest.latestVersionCode)
            putExtra(AppUpdateReceiver.EXTRA_VERSION_NAME, manifest.latestVersionName)
        }
        context.sendBroadcast(downloadIntent)
    }

    private fun showUpdateNotificationOnce(context: Context, manifest: UpdateManifest) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastNotified = prefs.getInt(KEY_LAST_NOTIFIED_VERSION_CODE, -1)
        if (lastNotified >= manifest.latestVersionCode) return

        prefs.edit()
            .putInt(KEY_LAST_NOTIFIED_VERSION_CODE, manifest.latestVersionCode)
            .apply()

        showUpdateNotification(context, manifest)
    }

    private fun fetchManifest(): UpdateManifest? {
        val request = Request.Builder().url(MANIFEST_URL).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Manifest request failed: HTTP ${response.code}")
                return null
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            return json.decodeFromString<UpdateManifest>(body)
        }
    }

    private fun currentVersionCode(context: Context): Int {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
    }

    private fun showUpdateNotification(context: Context, manifest: UpdateManifest) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(notificationManager)

        val launchIntent = Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val downloadIntent = Intent(context, AppUpdateReceiver::class.java).apply {
            action = AppUpdateReceiver.ACTION_DOWNLOAD_UPDATE
            putExtra(AppUpdateReceiver.EXTRA_APK_URL, manifest.apkUrl)
            putExtra(AppUpdateReceiver.EXTRA_VERSION_CODE, manifest.latestVersionCode)
            putExtra(AppUpdateReceiver.EXTRA_VERSION_NAME, manifest.latestVersionName)
        }
        val downloadPendingIntent = PendingIntent.getBroadcast(
            context,
            manifest.latestVersionCode,
            downloadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.download_24px)
            .setContentTitle("Update available")
            .setContentText("${manifest.latestVersionName} is ready to download")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Download", downloadPendingIntent)
            .build()

        notificationManager.notify(UPDATE_NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }
}
