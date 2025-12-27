package com.example.musicality.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicality.di.NetworkModule
import com.example.musicality.domain.model.Todo
import com.example.musicality.domain.repository.TodoRepository
import com.example.musicality.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: TodoRepository = NetworkModule.provideTodoRepository()
) : ViewModel() {

    private val _todoState = MutableStateFlow<UiState<Todo>>(UiState.Idle)
    val todoState: StateFlow<UiState<Todo>> = _todoState.asStateFlow()

    init {
        // Fetch todo on initialization
        fetchTodo()
    }

    /**
     * Fetches a todo item from the API
     */
    fun fetchTodo(id: Int = 1) {
        viewModelScope.launch {
            _todoState.value = UiState.Loading
            
            repository.getTodo(id).fold(
                onSuccess = { todo ->
                    _todoState.value = UiState.Success(todo)
                },
                onFailure = { exception ->
                    _todoState.value = UiState.Error(
                        exception.message ?: "Unknown error occurred"
                    )
                }
            )
        }
    }
}