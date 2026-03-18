package com.proj.Musicality.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proj.Musicality.data.model.HomeFeed
import com.proj.Musicality.data.model.HomeSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val apiFeed: HomeFeed? = null,
    val personalizedSections: List<HomeSection> = emptyList(),
    val reservedPersonalizedSlots: Int = 0,
    val isInitialLoading: Boolean = true,
    val errorMessage: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private var initialized = false
    private var apiFeed: HomeFeed? = null
    private var personalizedSections: List<HomeSection> = emptyList()
    private var reservedPersonalizedSlots: Int = 0

    fun initialize() {
        if (initialized) return
        initialized = true

        val appContext = getApplication<Application>().applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val cached = HomePrefetchManager.loadCached(appContext)
            apiFeed = cached.apiFeed
            personalizedSections = cached.personalizedSections
            reservedPersonalizedSlots = cached.reservedPersonalizedSlots
            publishState(
                isInitialLoading = cached.apiFeed == null,
                errorMessage = null
            )

            if (cached.personalizedSections.isEmpty() || cached.snapshotKey.isNotBlank()) {
                refreshPersonalizationAsync(force = false)
            }

            streamApiFeed(force = false)
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
            publishState(isInitialLoading = true, errorMessage = null)
        }

        runCatching {
            HomePrefetchManager.streamApiFeed(
                context = appContext,
                force = force,
                onFeedUpdated = { updatedFeed ->
                    apiFeed = updatedFeed
                    publishState(isInitialLoading = false, errorMessage = null)
                    _isLoadingMore.value = updatedFeed.continuation != null
                }
            )
        }.onFailure { throwable ->
            if (apiFeed == null) {
                publishState(
                    isInitialLoading = false,
                    errorMessage = throwable.message ?: "Failed to load home feed"
                )
            }
            _isLoadingMore.value = false
        }.onSuccess { streamedFeed ->
            if (streamedFeed == null && apiFeed == null) {
                publishState(
                    isInitialLoading = false,
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
        val result = runCatching {
            HomePrefetchManager.loadOrGeneratePersonalization(appContext, force = force)
        }.getOrNull() ?: return

        personalizedSections = result.sections
        reservedPersonalizedSlots = result.reservedSectionCount
        publishState(
            isInitialLoading = apiFeed == null,
            errorMessage = _state.value.errorMessage
        )
    }

    private fun publishState(isInitialLoading: Boolean, errorMessage: String?) {
        _state.value = HomeUiState(
            apiFeed = apiFeed,
            personalizedSections = personalizedSections,
            reservedPersonalizedSlots = reservedPersonalizedSlots,
            isInitialLoading = isInitialLoading,
            errorMessage = errorMessage
        )
    }
}
