package com.example.musicality.domain.model

/**
 * Domain model for Todo
 * This is the clean, UI-ready representation of a Todo item
 */
data class Todo(
    val id: Int,
    val title: String,
    val isCompleted: Boolean,
    val userId: Int
)
