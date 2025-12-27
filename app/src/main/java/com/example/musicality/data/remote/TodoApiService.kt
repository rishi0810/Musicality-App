package com.example.musicality.data.remote

import com.example.musicality.data.model.TodoDto
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit API service interface for Todo endpoints
 * Define all API endpoints here
 */
interface TodoApiService {
    
    /**
     * Fetch a single todo item by ID
     * @param id The todo item ID
     * @return TodoDto wrapped in a suspend function
     */
    @GET("todos/{id}")
    suspend fun getTodo(@Path("id") id: Int): TodoDto
    
    /**
     * Fetch all todos
     * @return List of TodoDto
     */
    @GET("todos")
    suspend fun getAllTodos(): List<TodoDto>
}
