package com.proj.Musicality.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object RequestExecutor {
    private const val TAG = "RequestExecutor"
    private val VISITOR_ID_REGEX = Regex("""Cg[a-zA-Z0-9%_-]{20,}""")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun executeBrowseRequest(browseId: String, visitorId: String, params: String? = null): String = withContext(Dispatchers.IO) {
        val paramsField = if (params != null) ""","params":"$params"""" else ""
        val body = """
            {"context":{"client":{"clientName":"${ApiConstants.WEB_REMIX_CLIENT_NAME}","clientVersion":"${ApiConstants.WEB_REMIX_CLIENT_VERSION}","gl":"US","hl":"en","visitorData":"$visitorId"},"request":{"internalExperimentFlags":[],"useSsl":true},"user":{"lockedSafetyMode":false}},"browseId":"$browseId"$paramsField}
        """.trimIndent().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(ApiConstants.BROWSE_URL)
            .addHeader("User-Agent", ApiConstants.WEB_REMIX_USER_AGENT)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-visitor-id", visitorId)
            .addHeader("x-goog-api-format-version", "1")
            .addHeader("x-youtube-client-name", "67")
            .addHeader("x-youtube-client-version", ApiConstants.WEB_REMIX_CLIENT_VERSION)
            .addHeader("x-origin", "https://music.youtube.com")
            .addHeader("referer", "https://music.youtube.com/")
            .addHeader("accept", "application/json")
            .addHeader("accept-language", "en-US,en;q=0.9")
            .addHeader("cache-control", "no-cache")
            .addHeader("accept-charset", "UTF-8")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            response.body?.string() ?: ""
        }
    }

    suspend fun executeRelatedBrowseRequest(browseId: String, visitorId: String): String =
        withContext(Dispatchers.IO) {
            val body = """
                {"context":{"client":{"hl":"en","gl":"US","visitorData":"$visitorId","clientName":"${ApiConstants.WEB_REMIX_CLIENT_NAME}","clientVersion":"${ApiConstants.RELATED_CLIENT_VERSION}"}},"browseId":"$browseId"}
            """.trimIndent().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(ApiConstants.BROWSE_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", ApiConstants.RELATED_USER_AGENT)
                .addHeader("X-Youtube-Client-Name", "67")
                .addHeader("X-Youtube-Client-Version", ApiConstants.RELATED_CLIENT_VERSION)
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("Referer", "https://music.youtube.com/")
                .addHeader("X-Goog-Visitor-Id", visitorId)
                .addHeader("accept", "application/json")
                .addHeader("accept-language", "en-US,en;q=0.9")
                .addHeader("cache-control", "no-cache")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "executeRelatedBrowseRequest: HTTP ${response.code}, body length=${responseBody.length}")
                responseBody
            }
        }

    /**
     * Fetch the next page of a browse feed (home, explore, etc.) using a
     * continuation token returned by the previous response.
     *
     * The token is sent in the POST body as `"continuation"` alongside the
     * standard context block. The response is parsed by
     * [HomeParser.extractContinuation].
     */
    suspend fun executeBrowseContinuationRequest(continuation: String, visitorId: String): String =
        withContext(Dispatchers.IO) {
            val body = """
                {"context":{"client":{"clientName":"${ApiConstants.WEB_REMIX_CLIENT_NAME}","clientVersion":"${ApiConstants.WEB_REMIX_CLIENT_VERSION}","gl":"US","hl":"en","visitorData":"$visitorId"},"request":{"internalExperimentFlags":[],"useSsl":true},"user":{"lockedSafetyMode":false}},"continuation":"$continuation"}
            """.trimIndent().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(ApiConstants.BROWSE_URL)
                .addHeader("User-Agent", ApiConstants.WEB_REMIX_USER_AGENT)
                .addHeader("Content-Type", "application/json")
                .addHeader("x-goog-visitor-id", visitorId)
                .addHeader("x-goog-api-format-version", "1")
                .addHeader("x-youtube-client-name", "67")
                .addHeader("x-youtube-client-version", ApiConstants.WEB_REMIX_CLIENT_VERSION)
                .addHeader("x-origin", "https://music.youtube.com")
                .addHeader("referer", "https://music.youtube.com/")
                .addHeader("accept", "application/json")
                .addHeader("accept-language", "en-US,en;q=0.9")
                .addHeader("cache-control", "no-cache")
                .addHeader("accept-charset", "UTF-8")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                response.body?.string() ?: ""
            }
        }

    suspend fun executeNextRequest(videoId: String, visitorId: String): String = withContext(Dispatchers.IO) {
        val body = """
            {
              "videoId": "$videoId",
              "playlistId": "RDAMVM$videoId",
              "isAudioOnly": true,
              "responsiveSignals": {
                "videoInteraction": [
                  {
                    "queueImpress": {},
                    "videoId": "$videoId",
                    "queueIndex": 0
                  }
                ]
              },
              "context": {
                "client": {
                  "visitorData": "$visitorId",
                  "clientName": "${ApiConstants.WEB_REMIX_CLIENT_NAME}",
                  "clientVersion": "${ApiConstants.WEB_REMIX_CLIENT_VERSION}",
                  "gl": "US",
                  "hl": "en"
                },
                "user": {
                  "lockedSafetyMode": false
                },
                "request": {
                  "useSsl": true,
                  "internalExperimentFlags": []
                }
              }
            }
        """.trimIndent().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(ApiConstants.NEXT_URL)
            .addHeader("User-Agent", ApiConstants.WEB_REMIX_USER_AGENT)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-visitor-id", visitorId)
            .addHeader("x-goog-api-format-version", "1")
            .addHeader("x-youtube-client-name", "67")
            .addHeader("x-youtube-client-version", ApiConstants.WEB_REMIX_CLIENT_VERSION)
            .addHeader("x-origin", "https://music.youtube.com")
            .addHeader("referer", "https://music.youtube.com/")
            .addHeader("accept", "application/json")
            .addHeader("accept-language", "en-US,en;q=0.9")
            .addHeader("cache-control", "no-cache")
            .addHeader("accept-charset", "UTF-8")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val code = response.code
            val responseBody = response.body?.string() ?: ""
            Log.d(TAG, "executeNextRequest: HTTP $code, body length=${responseBody.length}")
            if (code != 200) {
                Log.e(TAG, "executeNextRequest: NON-200 response: $code")
                Log.e(TAG, "executeNextRequest: response body (first 500): ${responseBody.take(500)}")
            }
            responseBody
        }
    }

    suspend fun executeSearchRequest(query: String, params: String, visitorId: String): String = withContext(Dispatchers.IO) {
        val body = """
            {"context":{"client":{"clientName":"${ApiConstants.WEB_REMIX_CLIENT_NAME}","clientVersion":"${ApiConstants.WEB_REMIX_CLIENT_VERSION}"}},"query":"$query","params":"$params"}
        """.trimIndent().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(ApiConstants.SEARCH_URL)
            .addHeader("User-Agent", ApiConstants.WEB_REMIX_USER_AGENT)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-visitor-id", visitorId)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            response.body?.string() ?: ""
        }
    }

    suspend fun executeSearchAllRequest(query: String, visitorId: String): String = withContext(Dispatchers.IO) {
        val body = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB_REMIX",
                        "clientVersion": "1.20260213.01.00",
                        "gl": "US",
                        "hl": "en",
                        "visitorData": "$visitorId"
                    },
                    "request": {
                        "internalExperimentFlags": [],
                        "useSsl": true
                    },
                    "user": {
                        "lockedSafetyMode": false
                    }
                },
                "query": "$query"
            }
        """.trimIndent().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(ApiConstants.SEARCH_URL + "?prettyPrint=false")
            .addHeader("x-goog-api-format-version", "1")
            .addHeader("x-youtube-client-name", "67")
            .addHeader("x-youtube-client-version", "1.20260213.01.00")
            .addHeader("x-origin", "https://music.youtube.com")
            .addHeader("referer", "https://music.youtube.com/")
            .addHeader("x-goog-visitor-id", visitorId)
            .addHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
            .addHeader("accept", "application/json")
            .addHeader("accept-language", "en-US,en;q=0.9")
            .addHeader("cache-control", "no-cache")
            .addHeader("content-type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            response.body?.string() ?: ""
        }
    }


    suspend fun executeSuggestionRequest(input: String, visitorId: String): String = withContext(Dispatchers.IO) {
        val body = """
            {"context":{"client":{"clientName":"${ApiConstants.WEB_REMIX_CLIENT_NAME}","clientVersion":"${ApiConstants.WEB_REMIX_CLIENT_VERSION}","gl":"US","hl":"en","visitorData":"$visitorId"},"request":{"internalExperimentFlags":[],"useSsl":true},"user":{"lockedSafetyMode":false}},"input":"$input"}
        """.trimIndent().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(ApiConstants.SUGGESTION_URL)
            .addHeader("User-Agent", ApiConstants.WEB_REMIX_USER_AGENT)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-visitor-id", visitorId)
            .addHeader("x-goog-api-format-version", "1")
            .addHeader("x-youtube-client-name", "67")
            .addHeader("x-youtube-client-version", ApiConstants.WEB_REMIX_CLIENT_VERSION)
            .addHeader("x-origin", "https://music.youtube.com")
            .addHeader("referer", "https://music.youtube.com/")
            .addHeader("accept", "application/json")
            .addHeader("accept-language", "en-US,en;q=0.9")
            .addHeader("cache-control", "no-cache")
            .addHeader("accept-charset", "UTF-8")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            response.body?.string() ?: ""
        }
    }

    suspend fun executePlayerRequest(videoId: String, visitorId: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "executePlayerRequest: videoId='$videoId', visitorId='${visitorId.take(20)}...' (${visitorId.length} chars)")
        if (visitorId.isBlank()) {
            Log.e(TAG, "executePlayerRequest: WARNING - visitorId is BLANK! Request will likely fail.")
        }

        val body = """{"context":{"client":{"clientName":"${ApiConstants.ANDROID_VR_CLIENT_NAME}","clientVersion":"${ApiConstants.ANDROID_VR_CLIENT_VERSION}","osName":"Android","osVersion":"12","deviceMake":"Oculus","deviceModel":"Quest 3","androidSdkVersion":"32","gl":"US","hl":"en","visitorData":"$visitorId"},"request":{"internalExperimentFlags":[],"useSsl":true},"user":{"lockedSafetyMode":false}},"videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true}"""
            .toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(ApiConstants.PLAYER_URL)
            .addHeader("x-goog-api-format-version", "1")
            .addHeader("x-youtube-client-name", "28")
            .addHeader("x-youtube-client-version", ApiConstants.ANDROID_VR_CLIENT_VERSION)
            .addHeader("x-origin", "https://music.youtube.com")
            .addHeader("referer", "https://music.youtube.com/")
            .addHeader("x-goog-visitor-id", visitorId)
            .addHeader("user-agent", ApiConstants.ANDROID_VR_USER_AGENT)
            .addHeader("accept", "application/json")
            .addHeader("accept-language", "en-US,en;q=0.9")
            .addHeader("cache-control", "no-cache")
            .addHeader("content-type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val code = response.code
            val responseBody = response.body?.string() ?: ""
            Log.d(TAG, "executePlayerRequest: HTTP $code, body length=${responseBody.length}")
            if (code != 200) {
                Log.e(TAG, "executePlayerRequest: NON-200 response: $code")
                Log.e(TAG, "executePlayerRequest: response body (first 500): ${responseBody.take(500)}")
            } else {
                Log.d(TAG, "executePlayerRequest: response body (first 300): ${responseBody.take(300)}")
            }
            responseBody
        }
    }

    // ── Explore: mood category & charts ───────────────────────────────────────

    /**
     * Fetches a mood/genre category page.
     * browseId = "FEmusic_moods_and_genres_category", params = one of [MoodCategoryParser.Mood].
     */
    suspend fun executeMoodCategoryRequest(params: String, visitorId: String): String =
        withContext(Dispatchers.IO) {
            val body = """{"context":{"client":{"clientName":"${ApiConstants.WEB_REMIX_CLIENT_NAME}","clientVersion":"${ApiConstants.WEB_REMIX_CLIENT_VERSION}","gl":"US","hl":"en","visitorData":"$visitorId"},"request":{"internalExperimentFlags":[],"useSsl":true},"user":{"lockedSafetyMode":false}},"browseId":"FEmusic_moods_and_genres_category","params":"$params"}"""
                .toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(ApiConstants.BROWSE_URL)
                .addHeader("User-Agent", ApiConstants.WEB_REMIX_USER_AGENT)
                .addHeader("Content-Type", "application/json")
                .addHeader("x-goog-visitor-id", visitorId)
                .addHeader("x-goog-api-format-version", "1")
                .addHeader("x-youtube-client-name", "67")
                .addHeader("x-youtube-client-version", ApiConstants.WEB_REMIX_CLIENT_VERSION)
                .addHeader("x-origin", "https://music.youtube.com")
                .addHeader("referer", "https://music.youtube.com/")
                .addHeader("accept", "application/json")
                .addHeader("accept-language", "en-US,en;q=0.9")
                .addHeader("cache-control", "no-cache")
                .addHeader("accept-charset", "UTF-8")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                response.body?.string() ?: ""
            }
        }

    /**
     * Fetches the charts page for a given country code.
     * browseId = "FEmusic_charts", countryCode = ISO 3166-1 alpha-2 (or "ZZ" for Global).
     */
    suspend fun executeChartsRequest(countryCode: String, visitorId: String): String =
        withContext(Dispatchers.IO) {
            val body = """{"context":{"client":{"clientName":"${ApiConstants.WEB_REMIX_CLIENT_NAME}","clientVersion":"${ApiConstants.WEB_REMIX_CLIENT_VERSION}","gl":"US","hl":"en","visitorData":"$visitorId"},"request":{"internalExperimentFlags":[]},"user":{"enableSafetyMode":false}},"browseId":"FEmusic_charts","params":"sgYPRkVtdXNpY19leHBsb3Jl","navigationType":"BROWSE_NAVIGATION_TYPE_LOAD_IN_PLACE","formData":{"selectedValues":["$countryCode"]}}"""
                .toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(ApiConstants.BROWSE_URL)
                .addHeader("User-Agent", ApiConstants.WEB_REMIX_USER_AGENT)
                .addHeader("Content-Type", "application/json")
                .addHeader("x-goog-visitor-id", visitorId)
                .addHeader("x-goog-api-format-version", "1")
                .addHeader("x-youtube-client-name", "67")
                .addHeader("x-youtube-client-version", ApiConstants.WEB_REMIX_CLIENT_VERSION)
                .addHeader("x-origin", "https://music.youtube.com")
                .addHeader("referer", "https://music.youtube.com/")
                .addHeader("accept", "application/json")
                .addHeader("accept-language", "en-US,en;q=0.9")
                .addHeader("cache-control", "no-cache")
                .addHeader("accept-charset", "UTF-8")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                response.body?.string() ?: ""
            }
        }

    /** Simple GET request — used for IP geolocation. */
    suspend fun executeGetRequest(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", ApiConstants.WEB_REMIX_USER_AGENT)
            .addHeader("accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            response.body?.string() ?: ""
        }
    }

    suspend fun fetchBrowseVisitorId(): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "fetchBrowseVisitorId: fetching from ${ApiConstants.VISITOR_BROWSE_URL}")
        val request = Request.Builder()
            .url(ApiConstants.VISITOR_BROWSE_URL)
            .addHeader("accept", "application/json")
            .addHeader("accept-language", "en-US,en;q=0.9")
            .addHeader("cache-control", "no-cache")
            .addHeader("accept-charset", "UTF-8")
            .addHeader("user-agent", ApiConstants.KTOR_USER_AGENT)
            .build()

        val raw = client.newCall(request).execute().use { response ->
            val code = response.code
            val body = response.body?.string() ?: ""
            Log.d(TAG, "fetchBrowseVisitorId: HTTP $code, body length=${body.length}")
            Log.d(TAG, "fetchBrowseVisitorId: raw (first 300): ${body.take(300)}")
            body
        }

        val match = VISITOR_ID_REGEX.find(raw)?.value
        val result = match?.let {
            java.net.URLDecoder.decode(it, "UTF-8")
        } ?: ""
        Log.d(TAG, "fetchBrowseVisitorId: regex match='${match?.take(30)}', decoded result='${result.take(30)}...' (${result.length} chars)")
        if (result.isBlank()) Log.e(TAG, "fetchBrowseVisitorId: FAILED - no visitor ID extracted!")
        result
    }

    suspend fun fetchStreamVisitorId(): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "fetchStreamVisitorId: using SW-based visitor ID extraction")
        fetchBrowseVisitorId()
    }
}
