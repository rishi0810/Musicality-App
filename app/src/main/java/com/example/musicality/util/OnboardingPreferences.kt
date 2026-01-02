package com.example.musicality.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages onboarding preferences for first-time user experiences.
 * Uses SharedPreferences for simple key-value storage.
 */
object OnboardingPreferences {
    private const val PREFS_NAME = "musicality_onboarding"
    private const val KEY_SWIPE_UP_SHOWN = "swipe_up_hint_shown"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Check if the swipe-up hint has been shown before
     */
    fun hasSwipeUpHintBeenShown(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SWIPE_UP_SHOWN, false)
    }
    
    /**
     * Mark the swipe-up hint as shown (user has swiped up or dismissed it)
     */
    fun markSwipeUpHintAsShown(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_SWIPE_UP_SHOWN, true).apply()
    }
    
    /**
     * Reset onboarding preferences (useful for testing)
     */
    fun resetOnboarding(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
