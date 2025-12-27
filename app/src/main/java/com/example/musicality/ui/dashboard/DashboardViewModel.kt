package com.example.musicality.ui.dashboard

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DashboardViewModel : ViewModel() {

    private val _text = MutableStateFlow("This is dashboard Screen")
    val text: StateFlow<String> = _text.asStateFlow()
}