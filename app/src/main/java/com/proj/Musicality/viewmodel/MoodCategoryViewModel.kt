package com.proj.Musicality.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proj.Musicality.api.RequestExecutor
import com.proj.Musicality.api.VisitorManager
import com.proj.Musicality.cache.ExploreDiskCache
import com.proj.Musicality.data.model.HomeSection
import com.proj.Musicality.data.parser.MoodCategoryParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MoodCategoryViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(val mood: MoodCategoryParser.Mood, val sections: List<HomeSection>) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var initializedMood: MoodCategoryParser.Mood? = null

    fun initialize(mood: MoodCategoryParser.Mood) {
        if (initializedMood == mood && _state.value !is UiState.Error) return
        initializedMood = mood
        viewModelScope.launch { loadData(mood, forceRefresh = false) }
    }

    suspend fun refresh(mood: MoodCategoryParser.Mood) {
        initializedMood = mood
        loadData(mood, forceRefresh = true)
    }

    private suspend fun loadData(mood: MoodCategoryParser.Mood, forceRefresh: Boolean) {
        val context = getApplication<Application>()
        val cacheKey = "mood-category-${mood.name.lowercase()}"

        if (!forceRefresh) {
            withContext(Dispatchers.IO) {
                val cachedJson = ExploreDiskCache.get(context, cacheKey)
                if (cachedJson != null) {
                    runCatching {
                        MoodCategoryParser.parse(cachedJson)
                    }.onSuccess { feed ->
                        _state.value = UiState.Loaded(mood, feed.sections)
                        return@withContext
                    }
                }
            }
            if (_state.value is UiState.Loaded) return
        }

        _state.value = UiState.Loading

        runCatching {
            withContext(Dispatchers.IO) {
                val visitorId = VisitorManager.ensureBrowseVisitorId()
                val json = RequestExecutor.executeMoodCategoryRequest(mood.params, visitorId)
                if (json.isNotBlank()) ExploreDiskCache.put(context, cacheKey, json)
                MoodCategoryParser.parse(json).sections
            }
        }.onSuccess { sections ->
            _state.value = UiState.Loaded(mood, sections)
        }.onFailure { err ->
            _state.value = UiState.Error(err.message ?: "Failed to load mood category")
        }
    }
}
