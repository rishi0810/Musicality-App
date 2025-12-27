package com.example.musicality.data.mapper

import com.example.musicality.data.model.TodoDto
import com.example.musicality.domain.model.Todo

/**
 * Extension function to convert TodoDto to domain model Todo
 * This keeps the UI layer independent of API response structure
 */
fun TodoDto.toDomain(): Todo {
    return Todo(
        id = this.id,
        title = this.title,
        isCompleted = this.completed,
        userId = this.userId
    )
}
