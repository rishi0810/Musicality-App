package com.example.musicality.domain.repository

import com.example.musicality.domain.model.Todo

/**
 * Repository interface defining data operations
 * This abstraction allows for easy testing and implementation swapping
 */
interface TodoRepository {
    /**
     * Fetch a todo item by ID
     * @param id The todo item ID
     * @return Result containing Todo or error
     */
    suspend fun getTodo(id: Int): Result<Todo>
    
    /**
     * Fetch all todos
     * @return Result containing list of Todos or error
     */
    suspend fun getAllTodos(): Result<List<Todo>>
}
