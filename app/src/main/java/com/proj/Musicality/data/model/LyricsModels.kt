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
        /** Upstream attribution, e.g. "Source: Musixmatch". */
        val sourceMessage: String? = null,
    ) : LyricsState()
    @Immutable
    object NotFound : LyricsState()
    @Immutable
    data class Error(val message: String) : LyricsState()
}

/** Parsed snapshot for one provider, ready to swap into LyricsState.Loaded. */
@Immutable
data class ProviderLyricsSnapshot(
    val lines: List<LyricLine>,
    val isSynced: Boolean,
    val sourceMessage: String? = null,
)

@Immutable
sealed class ProviderLoadState {
    /** Not attempted yet. */
    @Immutable object Idle : ProviderLoadState()
    /** Fetch in flight. */
    @Immutable object Loading : ProviderLoadState()
    /** Fetched and parsed; ready to display. */
    @Immutable data class Loaded(val snapshot: ProviderLyricsSnapshot) : ProviderLoadState()
    /** Fetch attempted, provider has nothing for this track. */
    @Immutable object Unavailable : ProviderLoadState()
}

/** A translation is offered for the current track; [attribution] e.g. "Translated by Google". */
@Immutable
data class TranslationOffer(val attribution: String?)

/** Translated lines exactly as served (not yet aligned to displayed lines). */
@Immutable
data class LyricsTranslation(val lines: List<String>, val attribution: String?)

@Immutable
sealed class LyricsTranslationState {
    @Immutable object Unavailable : LyricsTranslationState()
    @Immutable data class Available(val attribution: String?) : LyricsTranslationState()
    @Immutable data class Loading(val attribution: String?) : LyricsTranslationState()
    /** [lines] is index-aligned with the YouTube Music provider's [LyricLine]s; null = no translation for that line. */
    @Immutable data class Shown(val lines: List<String?>, val attribution: String?) : LyricsTranslationState()
}
