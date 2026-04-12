package com.proj.Musicality.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proj.Musicality.data.local.ListeningHistoryRepository
import com.proj.Musicality.data.model.HomeFeed
import com.proj.Musicality.data.model.HomeItem
import com.proj.Musicality.data.model.HomeSection
import com.proj.Musicality.data.model.SectionLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

data class HomeUiState(
    val apiFeed: HomeFeed? = null,
    val personalizedSections: List<HomeSection> = emptyList(),
    val reservedPersonalizedSlots: Int = 0,
    val isApiLoading: Boolean = true,
    val isPersonalizationLoading: Boolean = false,
    val errorMessage: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private var initialized = false
    private var apiFeed: HomeFeed? = null
    private var personalizedSections: List<HomeSection> = emptyList()
    private var reservedPersonalizedSlots: Int = 0
    private var isApiLoading: Boolean = true
    private var isPersonalizationLoading: Boolean = false

    fun initialize() {
        if (initialized) return
        initialized = true

        val appContext = getApplication<Application>().applicationContext
        val shouldRunFirstLaunchReload = shouldRunFirstLaunchReload()
        viewModelScope.launch(Dispatchers.IO) {
            val cached = HomePrefetchManager.loadCached(appContext)
            apiFeed = cached.apiFeed
            personalizedSections = cached.personalizedSections
            reservedPersonalizedSlots = cached.reservedPersonalizedSlots
            isApiLoading = cached.apiFeed == null
            publishState(errorMessage = null)

            if (cached.personalizedSections.isEmpty() || cached.snapshotKey.isNotBlank()) {
                isPersonalizationLoading = cached.personalizedSections.isEmpty()
                publishState(errorMessage = null)
                refreshPersonalizationAsync(force = false)
            }

            streamApiFeed(force = false)

            if (shouldRunFirstLaunchReload) {
                runFirstLaunchReload(appContext)
            }
        }
    }

    suspend fun refresh() {
        withContext(Dispatchers.IO) {
            coroutineScope {
                val apiRefresh = async { streamApiFeed(force = true) }
                val personalizationRefresh = async { refreshPersonalization(force = true) }
                apiRefresh.await()
                personalizationRefresh.await()
            }
        }
    }

    private suspend fun streamApiFeed(force: Boolean) {
        val appContext = getApplication<Application>().applicationContext
        if (force && apiFeed == null) {
            isApiLoading = true
            publishState(errorMessage = null)
        }

        runCatching {
            HomePrefetchManager.streamApiFeed(
                context = appContext,
                force = force,
                onFeedUpdated = { updatedFeed ->
                    apiFeed = updatedFeed
                    isApiLoading = false
                    publishState(errorMessage = null)
                    _isLoadingMore.value = updatedFeed.continuation != null
                }
            )
        }.onFailure { throwable ->
            isApiLoading = false
            if (apiFeed == null) {
                publishState(
                    errorMessage = throwable.message ?: "Failed to load home feed"
                )
            }
            _isLoadingMore.value = false
        }.onSuccess { streamedFeed ->
            isApiLoading = false
            if (streamedFeed == null && apiFeed == null) {
                publishState(
                    errorMessage = "Failed to load home feed"
                )
            }
            _isLoadingMore.value = false
        }
    }

    private fun refreshPersonalizationAsync(force: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            refreshPersonalization(force)
        }
    }

    private suspend fun refreshPersonalization(force: Boolean) {
        val appContext = getApplication<Application>().applicationContext
        if (personalizedSections.isEmpty()) {
            isPersonalizationLoading = true
            publishState(errorMessage = _state.value.errorMessage)
        }
        val result = runCatching {
            withTimeout(PERSONALIZATION_LOAD_TIMEOUT_MS) {
                HomePrefetchManager.loadOrGeneratePersonalization(appContext, force = force)
            }
        }.getOrElse {
            // Avoid infinite personalization shimmer when generation fails or stalls.
            // If we already have sections, keep showing them.
            if (personalizedSections.isEmpty()) {
                personalizedSections = buildLocalPersonalizationFallback(appContext)
                reservedPersonalizedSlots = personalizedSections.size
                Log.w(
                    TAG,
                    "refreshPersonalization: fallback applied sections=${personalizedSections.size}, reason=${it::class.java.simpleName}"
                )
                publishState(
                    errorMessage = _state.value.errorMessage
                )
            }
            isPersonalizationLoading = false
            publishState(errorMessage = _state.value.errorMessage)
            return
        }

        personalizedSections = result.sections
        reservedPersonalizedSlots = result.reservedSectionCount
        isPersonalizationLoading = false
        Log.d(
            TAG,
            "refreshPersonalization: success sections=${result.sections.size}, reserved=${result.reservedSectionCount}, titles=${result.sections.joinToString { it.title }}"
        )
        publishState(errorMessage = _state.value.errorMessage)
    }

    private fun publishState(errorMessage: String?) {
        _state.value = HomeUiState(
            apiFeed = apiFeed,
            personalizedSections = personalizedSections,
            reservedPersonalizedSlots = reservedPersonalizedSlots,
            isApiLoading = isApiLoading,
            isPersonalizationLoading = isPersonalizationLoading,
            errorMessage = errorMessage
        )
    }

    private suspend fun runFirstLaunchReload(appContext: Context) {
        runCatching {
            Log.d(TAG, "runFirstLaunchReload: forcing silent API refresh")
            streamApiFeed(force = true)
            markFirstLaunchReloadComplete()
        }.onFailure { throwable ->
            Log.w(TAG, "runFirstLaunchReload: failed", throwable)
        }
    }

    private fun shouldRunFirstLaunchReload(): Boolean {
        return !prefs.getBoolean(KEY_FIRST_LAUNCH_RELOAD_DONE, false)
    }

    private fun markFirstLaunchReloadComplete() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH_RELOAD_DONE, true).apply()
    }

    companion object {
        private const val TAG = "HomeViewModel"
        private const val PERSONALIZATION_LOAD_TIMEOUT_MS = 20_000L
        private const val PREFS_NAME = "home_prefs"
        private const val KEY_FIRST_LAUNCH_RELOAD_DONE = "first_launch_reload_done"
    }

    private suspend fun buildLocalPersonalizationFallback(
        appContext: android.content.Context
    ): List<HomeSection> {
        val snapshot = ListeningHistoryRepository
            .getInstance(appContext)
            .getSnapshot()
        if (snapshot.distinctSongCount != 1) {
            return emptyList()
        }
        val seed = snapshot.recentlyPlayed.firstOrNull() ?: return emptyList()
        val continuePlaying = HomeItem.Song(
            videoId = seed.videoId,
            playlistId = null,
            title = seed.title,
            artistName = seed.artistName,
            artistId = seed.artistId,
            albumName = null,
            albumId = null,
            plays = null,
            thumbnailUrl = seed.thumbnailUrl,
            musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
        )
        return listOf(
            HomeSection(
                title = "Continue Playing",
                items = listOf(continuePlaying),
                moreEndpoint = null,
                layoutHint = SectionLayout.HERO_CARD
            )
        )
    }
}
