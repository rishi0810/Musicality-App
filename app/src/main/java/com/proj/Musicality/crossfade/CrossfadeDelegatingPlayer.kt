package com.proj.Musicality.crossfade

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * A Player wrapper that delegates to whichever ExoPlayer is currently "active."
 * The MediaSession binds to this, so swapping the active player requires
 * no session rewiring — notification, Bluetooth, Android Auto all stay stable.
 *
 * Built on ForwardingPlayer to automatically handle all Player interface methods.
 *
 * Each inner ExoPlayer runs its own [CrossfadeAudioProcessor] in its audio pipeline so the
 * manager can shape gain and frequency response independently, with sample-accurate smoothing.
 *
 * Architecture:
 *     MediaSession
 *           │
 *     CrossfadeDelegatingPlayer
 *           │
 *      ┌────┴────┐
 *    Player A   Player B   (each has its own CrossfadeAudioProcessor)
 */
@UnstableApi
class CrossfadeDelegatingPlayer private constructor(
    injectedPlayerA: ExoPlayer,
    injectedPlayerB: ExoPlayer,
    private val processorForA: CrossfadeAudioProcessor,
    private val processorForB: CrossfadeAudioProcessor
) : ForwardingPlayer(injectedPlayerA) {

    companion object {
        private const val TAG = "DelegatingPlayer"

        /**
         * Build both ExoPlayers (each with its own audio processor chain) and return a
         * [CrossfadeDelegatingPlayer] that wraps them. This factory exists so the processor
         * can be created *before* the player it gets injected into — something the primary
         * constructor can't do because it has to pass a pre-built player to `super(...)`.
         */
        fun create(context: Context): CrossfadeDelegatingPlayer {
            val procA = CrossfadeAudioProcessor()
            val procB = CrossfadeAudioProcessor()

            val audioAttrs = AudioAttributes.Builder()
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .build()

            val playerA = ExoPlayer.Builder(context)
                .setRenderersFactory(CrossfadeRenderersFactory(context, procA))
                .setAudioAttributes(audioAttrs, /* handleAudioFocus = */ true)
                .setHandleAudioBecomingNoisy(true)
                .build()

            val playerB = ExoPlayer.Builder(context)
                .setRenderersFactory(CrossfadeRenderersFactory(context, procB))
                .setAudioAttributes(audioAttrs, /* handleAudioFocus = */ false)
                .setHandleAudioBecomingNoisy(false)
                .build()

            return CrossfadeDelegatingPlayer(playerA, playerB, procA, procB)
        }
    }

    val playerA: ExoPlayer = injectedPlayerA
    val playerB: ExoPlayer = injectedPlayerB

    /** The player currently producing audible output. */
    @Volatile
    var activePlayer: ExoPlayer = playerA
        private set

    /** The player used for preloading the next track. */
    val inactivePlayer: ExoPlayer
        get() = if (activePlayer === playerA) playerB else playerA

    /** Look up the [CrossfadeAudioProcessor] that sits in the audio pipeline of [player]. */
    fun processorFor(player: ExoPlayer): CrossfadeAudioProcessor =
        if (player === playerA) processorForA else processorForB

    /** Reset both processors to unity-gain, bypass-filter state. */
    fun resetProcessors() {
        processorForA.resetToIdle()
        processorForB.resetToIdle()
    }

    // Custom listener management — we forward from the active player
    private val externalListeners = mutableListOf<Player.Listener>()
    private var activeBridge: Player.Listener? = null

    init {
        installBridge()
    }

    /**
     * Swap which player is active. Called by the crossfade manager
     * when the fade completes. The session sees no interruption.
     */
    fun swapActivePlayer() {
        removeBridge()
        val oldActive = activePlayer
        val oldMediaItem = oldActive.currentMediaItem
        activePlayer = inactivePlayer
        oldActive.stop()
        installBridge()
        Log.d(TAG, "Swapped active player. Now playing: ${activePlayer.currentPosition}ms")
        val newMediaItem = activePlayer.currentMediaItem
        val listeners = externalListeners.toList()

        // The incoming player loaded its media before the bridge was attached.
        // Re-emit the transition/metadata callbacks so MediaSession refreshes
        // notification / control-center metadata to the new track.
        listeners.forEach { listener ->
            listener.onTimelineChanged(activePlayer.currentTimeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            if (oldMediaItem != newMediaItem) {
                listener.onMediaItemTransition(newMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            }
            listener.onMediaMetadataChanged(activePlayer.mediaMetadata)
            listener.onAvailableCommandsChanged(activePlayer.availableCommands)
            listener.onTracksChanged(activePlayer.currentTracks)
        }

        // Notify external listeners of the new player's playback state
        listeners.forEach { listener ->
            listener.onIsPlayingChanged(activePlayer.isPlaying)
            if (activePlayer.duration > 0) {
                listener.onPlaybackStateChanged(activePlayer.playbackState)
            }
        }
    }

    private fun installBridge() {
        val self = this@CrossfadeDelegatingPlayer
        val bridge = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                externalListeners.toList().forEach { it.onIsPlayingChanged(isPlaying) }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                externalListeners.toList().forEach { it.onPlaybackStateChanged(playbackState) }
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                externalListeners.toList().forEach { it.onPlayWhenReadyChanged(playWhenReady, reason) }
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                externalListeners.toList().forEach { it.onMediaMetadataChanged(mediaMetadata) }
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                externalListeners.toList().forEach { it.onRepeatModeChanged(repeatMode) }
            }
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                externalListeners.toList().forEach { it.onPlaybackParametersChanged(playbackParameters) }
            }
            override fun onPlayerError(error: PlaybackException) {
                externalListeners.toList().forEach { it.onPlayerError(error) }
            }
            // Critical for MediaSession to learn about available commands (e.g. seek)
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                externalListeners.toList().forEach { it.onAvailableCommandsChanged(availableCommands) }
            }
            // Critical for MediaSession to know media was loaded (enables notification)
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                externalListeners.toList().forEach { it.onTimelineChanged(timeline, reason) }
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                externalListeners.toList().forEach { it.onTracksChanged(tracks) }
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                externalListeners.toList().forEach { it.onMediaItemTransition(mediaItem, reason) }
            }
            // Pass 'self' so MediaSession sees events from its own player, not a raw ExoPlayer
            override fun onEvents(player: Player, events: Player.Events) {
                externalListeners.toList().forEach { it.onEvents(self, events) }
            }
        }
        activeBridge = bridge
        activePlayer.addListener(bridge)
    }

    private fun removeBridge() {
        activeBridge?.let { activePlayer.removeListener(it) }
        activeBridge = null
    }

    // ── Override listener management to use our external list ──

    override fun addListener(listener: Player.Listener) {
        externalListeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        externalListeners.remove(listener)
    }

    // ── Override ALL delegated methods to route to activePlayer ──
    // ForwardingPlayer delegates to wrappedPlayer (playerA), but after a swap
    // activePlayer might be playerB. We must override every method.

    // Playback control
    override fun play() { activePlayer.play() }
    override fun pause() { activePlayer.pause() }
    override fun prepare() { activePlayer.prepare() }
    override fun stop() { activePlayer.stop() }

    // Seek
    override fun seekTo(positionMs: Long) { activePlayer.seekTo(positionMs) }
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) { activePlayer.seekTo(mediaItemIndex, positionMs) }
    override fun seekToDefaultPosition() { activePlayer.seekToDefaultPosition() }
    override fun seekToDefaultPosition(mediaItemIndex: Int) { activePlayer.seekToDefaultPosition(mediaItemIndex) }
    override fun seekBack() { activePlayer.seekBack() }
    override fun seekForward() { activePlayer.seekForward() }
    override fun seekToPrevious() { activePlayer.seekToPrevious() }
    override fun seekToNext() { activePlayer.seekToNext() }
    override fun seekToPreviousMediaItem() { activePlayer.seekToPreviousMediaItem() }
    override fun seekToNextMediaItem() { activePlayer.seekToNextMediaItem() }
    override fun getSeekBackIncrement(): Long = activePlayer.seekBackIncrement
    override fun getSeekForwardIncrement(): Long = activePlayer.seekForwardIncrement
    override fun getMaxSeekToPreviousPosition(): Long = activePlayer.maxSeekToPreviousPosition
    override fun hasPreviousMediaItem(): Boolean = activePlayer.hasPreviousMediaItem()
    override fun hasNextMediaItem(): Boolean = activePlayer.hasNextMediaItem()

    // Media items
    override fun setMediaItem(mediaItem: androidx.media3.common.MediaItem) { activePlayer.setMediaItem(mediaItem) }
    override fun setMediaItem(mediaItem: androidx.media3.common.MediaItem, startPositionMs: Long) { activePlayer.setMediaItem(mediaItem, startPositionMs) }
    override fun setMediaItem(mediaItem: androidx.media3.common.MediaItem, resetPosition: Boolean) { activePlayer.setMediaItem(mediaItem, resetPosition) }
    override fun setMediaItems(mediaItems: MutableList<androidx.media3.common.MediaItem>) { activePlayer.setMediaItems(mediaItems) }
    override fun setMediaItems(mediaItems: MutableList<androidx.media3.common.MediaItem>, resetPosition: Boolean) { activePlayer.setMediaItems(mediaItems, resetPosition) }
    override fun setMediaItems(mediaItems: MutableList<androidx.media3.common.MediaItem>, startIndex: Int, startPositionMs: Long) { activePlayer.setMediaItems(mediaItems, startIndex, startPositionMs) }
    override fun addMediaItem(mediaItem: androidx.media3.common.MediaItem) { activePlayer.addMediaItem(mediaItem) }
    override fun addMediaItem(index: Int, mediaItem: androidx.media3.common.MediaItem) { activePlayer.addMediaItem(index, mediaItem) }
    override fun addMediaItems(mediaItems: MutableList<androidx.media3.common.MediaItem>) { activePlayer.addMediaItems(mediaItems) }
    override fun addMediaItems(index: Int, mediaItems: MutableList<androidx.media3.common.MediaItem>) { activePlayer.addMediaItems(index, mediaItems) }
    override fun moveMediaItem(currentIndex: Int, newIndex: Int) { activePlayer.moveMediaItem(currentIndex, newIndex) }
    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) { activePlayer.moveMediaItems(fromIndex, toIndex, newIndex) }
    override fun removeMediaItem(index: Int) { activePlayer.removeMediaItem(index) }
    override fun removeMediaItems(fromIndex: Int, toIndex: Int) { activePlayer.removeMediaItems(fromIndex, toIndex) }
    override fun clearMediaItems() { activePlayer.clearMediaItems() }
    override fun replaceMediaItem(index: Int, mediaItem: androidx.media3.common.MediaItem) { activePlayer.replaceMediaItem(index, mediaItem) }
    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<androidx.media3.common.MediaItem>) { activePlayer.replaceMediaItems(fromIndex, toIndex, mediaItems) }
    override fun getCurrentMediaItem(): androidx.media3.common.MediaItem? = activePlayer.currentMediaItem
    override fun getMediaItemCount(): Int = activePlayer.mediaItemCount
    override fun getMediaItemAt(index: Int): androidx.media3.common.MediaItem = activePlayer.getMediaItemAt(index)

    // Playback parameters
    override fun setPlayWhenReady(playWhenReady: Boolean) { activePlayer.playWhenReady = playWhenReady }
    override fun getPlayWhenReady(): Boolean = activePlayer.playWhenReady
    override fun setRepeatMode(repeatMode: Int) { activePlayer.repeatMode = repeatMode }
    override fun getRepeatMode(): Int = activePlayer.repeatMode
    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) { activePlayer.shuffleModeEnabled = shuffleModeEnabled }
    override fun getShuffleModeEnabled(): Boolean = activePlayer.shuffleModeEnabled
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) { activePlayer.playbackParameters = playbackParameters }
    override fun getPlaybackParameters(): PlaybackParameters = activePlayer.playbackParameters
    override fun setPlaybackSpeed(speed: Float) { activePlayer.setPlaybackSpeed(speed) }

    // State
    override fun isPlaying(): Boolean = activePlayer.isPlaying
    override fun isLoading(): Boolean = activePlayer.isLoading
    override fun getPlaybackState(): Int = activePlayer.playbackState
    override fun getPlaybackSuppressionReason(): Int = activePlayer.playbackSuppressionReason
    override fun getPlayerError(): PlaybackException? = activePlayer.playerError

    // Position / timeline
    override fun getDuration(): Long = activePlayer.duration
    override fun getCurrentPosition(): Long = activePlayer.currentPosition
    override fun getBufferedPosition(): Long = activePlayer.bufferedPosition
    override fun getBufferedPercentage(): Int = activePlayer.bufferedPercentage
    override fun getTotalBufferedDuration(): Long = activePlayer.totalBufferedDuration
    override fun getContentDuration(): Long = activePlayer.contentDuration
    override fun getContentPosition(): Long = activePlayer.contentPosition
    override fun getContentBufferedPosition(): Long = activePlayer.contentBufferedPosition
    override fun getCurrentTimeline(): androidx.media3.common.Timeline = activePlayer.currentTimeline
    override fun getCurrentPeriodIndex(): Int = activePlayer.currentPeriodIndex
    override fun getCurrentMediaItemIndex(): Int = activePlayer.currentMediaItemIndex
    override fun getNextMediaItemIndex(): Int = activePlayer.nextMediaItemIndex
    override fun getPreviousMediaItemIndex(): Int = activePlayer.previousMediaItemIndex

    // Media info
    override fun getMediaMetadata(): MediaMetadata = activePlayer.mediaMetadata
    override fun getPlaylistMetadata(): MediaMetadata = activePlayer.playlistMetadata
    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) { activePlayer.playlistMetadata = mediaMetadata }
    override fun getCurrentManifest(): Any? = activePlayer.currentManifest
    override fun getCurrentTracks(): androidx.media3.common.Tracks = activePlayer.currentTracks
    override fun getTrackSelectionParameters(): androidx.media3.common.TrackSelectionParameters = activePlayer.trackSelectionParameters
    override fun setTrackSelectionParameters(parameters: androidx.media3.common.TrackSelectionParameters) { activePlayer.trackSelectionParameters = parameters }

    // Media state flags
    override fun isCurrentMediaItemDynamic(): Boolean = activePlayer.isCurrentMediaItemDynamic
    override fun isCurrentMediaItemLive(): Boolean = activePlayer.isCurrentMediaItemLive
    override fun getCurrentLiveOffset(): Long = activePlayer.currentLiveOffset
    override fun isCurrentMediaItemSeekable(): Boolean = activePlayer.isCurrentMediaItemSeekable
    override fun isPlayingAd(): Boolean = activePlayer.isPlayingAd
    override fun getCurrentAdGroupIndex(): Int = activePlayer.currentAdGroupIndex
    override fun getCurrentAdIndexInAdGroup(): Int = activePlayer.currentAdIndexInAdGroup

    // Commands
    override fun getAvailableCommands(): Player.Commands = activePlayer.availableCommands
    override fun isCommandAvailable(command: Int): Boolean = activePlayer.isCommandAvailable(command)

    // Audio
    override fun setVolume(volume: Float) { activePlayer.volume = volume }
    override fun getVolume(): Float = activePlayer.volume
    override fun getAudioAttributes(): AudioAttributes = activePlayer.audioAttributes

    // Device volume
    override fun getDeviceInfo(): androidx.media3.common.DeviceInfo = activePlayer.deviceInfo
    override fun getDeviceVolume(): Int = activePlayer.deviceVolume
    override fun isDeviceMuted(): Boolean = activePlayer.isDeviceMuted
    @Suppress("DEPRECATION")
    override fun setDeviceVolume(volume: Int) { activePlayer.setDeviceVolume(volume) }
    override fun setDeviceVolume(volume: Int, flags: Int) { activePlayer.setDeviceVolume(volume, flags) }
    @Suppress("DEPRECATION")
    override fun increaseDeviceVolume() { activePlayer.increaseDeviceVolume() }
    override fun increaseDeviceVolume(flags: Int) { activePlayer.increaseDeviceVolume(flags) }
    @Suppress("DEPRECATION")
    override fun decreaseDeviceVolume() { activePlayer.decreaseDeviceVolume() }
    override fun decreaseDeviceVolume(flags: Int) { activePlayer.decreaseDeviceVolume(flags) }
    @Suppress("DEPRECATION")
    override fun setDeviceMuted(muted: Boolean) { activePlayer.setDeviceMuted(muted) }
    override fun setDeviceMuted(muted: Boolean, flags: Int) { activePlayer.setDeviceMuted(muted, flags) }

    // Video (no-ops for audio app, but delegate properly)
    override fun getVideoSize(): androidx.media3.common.VideoSize = activePlayer.videoSize
    override fun getCurrentCues(): androidx.media3.common.text.CueGroup = activePlayer.currentCues

    override fun release() {
        removeBridge()
        externalListeners.clear()
        playerA.release()
        playerB.release()
    }
}
