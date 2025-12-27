package com.example.musicality.util

/**
 * A generic wrapper class to represent different states of data loading
 */
sealed class UiState<out T> {
    /**
     * Initial idle state before any operation
     */
    data object Idle : UiState<Nothing>()

    /**
     * Loading state during data fetch
     */
    data object Loading : UiState<Nothing>()

    /**
     * Success state with data
     */
    data class Success<T>(val data: T) : UiState<T>()

    /**
     * Error state with error message
     */
    data class Error(val message: String) : UiState<Nothing>()
}
