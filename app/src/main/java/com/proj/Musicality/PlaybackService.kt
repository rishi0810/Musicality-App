package com.proj.Musicality

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.proj.Musicality.crossfade.CrossfadeDelegatingPlayer
import kotlinx.coroutines.flow.MutableSharedFlow

@UnstableApi
class PlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "PlaybackService"
        const val ACTION_OPEN_PLAYER_FROM_NOTIFICATION =
            "com.proj.Musicality.action.OPEN_PLAYER_FROM_NOTIFICATION"
        var delegatingPlayer: CrossfadeDelegatingPlayer? = null
            private set
        val skipEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    }

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        val crossfadePlayer = CrossfadeDelegatingPlayer(this)
        delegatingPlayer = crossfadePlayer

        val forwarding = object : ForwardingPlayer(crossfadePlayer) {
            fun handlePreviousTransportCommand(source: String) {
                // Keep transport controls consistent with in-app UI behavior.
                // PlaybackViewModel.skipPrev() decides restart-vs-previous and
                // applies crossfade cancellation/grace-window logic.
                Log.d(TAG, "$source: previous track via ViewModel transport")
                skipEvents.tryEmit(-1)
            }

            override fun seekToNext() {
                Log.d(TAG, "seekToNext (notification)")
                skipEvents.tryEmit(1)
            }

            override fun seekToNextMediaItem() {
                Log.d(TAG, "seekToNextMediaItem (notification)")
                skipEvents.tryEmit(1)
            }

            override fun seekToPrevious() {
                handlePreviousTransportCommand("seekToPrevious (notification)")
            }

            override fun seekToPreviousMediaItem() {
                handlePreviousTransportCommand("seekToPreviousMediaItem (notification)")
            }

            override fun hasNextMediaItem(): Boolean = true
            override fun hasPreviousMediaItem(): Boolean = true

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean = when (command) {
                COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                else -> super.isCommandAvailable(command)
            }
        }

        val activityIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_PLAYER_FROM_NOTIFICATION
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, forwarding)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = delegatingPlayer
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        mediaSession?.release()
        delegatingPlayer?.release()
        delegatingPlayer = null
        mediaSession = null
        super.onDestroy()
    }
}
