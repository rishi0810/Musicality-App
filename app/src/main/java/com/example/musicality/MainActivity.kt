package com.example.musicality

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.example.musicality.service.MusicService
import com.example.musicality.ui.MusicalityApp
import com.example.musicality.ui.theme.MusicalityTheme

class MainActivity : ComponentActivity() {
    
    // Track if we should open the player (from notification click)
    private val shouldOpenPlayer = mutableStateOf(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Check if opened from notification
        handleIntent(intent)
        
        setContent {
            MusicalityTheme {
                MusicalityApp(openPlayerOnStart = shouldOpenPlayer.value)
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle when app is already running and notification is tapped
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(MusicService.EXTRA_OPEN_PLAYER, false) == true) {
            shouldOpenPlayer.value = true
        }
    }
}