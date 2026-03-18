package com.proj.Musicality.cache

import android.content.Context
import java.io.File

/**
 * File-based disk cache for the Explore screen with a 7-day TTL.
 *
 * JSON responses are written to `cacheDir/explore/<key>.json`.
 * Timestamps are stored in SharedPreferences so TTL checks survive process restarts.
 *
 * On pull-to-refresh, call [invalidate] to clear all entries; the next
 * [initialize] / [get] will trigger a fresh network fetch.
 */
object ExploreDiskCache {

    private const val PREFS = "explore_disk_cache_ts"
    private const val TTL_MS = 7L * 24 * 60 * 60 * 1000   // 7 days
    private const val DIR = "explore"

    fun get(context: Context, key: String): String? {
        val ts = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(key, 0L)
        if (System.currentTimeMillis() - ts > TTL_MS) return null

        val file = file(context, key)
        return if (file.exists()) file.readText().takeIf { it.isNotBlank() } else null
    }

    fun put(context: Context, key: String, json: String) {
        val file = file(context, key)
        file.parentFile?.mkdirs()
        file.writeText(json)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(key, System.currentTimeMillis()).apply()
    }

    /** Clears all cached entries and their timestamps. */
    fun invalidate(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
        file(context, "").parentFile?.deleteRecursively()
    }

    private fun file(context: Context, key: String): File =
        File(context.cacheDir, "$DIR/$key.json")
}
