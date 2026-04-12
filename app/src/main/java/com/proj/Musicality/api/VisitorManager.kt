package com.proj.Musicality.api

import android.content.Context
import android.util.Log
import com.proj.Musicality.data.parser.StreamParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object VisitorManager {
    private const val TAG = "VisitorManager"
    private const val STREAM_HEALTHCHECK_VIDEO_ID = "dQw4w9WgXcQ"

    var browseVisitorId: String = ""
        private set
    var streamVisitorId: String = ""
        private set

    @Volatile
    var isInitialized: Boolean = false
        private set

    private const val PREFS_NAME = "visitor_prefs"
    private const val KEY_UNIFIED = "visitor_id"
    private const val KEY_BROWSE = "browse_visitor_id"
    private const val KEY_STREAM = "stream_visitor_id"
    private var appContext: Context? = null
    private val browseMutex = Mutex()
    private val streamMutex = Mutex()

    fun loadFromPrefs(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val unified = prefs.getString(KEY_UNIFIED, "") ?: ""
        val browse = prefs.getString(KEY_BROWSE, "") ?: ""
        val stream = prefs.getString(KEY_STREAM, "") ?: ""
        val resolved = unified.ifBlank { browse.ifBlank { stream } }
        browseVisitorId = resolved
        streamVisitorId = resolved
        Log.d(TAG, "loadFromPrefs: browse='$browseVisitorId', stream='$streamVisitorId'")
        updateInitializedFlag()
    }

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        appContext = context.applicationContext
        Log.d(TAG, "initialize: validating cached visitor IDs")

        if (browseVisitorId.isBlank()) {
            refreshBrowseVisitorId()
        }
        if (streamVisitorId.isBlank()) {
            refreshStreamVisitorId()
        } else {
            validateAndRefreshStreamVisitorIdIfNeeded()
        }
        updateInitializedFlag()
        Log.d(TAG, "initialize DONE: isInitialized=$isInitialized")
    }

    suspend fun ensureBrowseVisitorId(): String {
        if (browseVisitorId.isNotBlank()) return browseVisitorId
        return refreshBrowseVisitorId()
    }

    suspend fun ensureStreamVisitorId(): String {
        if (streamVisitorId.isNotBlank()) return streamVisitorId
        return refreshStreamVisitorId()
    }

    suspend fun refreshBrowseVisitorId(): String = browseMutex.withLock {
        val fetched = runCatching { RequestExecutor.fetchBrowseVisitorId() }
            .onFailure { Log.e(TAG, "refreshBrowseVisitorId: fetch failed", it) }
            .getOrDefault("")
        if (fetched.isNotBlank()) {
            setVisitorIds(fetched)
            persistToPrefs()
            updateInitializedFlag()
        }
        browseVisitorId
    }

    suspend fun refreshStreamVisitorId(): String = streamMutex.withLock {
        // Stream calls now use the same SW-derived visitor ID as browse calls.
        refreshBrowseVisitorId()
    }

    suspend fun validateAndRefreshStreamVisitorIdIfNeeded() = withContext(Dispatchers.IO) {
        val current = ensureStreamVisitorId()
        if (current.isBlank()) return@withContext
        val isHealthy = runCatching {
            val json = RequestExecutor.executeReelRequest(STREAM_HEALTHCHECK_VIDEO_ID, current)
            val details = StreamParser.extractSongDetails(json)
            !details?.streamUrl.isNullOrBlank()
        }.onFailure {
            Log.w(TAG, "validateStreamVisitorId: health check failed", it)
        }.getOrDefault(false)
        if (!isHealthy) {
            Log.w(TAG, "validateStreamVisitorId: cached stream visitor ID invalid, refreshing")
            refreshStreamVisitorId()
        }
    }

    suspend fun executeBrowseRequestWithRecovery(browseId: String): String {
        val initialId = ensureBrowseVisitorId()
        if (initialId.isBlank()) return ""
        val first = runCatching {
            RequestExecutor.executeBrowseRequest(browseId, initialId)
        }.getOrDefault("")
        if (first.isNotBlank()) return first

        val refreshedId = refreshBrowseVisitorId()
        if (refreshedId.isBlank() || refreshedId == initialId) return first
        return runCatching {
            RequestExecutor.executeBrowseRequest(browseId, refreshedId)
        }.getOrDefault(first)
    }

    suspend fun executeBrowseContinuationRequestWithRecovery(continuation: String): String {
        val initialId = ensureBrowseVisitorId()
        if (initialId.isBlank()) return ""
        val first = runCatching {
            RequestExecutor.executeBrowseContinuationRequest(continuation, initialId)
        }.getOrDefault("")
        if (first.isNotBlank()) return first

        val refreshedId = refreshBrowseVisitorId()
        if (refreshedId.isBlank() || refreshedId == initialId) return first
        return runCatching {
            RequestExecutor.executeBrowseContinuationRequest(continuation, refreshedId)
        }.getOrDefault(first)
    }

    suspend fun executeSearchAllRequestWithRecovery(query: String): String {
        val initialId = ensureBrowseVisitorId()
        if (initialId.isBlank()) return ""
        val first = runCatching {
            RequestExecutor.executeSearchAllRequest(query, initialId)
        }.getOrDefault("")
        if (first.isNotBlank()) return first

        val refreshedId = refreshBrowseVisitorId()
        if (refreshedId.isBlank() || refreshedId == initialId) return first
        return runCatching {
            RequestExecutor.executeSearchAllRequest(query, refreshedId)
        }.getOrDefault(first)
    }

    suspend fun executeSearchRequestWithRecovery(query: String, params: String): String {
        val initialId = ensureBrowseVisitorId()
        if (initialId.isBlank()) return ""
        val first = runCatching {
            RequestExecutor.executeSearchRequest(query, params, initialId)
        }.getOrDefault("")
        if (first.isNotBlank()) return first

        val refreshedId = refreshBrowseVisitorId()
        if (refreshedId.isBlank() || refreshedId == initialId) return first
        return runCatching {
            RequestExecutor.executeSearchRequest(query, params, refreshedId)
        }.getOrDefault(first)
    }

    suspend fun executeSuggestionRequestWithRecovery(input: String): String {
        val initialId = ensureBrowseVisitorId()
        if (initialId.isBlank()) return ""
        val first = runCatching {
            RequestExecutor.executeSuggestionRequest(input, initialId)
        }.getOrDefault("")
        if (first.isNotBlank()) return first

        val refreshedId = refreshBrowseVisitorId()
        if (refreshedId.isBlank() || refreshedId == initialId) return first
        return runCatching {
            RequestExecutor.executeSuggestionRequest(input, refreshedId)
        }.getOrDefault(first)
    }

    private fun updateInitializedFlag() {
        isInitialized = browseVisitorId.isNotBlank() && streamVisitorId.isNotBlank()
    }

    private fun persistToPrefs() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_UNIFIED, browseVisitorId)
            .putString(KEY_BROWSE, browseVisitorId)
            .putString(KEY_STREAM, streamVisitorId)
            .apply()
    }

    private fun setVisitorIds(visitorId: String) {
        browseVisitorId = visitorId
        streamVisitorId = visitorId
    }
}
