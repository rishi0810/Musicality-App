package com.example.musicality.data.repository

import com.example.musicality.data.mapper.toDomain
import com.example.musicality.data.remote.TodoApiService
import com.example.musicality.domain.model.Todo
import com.example.musicality.domain.repository.TodoRepository

/**
 * Implementation of TodoRepository
 * Handles data fetching from the API and error handling
 */
class TodoRepositoryImpl(
    private val apiService: TodoApiService
) : TodoRepository {
    
    override suspend fun getTodo(id: Int): Result<Todo> {
        return try {
            val todoDto = apiService.getTodo(id)
            Result.success(todoDto.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getAllTodos(): Result<List<Todo>> {
        return try {
            val todoDtos = apiService.getAllTodos()
            Result.success(todoDtos.map { it.toDomain() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
