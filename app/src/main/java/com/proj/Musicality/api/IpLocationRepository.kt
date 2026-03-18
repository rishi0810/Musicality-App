package com.proj.Musicality.api

import android.content.Context
import android.util.Log
import com.proj.Musicality.data.json.IpLocationResponse
import com.proj.Musicality.data.parser.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches and persists the user's country code via IP geolocation.
 * The fetch happens exactly once: on first launch. Subsequent calls return the cached value.
 *
 * Default country code when the fetch fails or has not run yet: "ZZ" (Global).
 */
object IpLocationRepository {
    private const val TAG = "IpLocationRepository"
    private const val PREFS = "ip_location_prefs"
    private const val KEY_COUNTRY_CODE = "country_code"
    private const val KEY_COUNTRY_NAME = "country_name"
    private const val IP_API_URL = "https://free.freeipapi.com/api/json"

    fun getCountryCode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_COUNTRY_CODE, "ZZ") ?: "ZZ"

    fun getCountryName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_COUNTRY_NAME, "Global") ?: "Global"

    /**
     * Fetches the country code from the IP API and caches it permanently.
     * No-op if already cached. Safe to call from any coroutine scope.
     */
    suspend fun fetchAndCacheOnce(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_COUNTRY_CODE)) {
            Log.d(TAG, "fetchAndCacheOnce: already cached, skipping")
            return@withContext
        }

        val raw = runCatching { RequestExecutor.executeGetRequest(IP_API_URL) }
            .onFailure { Log.e(TAG, "fetchAndCacheOnce: request failed", it) }
            .getOrDefault("")

        if (raw.isBlank()) {
            Log.w(TAG, "fetchAndCacheOnce: empty response, defaulting to ZZ")
            return@withContext
        }

        val response = runCatching {
            JsonParser.instance.decodeFromString<IpLocationResponse>(raw)
        }.onFailure { Log.e(TAG, "fetchAndCacheOnce: parse failed", it) }
            .getOrNull()

        val code = response?.countryCode
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.length == 2 }
            ?: "ZZ"

        prefs.edit()
            .putString(KEY_COUNTRY_CODE, code)
            .apply()

        Log.d(TAG, "fetchAndCacheOnce: cached countryCode=$code")
    }
}
