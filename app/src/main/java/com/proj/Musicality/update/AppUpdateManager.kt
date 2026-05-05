package com.proj.Musicality.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
    private const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/rishi0810/Musicality-App/releases/latest"
    private const val CHANNEL_ID = "app_updates"
    private const val CHANNEL_NAME = "App updates"
    private const val PREFS = "app_update_prefs"
    private const val KEY_LAST_NOTIFIED_VERSION_NAME = "last_notified_version_name"
    private const val KEY_LAST_DISMISSED_DIALOG_VERSION_NAME = "last_dismissed_dialog_version_name"
    private const val UPDATE_NOTIFICATION_ID = 4001
    private const val CHECK_INTERVAL_MILLIS = 2 * 60 * 60 * 1000L

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient()
    private var cachedManifest: UpdateManifest? = null
    private var lastCheckTime = 0L

    @Serializable
    data class UpdateManifest(
        @SerialName("latestVersionCode") val latestVersionCode: Int,
        @SerialName("latestVersionName") val latestVersionName: String,
        @SerialName("apkUrl") val apkUrl: String
    )

    @Serializable
    private data class GithubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("name") val name: String? = null,
        @SerialName("assets") val assets: List<GithubReleaseAsset> = emptyList()
    )

    @Serializable
    private data class GithubReleaseAsset(
        @SerialName("name") val name: String,
        @SerialName("browser_download_url") val browserDownloadUrl: String
    )

    suspend fun checkAndNotify(context: Context) {
        checkForUpdate(context, notify = true)
    }

    suspend fun checkForUpdate(
        context: Context,
        notify: Boolean = true
    ): UpdateManifest? = withContext(Dispatchers.IO) {
        runCatching {
            val manifest = getLatestReleaseManifest() ?: return@runCatching null
            val currentVersionName = currentVersionName(context)

            if (compareVersions(manifest.latestVersionName, currentVersionName) <= 0) return@runCatching null
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

    fun lastDismissedDialogVersionName(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_DISMISSED_DIALOG_VERSION_NAME, null)

    fun markDialogDismissed(context: Context, manifest: UpdateManifest) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_DISMISSED_DIALOG_VERSION_NAME, manifest.latestVersionName)
            .apply()
    }

    private fun showUpdateNotificationOnce(context: Context, manifest: UpdateManifest) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastNotified = prefs.getString(KEY_LAST_NOTIFIED_VERSION_NAME, null)
        if (lastNotified == manifest.latestVersionName) return

        prefs.edit()
            .putString(KEY_LAST_NOTIFIED_VERSION_NAME, manifest.latestVersionName)
            .apply()

        showUpdateNotification(context, manifest)
    }

    private fun getLatestReleaseManifest(): UpdateManifest? {
        val now = System.currentTimeMillis()
        cachedManifest?.let { manifest ->
            if (now - lastCheckTime < CHECK_INTERVAL_MILLIS) return manifest
        }

        val request = Request.Builder()
            .url(GITHUB_LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Musicality-App-Updater")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub release request failed: HTTP ${response.code}")
                return null
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val release = json.decodeFromString<GithubRelease>(body)
            val apkAsset = release.assets.firstOrNull { it.name == "app-release.apk" }
                ?: release.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true) &&
                        it.name.contains("release", ignoreCase = true)
                }
                ?: release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return null

            val manifest = UpdateManifest(
                latestVersionCode = versionCodeFromTag(release.tagName),
                latestVersionName = release.tagName,
                apkUrl = apkAsset.browserDownloadUrl
            )
            cachedManifest = manifest
            lastCheckTime = now
            return manifest
        }
    }

    private fun currentVersionName(context: Context): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return packageInfo.versionName.orEmpty()
    }

    private fun compareVersions(candidate: String, current: String): Int {
        val candidateParts = numericVersionParts(candidate)
        val currentParts = numericVersionParts(current)
        val maxLength = maxOf(candidateParts.size, currentParts.size)
        for (index in 0 until maxLength) {
            val next = candidateParts.getOrElse(index) { 0 }
            val installed = currentParts.getOrElse(index) { 0 }
            if (next != installed) return next.compareTo(installed)
        }
        return 0
    }

    private fun numericVersionParts(version: String): List<Int> =
        version
            .trim()
            .removePrefix("v")
            .split(".", "-", "_")
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }

    private fun versionCodeFromTag(tagName: String): Int {
        val parts = numericVersionParts(tagName)
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return major * 10_000 + minor * 100 + patch
    }

    private fun showUpdateNotification(context: Context, manifest: UpdateManifest) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

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
