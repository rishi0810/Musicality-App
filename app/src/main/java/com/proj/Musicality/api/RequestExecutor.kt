package com.proj.Musicality.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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
                        "clientVersion": "${ApiConstants.ALL_SEARCH_CLIENT_VERSION}",
                        "gl": "IN",
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
            .addHeader("x-youtube-client-version", ApiConstants.ALL_SEARCH_CLIENT_VERSION)
            .addHeader("x-youtube-bootstrap-logged-in", "false")
            .addHeader("x-origin", "https://music.youtube.com")
            .addHeader("referer", "https://music.youtube.com/")
            .addHeader("x-goog-visitor-id", visitorId)
            .addHeader("user-agent", ApiConstants.ALL_SEARCH_USER_AGENT)
            .addHeader("accept", "*/*")
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

        val body = """
            {
              "context": {
                "client": {
                  "clientName": "${ApiConstants.VISIONOS_CLIENT_NAME}",
                  "clientVersion": "${ApiConstants.VISIONOS_CLIENT_VERSION}",
                  "osName": "visionOS",
                  "osVersion": "${ApiConstants.VISIONOS_OS_VERSION}",
                  "deviceMake": "Apple",
                  "deviceModel": "${ApiConstants.VISIONOS_DEVICE_MODEL}",
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
              "videoId": "$videoId",
              "contentCheckOk": true,
              "racyCheckOk": true
            }
        """.trimIndent()
            .toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(ApiConstants.PLAYER_URL)
            .addHeader("x-goog-api-format-version", "1")
            .addHeader("x-youtube-client-name", ApiConstants.VISIONOS_HEADER_CLIENT_NAME)
            .addHeader("x-youtube-client-version", ApiConstants.VISIONOS_CLIENT_VERSION)
            .addHeader("x-origin", "https://music.youtube.com")
            .addHeader("referer", "https://music.youtube.com/")
            .addHeader("x-goog-visitor-id", visitorId)
            .addHeader("user-agent", ApiConstants.VISIONOS_USER_AGENT)
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
                throw PlayerRequestException(videoId, code)
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

    // ── Lyrics (YouTube Music lyrics tab) ─────────────────────────────────────

    data class HttpResult(val code: Int, val body: String) {
        val isSuccessful: Boolean get() = code in 200..299
    }

    /** WEB_REMIX `next` — the only client whose response carries the per-video Lyrics tab. */
    suspend fun executeLyricsNextRequest(videoId: String, hl: String): HttpResult =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", ApiConstants.WEB_REMIX_CLIENT_NAME)
                        put("clientVersion", ApiConstants.WEB_REMIX_CLIENT_VERSION)
                        put("hl", hl)
                        put("gl", "US")
                    }
                }
                put("videoId", videoId)
            }.toString().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(ApiConstants.NEXT_URL)
                .addHeader("User-Agent", ApiConstants.WEB_REMIX_USER_AGENT)
                .addHeader("Content-Type", "application/json")
                .addHeader("x-youtube-client-name", "67")
                .addHeader("x-youtube-client-version", ApiConstants.WEB_REMIX_CLIENT_VERSION)
                .addHeader("x-origin", "https://music.youtube.com")
                .addHeader("referer", "https://music.youtube.com/")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                HttpResult(response.code, response.body?.string() ?: "")
            }
        }

    /**
     * Lyrics page `browse` (pass [browseId]) or translation continuation (pass [continuation]).
     * [webFallback] switches to WEB_REMIX, which renders the same page as a plain-text shelf.
     */
    suspend fun executeLyricsBrowseRequest(
        hl: String,
        browseId: String? = null,
        continuation: String? = null,
        webFallback: Boolean = false,
    ): HttpResult = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    if (webFallback) {
                        put("clientName", ApiConstants.WEB_REMIX_CLIENT_NAME)
                        put("clientVersion", ApiConstants.WEB_REMIX_CLIENT_VERSION)
                    } else {
                        put("clientName", ApiConstants.ANDROID_MUSIC_CLIENT_NAME)
                        put("clientVersion", ApiConstants.ANDROID_MUSIC_CLIENT_VERSION)
                        put("androidSdkVersion", 32)
                        put("osName", "Android")
                        put("osVersion", "12")
                    }
                    put("hl", hl)
                    put("gl", "US")
                }
            }
            if (browseId != null) put("browseId", browseId)
            // Token is already URL-encoded upstream; must be sent verbatim.
            if (continuation != null) put("continuation", continuation)
        }.toString().toRequestBody(jsonMediaType)

        val builder = Request.Builder()
            .url(ApiConstants.BROWSE_URL)
            .addHeader("Content-Type", "application/json")
        if (webFallback) {
            builder
                .addHeader("User-Agent", ApiConstants.WEB_REMIX_USER_AGENT)
                .addHeader("x-youtube-client-name", "67")
                .addHeader("x-youtube-client-version", ApiConstants.WEB_REMIX_CLIENT_VERSION)
                .addHeader("x-origin", "https://music.youtube.com")
                .addHeader("referer", "https://music.youtube.com/")
        } else {
            builder
                .addHeader("User-Agent", ApiConstants.ANDROID_MUSIC_USER_AGENT)
                .addHeader("x-youtube-client-name", ApiConstants.ANDROID_MUSIC_HEADER_CLIENT_NAME)
                .addHeader("x-youtube-client-version", ApiConstants.ANDROID_MUSIC_CLIENT_VERSION)
        }

        client.newCall(builder.post(body).build()).execute().use { response ->
            HttpResult(response.code, response.body?.string() ?: "")
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
