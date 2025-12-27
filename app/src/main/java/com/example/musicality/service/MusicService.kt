package com.example.musicality.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.musicality.MainActivity

/**
 * Background music service using Media3's MediaSessionService.
 * 
 * This service:
 * - Runs as a foreground service to keep music playing when app is closed
 * - Automatically creates system notification with playback controls
 * - Integrates with lock screen, Bluetooth headsets, and other external controllers
 * - When app is closed, the service stops and music stops (per user requirement)
 */
class MusicService : MediaSessionService() {
    
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        
        // Build ExoPlayer with audio focus handling
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // Handle audio focus automatically
            )
            .setHandleAudioBecomingNoisy(true) // Pause when headphones disconnected
            .setWakeMode(C.WAKE_MODE_NETWORK) // Keep device awake for streaming
            .build()
        
        // Create pending intent to open app and expand player when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_PLAYER, true)
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Wrap the player to enable next/previous commands in notification
        val wrappedPlayer = object : ForwardingPlayer(player!!) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }
            
            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }
            
            override fun hasNextMediaItem(): Boolean = true
            
            override fun hasPreviousMediaItem(): Boolean = true
            
            override fun seekToNext() {
                // Send broadcast to app to play next
                sendBroadcast(Intent(ACTION_PLAY_NEXT).setPackage(packageName))
            }
            
            override fun seekToPrevious() {
                // Send broadcast to app to play previous
                sendBroadcast(Intent(ACTION_PLAY_PREVIOUS).setPackage(packageName))
            }
            
            override fun seekToNextMediaItem() {
                seekToNext()
            }
            
            override fun seekToPreviousMediaItem() {
                seekToPrevious()
            }
        }
        
        // Build MediaSession with wrapped player
        mediaSession = MediaSession.Builder(this, wrappedPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(MediaSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // When app is swiped away from recents, stop playback and service
        val player = mediaSession?.player
        if (player != null) {
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                // Stop the service if the player is not playing
                stopSelf()
            }
        }
        // Always stop when task is removed (per user requirement)
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
    
    /**
     * Get the ExoPlayer instance for external use
     */
    fun getPlayer(): ExoPlayer? = player
    
    /**
     * Update media metadata for notification display
     */
    fun updateMediaMetadata(title: String, artist: String, artworkUri: String?) {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(artworkUri?.let { android.net.Uri.parse(it) })
            .build()
        
        player?.let { exoPlayer ->
            val currentItem = exoPlayer.currentMediaItem
            if (currentItem != null) {
                val updatedItem = currentItem.buildUpon()
                    .setMediaMetadata(metadata)
                    .build()
                val currentIndex = exoPlayer.currentMediaItemIndex
                exoPlayer.replaceMediaItem(currentIndex, updatedItem)
            }
        }
    }
    
    /**
     * MediaSession callback for handling custom commands
     */
    private inner class MediaSessionCallback : MediaSession.Callback {
        // Default implementation handles play/pause/skip automatically
    }
    
    companion object {
        private const val TAG = "MusicService"
        const val EXTRA_OPEN_PLAYER = "com.example.musicality.OPEN_PLAYER"
        const val ACTION_PLAY_NEXT = "com.example.musicality.PLAY_NEXT"
        const val ACTION_PLAY_PREVIOUS = "com.example.musicality.PLAY_PREVIOUS"
    }
}
