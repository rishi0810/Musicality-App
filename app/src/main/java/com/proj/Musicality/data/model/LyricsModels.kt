package com.proj.Musicality.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class WordTimestamp(val text: String, val startMs: Long, val endMs: Long)

@Immutable
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<WordTimestamp>? = null,
)

@Immutable
sealed class LyricsState {
    @Immutable
    object Idle : LyricsState()
    @Immutable
    object Loading : LyricsState()
    @Immutable
    data class Loaded(
        val lines: List<LyricLine>,
        val isSynced: Boolean,
        val provider: String? = null,
    ) : LyricsState()
    @Immutable
    object NotFound : LyricsState()
    @Immutable
    data class Error(val message: String) : LyricsState()
}
