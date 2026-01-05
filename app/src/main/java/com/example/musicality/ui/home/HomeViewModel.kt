package com.example.musicality.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicality.data.repository.UrlFetchRepositoryImpl
import com.example.musicality.domain.model.UrlFetchResult
import com.example.musicality.domain.repository.UrlFetchRepository
import com.example.musicality.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: UrlFetchRepository = UrlFetchRepositoryImpl(application.applicationContext)

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _fetchState = MutableStateFlow<UiState<UrlFetchResult>>(UiState.Idle)
    val fetchState: StateFlow<UiState<UrlFetchResult>> = _fetchState.asStateFlow()

    /**
     * Update the URL input value
     */
    fun updateUrlInput(url: String) {
        _urlInput.value = url
    }

    /**
     * Fetches the URL with custom headers and saves the response locally
     */
    fun fetchUrl() {
        val url = _urlInput.value.trim()
        
        if (url.isEmpty()) {
            _fetchState.value = UiState.Error("Please enter a URL")
            return
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _fetchState.value = UiState.Error("Please enter a valid URL starting with http:// or https://")
            return
        }

        viewModelScope.launch {
            _fetchState.value = UiState.Loading
            
            repository.fetchAndSave(url).fold(
                onSuccess = { result ->
                    _fetchState.value = UiState.Success(result)
                },
                onFailure = { exception ->
                    _fetchState.value = UiState.Error(
                        exception.message ?: "Unknown error occurred"
                    )
                }
            )
        }
    }

    /**
     * Reset the state to idle
     */
    fun reset() {
        _fetchState.value = UiState.Idle
        _urlInput.value = ""
    }
}