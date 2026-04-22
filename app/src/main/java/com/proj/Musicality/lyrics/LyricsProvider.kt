package com.proj.Musicality.lyrics

interface LyricsProvider {
    val name: String

    /**
     * Returns an extended LRC string (possibly with `<word:start:end|...>` blocks)
     * on success, or a failure when the provider has no match for this track.
     *
     * @param duration Track duration in seconds, or `-1` when unknown.
     */
    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): Result<String>
}
