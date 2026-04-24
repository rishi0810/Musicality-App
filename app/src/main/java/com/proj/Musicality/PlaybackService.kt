package com.proj.Musicality

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.proj.Musicality.automotive.MusicalityLibraryCallback
import com.proj.Musicality.automotive.toAppMediaItem
import com.proj.Musicality.cache.AudioFileCache
import com.proj.Musicality.crossfade.CrossfadeDelegatingPlayer
import com.proj.Musicality.data.local.LibraryRepository
import com.proj.Musicality.data.local.ListeningHistoryRepository
import com.proj.Musicality.data.model.MediaItem as AppMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class PlaybackService : MediaLibraryService() {

    companion object {
        private const val TAG = "PlaybackService"
        const val ACTION_OPEN_PLAYER_FROM_NOTIFICATION =
            "com.proj.Musicality.action.OPEN_PLAYER_FROM_NOTIFICATION"
        var delegatingPlayer: CrossfadeDelegatingPlayer? = null
            private set
        val skipEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    }

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var serviceScope: CoroutineScope
    private var autoAdvanceJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        AudioFileCache.init(applicationContext)

        val crossfadePlayer = CrossfadeDelegatingPlayer.create(this)
        delegatingPlayer = crossfadePlayer

        val forwarding = object : ForwardingPlayer(crossfadePlayer) {
            fun handlePreviousTransportCommand(source: String) {
                if (isAutoInitiated()) {
                    Log.d(TAG, "$source: auto-queue previous")
                    advanceAutoQueue(-1)
                } else {
                    // Keep transport controls consistent with in-app UI behavior.
                    // PlaybackViewModel.skipPrev() decides restart-vs-previous and
                    // applies crossfade cancellation/grace-window logic.
                    Log.d(TAG, "$source: previous track via ViewModel transport")
                    skipEvents.tryEmit(-1)
                }
            }

            override fun seekToNext() {
                if (isAutoInitiated()) {
                    Log.d(TAG, "seekToNext: auto-queue advance")
                    advanceAutoQueue(1)
                } else {
                    Log.d(TAG, "seekToNext (notification)")
                    skipEvents.tryEmit(1)
                }
            }

            override fun seekToNextMediaItem() {
                if (isAutoInitiated()) {
                    Log.d(TAG, "seekToNextMediaItem: auto-queue advance")
                    advanceAutoQueue(1)
                } else {
                    Log.d(TAG, "seekToNextMediaItem (notification)")
                    skipEvents.tryEmit(1)
                }
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

        // Auto-advance Android-Auto-initiated playback when a track ends
        // without an in-app ViewModel consuming STATE_ENDED.
        forwarding.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && isAutoInitiated()) {
                    advanceAutoQueue(1)
                }
            }
        })

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

        val libraryCallback = MusicalityLibraryCallback(
            serviceScope = serviceScope,
            libraryRepository = LibraryRepository.getInstance(applicationContext),
            listeningHistoryRepository = ListeningHistoryRepository.getInstance(applicationContext)
        )

        mediaSession = MediaLibrarySession.Builder(this, forwarding, libraryCallback)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

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
        serviceScope.cancel()
        delegatingPlayer = null
        mediaSession = null
        super.onDestroy()
    }

    // ── Android Auto queue advancement ──

    /**
     * True when the currently-loaded media item was initiated via the Android Auto
     * browse tree. PlaybackViewModel sets raw videoIds; the Auto callback uses a
     * "tr|<folder>|<videoId>" scheme so we can route transport commands correctly.
     */
    private fun isAutoInitiated(): Boolean {
        val mediaId = delegatingPlayer?.currentMediaItem?.mediaId.orEmpty()
        return mediaId.startsWith(TRACK_PREFIX)
    }

    private fun advanceAutoQueue(direction: Int) {
        val player = delegatingPlayer ?: return
        val currentMediaId = player.currentMediaItem?.mediaId ?: return
        val folderKey = MusicalityLibraryCallback.extractFolderKey(currentMediaId) ?: return
        val videoId = MusicalityLibraryCallback.extractVideoId(currentMediaId) ?: return

        autoAdvanceJob?.cancel()
        autoAdvanceJob = serviceScope.launch {
            val items = folderItemsByKey(folderKey)
            val idx = items.indexOfFirst { it.videoId == videoId }
            if (idx < 0) {
                Log.w(TAG, "advanceAutoQueue: current track not in folder '$folderKey'")
                return@launch
            }
            val targetIdx = idx + direction
            if (targetIdx !in items.indices) {
                Log.d(TAG, "advanceAutoQueue: boundary reached (idx=$idx dir=$direction)")
                return@launch
            }

            val nextItem = items[targetIdx]
            val file = AudioFileCache.getOrDownload(nextItem.videoId) ?: run {
                Log.e(TAG, "advanceAutoQueue: failed to download ${nextItem.videoId}")
                return@launch
            }
            AudioFileCache.unpinAll()
            AudioFileCache.pin(nextItem.videoId)

            val nextMediaId = MusicalityLibraryCallback.trackId(folderKey, nextItem.videoId)
            val media3Item = MediaItem.Builder()
                .setMediaId(nextMediaId)
                .setUri(file.toURI().toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(nextItem.title)
                        .setArtist(nextItem.artistName)
                        .setAlbumTitle(nextItem.albumName)
                        .setArtworkUri(nextItem.thumbnailUrl?.let { Uri.parse(it) })
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .build()
                )
                .build()

            withContext(Dispatchers.Main) {
                player.setMediaItem(media3Item)
                player.prepare()
                player.play()
            }
        }
    }

    private suspend fun folderItemsByKey(key: String): List<AppMediaItem> =
        withContext(Dispatchers.IO) {
            val libraryRepo = LibraryRepository.getInstance(applicationContext)
            val historyRepo = ListeningHistoryRepository.getInstance(applicationContext)
            when (key) {
                "liked" -> {
                    libraryRepo.refresh()
                    libraryRepo.snapshot.value.likedSongs
                }
                "downloaded" -> {
                    libraryRepo.refresh()
                    libraryRepo.snapshot.value.downloadedMedia
                }
                "recent" -> historyRepo.getSnapshot().recentlyPlayed.map { it.toAppMediaItem() }
                "top" -> historyRepo.getSnapshot().topSongs.map { it.toAppMediaItem() }
                else -> emptyList()
            }
        }
}

private const val TRACK_PREFIX = "tr|"
