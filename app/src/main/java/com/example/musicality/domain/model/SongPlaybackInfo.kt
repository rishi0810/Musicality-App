package com.example.musicality.domain.model

/**
 * Enum representing where the song playback originated from
 */
enum class PlaybackSource {
    /** Song played from search results - show "NOW PLAYING" */
    SEARCH,
    /** Song played from an album - show "PLAYING FROM" + album name */
    ALBUM,
    /** Song played from a playlist - show "PLAYING FROM" + playlist name */
    PLAYLIST,
    /** Song played from queue - inherit parent context */
    QUEUE
}

/**
 * Context information about the current playback source
 */
data class PlaybackContext(
    val source: PlaybackSource = PlaybackSource.SEARCH,
    val sourceName: String = "" // Album name or Playlist name
)

/**
 * Domain model for song playback information
 */
data class SongPlaybackInfo(
    val mainUrl: String,
    val videoId: String,
    val title: String,
    val lengthSeconds: String,
    val thumbnailUrl: String,
    val author: String,
    val viewCount: String,
    val channelId: String = "",
    val playbackContext: PlaybackContext = PlaybackContext(),
    /** MIME type of the audio stream (e.g., "audio/webm; codecs=\"opus\"") */
    val mimeType: String = "audio/webm; codecs=\"opus\"",
    /** Content length in bytes for proper seeking/buffering */
    val contentLength: Long = 0L
)

