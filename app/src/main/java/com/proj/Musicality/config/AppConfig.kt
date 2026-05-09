package com.proj.Musicality.config

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, DARK, LIGHT }

enum class CornerRadiusPreset(val label: String, val scale: Float) {
    NONE("Sharp", 0f),
    SMALL("Small", 0.5f),
    DEFAULT("Default", 1f),
    LARGE("Rounded", 1.5f)
}

val LocalCornerRadius = compositionLocalOf { CornerRadiusPreset.DEFAULT }

fun Dp.scaled(preset: CornerRadiusPreset): Dp = this * preset.scale

object AppConfig {

    private const val PREFS = "app_config"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_CROSSFADE_ENABLED = "crossfade_enabled"
    private const val KEY_CORNER_RADIUS = "corner_radius"
    private const val KEY_WORD_SYNC_LYRICS = "word_sync_lyrics"
    private const val KEY_LISTENING_HISTORY_PAUSED = "listening_history_paused"
    private const val KEY_SEARCH_HISTORY_PAUSED = "search_history_paused"

    private lateinit var prefs: SharedPreferences

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _crossfadeEnabled = MutableStateFlow(false)
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private val _cornerRadius = MutableStateFlow(CornerRadiusPreset.DEFAULT)
    val cornerRadius: StateFlow<CornerRadiusPreset> = _cornerRadius.asStateFlow()

    private val _wordSyncLyrics = MutableStateFlow(true)
    val wordSyncLyrics: StateFlow<Boolean> = _wordSyncLyrics.asStateFlow()

    private val _listeningHistoryPaused = MutableStateFlow(false)
    val listeningHistoryPaused: StateFlow<Boolean> = _listeningHistoryPaused.asStateFlow()

    private val _searchHistoryPaused = MutableStateFlow(false)
    val searchHistoryPaused: StateFlow<Boolean> = _searchHistoryPaused.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _themeMode.value = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)
        _crossfadeEnabled.value = prefs.getBoolean(KEY_CROSSFADE_ENABLED, false)
        _cornerRadius.value = runCatching {
            CornerRadiusPreset.valueOf(prefs.getString(KEY_CORNER_RADIUS, CornerRadiusPreset.DEFAULT.name)!!)
        }.getOrDefault(CornerRadiusPreset.DEFAULT)
        _wordSyncLyrics.value = prefs.getBoolean(KEY_WORD_SYNC_LYRICS, true)
        _listeningHistoryPaused.value = prefs.getBoolean(KEY_LISTENING_HISTORY_PAUSED, false)
        _searchHistoryPaused.value = prefs.getBoolean(KEY_SEARCH_HISTORY_PAUSED, false)
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        _crossfadeEnabled.value = enabled
        prefs.edit().putBoolean(KEY_CROSSFADE_ENABLED, enabled).apply()
    }

    fun setCornerRadius(preset: CornerRadiusPreset) {
        _cornerRadius.value = preset
        prefs.edit().putString(KEY_CORNER_RADIUS, preset.name).apply()
    }

    fun setWordSyncLyrics(enabled: Boolean) {
        _wordSyncLyrics.value = enabled
        prefs.edit().putBoolean(KEY_WORD_SYNC_LYRICS, enabled).apply()
    }

    fun setListeningHistoryPaused(paused: Boolean) {
        _listeningHistoryPaused.value = paused
        prefs.edit().putBoolean(KEY_LISTENING_HISTORY_PAUSED, paused).apply()
    }

    fun setSearchHistoryPaused(paused: Boolean) {
        _searchHistoryPaused.value = paused
        prefs.edit().putBoolean(KEY_SEARCH_HISTORY_PAUSED, paused).apply()
    }
}
