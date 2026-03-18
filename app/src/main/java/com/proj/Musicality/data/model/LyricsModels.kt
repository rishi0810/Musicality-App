package com.proj.Musicality.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class LyricLine(val timeMs: Long, val text: String)

@Immutable
sealed class LyricsState {
    @Immutable
    object Idle : LyricsState()
    @Immutable
    object Loading : LyricsState()
    @Immutable
    data class Loaded(val lines: List<LyricLine>, val isSynced: Boolean) : LyricsState()
    @Immutable
    object NotFound : LyricsState()
    @Immutable
    data class Error(val message: String) : LyricsState()
}
