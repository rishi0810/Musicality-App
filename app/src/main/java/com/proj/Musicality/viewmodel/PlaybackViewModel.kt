package com.proj.Musicality.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.proj.Musicality.PlaybackService
import com.proj.Musicality.api.LyricsRepository
import com.proj.Musicality.api.RequestExecutor
import com.proj.Musicality.api.VisitorManager
import com.proj.Musicality.api.StreamRequestResolver
import com.proj.Musicality.cache.AppCache
import com.proj.Musicality.cache.AudioFileCache
import com.proj.Musicality.data.local.LibraryRepository
import com.proj.Musicality.data.local.ListeningHistoryRepository
import java.io.File
import com.proj.Musicality.data.model.LyricsState
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.data.model.ProviderLoadState
import com.proj.Musicality.lyrics.LyricsHelper
import com.proj.Musicality.data.model.PlaybackQueue
import com.proj.Musicality.data.model.QueueSource
import com.proj.Musicality.data.resolver.SongArtResolver
import com.proj.Musicality.crossfade.CrossfadeNextTrack
import com.proj.Musicality.crossfade.CrossfadeDelegatingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.proj.Musicality.data.parser.NextParser
import com.proj.Musicality.crossfade.SimpleCrossfadeManager
import com.proj.Musicality.util.shouldRestartCurrentTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.common.util.concurrent.ListenableFuture

@androidx.compose.runtime.Immutable
data class PlaybackState(
    val currentItem: MediaItem? = null,
    val queue: PlaybackQueue = PlaybackQueue(emptyList(), 0, QueueSource.SINGLE),
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
) {
    val hasMedia: Boolean get() = currentItem != null
}

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "PlaybackViewModel"
        private const val PLAYBACK_FLOW_TAG = "PlaybackFlow"
        private const val CROSSFADE_PREVIOUS_GRACE_MS = 10_000L
        private const val REWIND_PREVIOUS_WINDOW_MS = 2_000L
        private const val LIBRARY_EXTENSION_SEED_COUNT = 5
        private const val LONG_FORM_THRESHOLD_SECONDS = 900L
        private const val PLAYED_CACHE_MIN_PLAY_MS = 20_000L
        private const val MAX_CONSECUTIVE_FAILURES = 3
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    // The videoId currently being resolved/downloaded before playback can start.
    // Non-null while fetchAndPlay is waiting on a stream URL or file download. The UI
    // shows a wavy indeterminate progress bar during this window and the MediaSession
    // metadata is pre-swapped to the target track so the notification, mini player,
    // and full sheet all reference the same song even before any audio is buffered.
    private val _loadingTrackId = MutableStateFlow<String?>(null)
    val loadingTrackId: StateFlow<String?> = _loadingTrackId.asStateFlow()

    private val _lyricsState = MutableStateFlow<LyricsState>(LyricsState.Idle)
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()

    // Per-provider load state for the dropdown switcher. Reset when the active track
    // changes. Populated lazily — the race wins one provider on initial load; the
    // others are fetched the first time the user opens the dropdown.
    private val _lyricsProviderStates = MutableStateFlow<Map<String, ProviderLoadState>>(emptyMap())
    val lyricsProviderStates: StateFlow<Map<String, ProviderLoadState>> = _lyricsProviderStates.asStateFlow()

    // videoId the lyricsProviderStates map currently corresponds to. Guards against
    // racing fetches when the user skips tracks rapidly.
    private var lyricsProviderTrackId: String? = null
    private var lyricsLazyLoadJob: Job? = null

    // ── Crossfade ──
    private val crossfadeManager = SimpleCrossfadeManager(application.applicationContext)
    private val _crossfadeEnabled = MutableStateFlow(
        com.proj.Musicality.config.AppConfig.crossfadeEnabled.value
    )
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private var positionJob: Job? = null
    private var fetchAndPlayJob: Job? = null
    private var playerListener: Player.Listener? = null
    private var observedPlayer: Player? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var serviceStarted = false
    private var upNextJob: Job? = null
    private var libraryQueueExtensionJob: Job? = null
    private var librarySeedQueueSize: Int = 0
    private var libraryTailExtensionRequested: Boolean = false
    private var lastCrossfadeArrivalTrackId: String? = null
    private var lastCrossfadeArrivalRealtimeMs: Long = 0L
    private var lastRewindRealtimeMs: Long = 0L
    private var playedCacheTimerJob: Job? = null
    private var pendingPlayedCacheVideoId: String? = null
    private var consecutivePlaybackFailures = 0
    private val listeningHistoryRepository =
        ListeningHistoryRepository.getInstance(application.applicationContext)
    private val libraryRepository =
        LibraryRepository.getInstance(application.applicationContext)

    init {
        AudioFileCache.init(application.applicationContext)
        if (_crossfadeEnabled.value) {
            crossfadeManager.setEnabled(true)
        }
        connectToSession()
        viewModelScope.launch {
            PlaybackService.skipEvents.collect { direction ->
                Log.d(TAG, "Skip event from notification: direction=$direction")
                if (direction > 0) skipNext() else skipPrev()
            }
        }
        viewModelScope.launch {
            com.proj.Musicality.config.AppConfig.crossfadeEnabled.collect { enabled ->
                if (enabled != _crossfadeEnabled.value) {
                    _crossfadeEnabled.value = enabled
                    crossfadeManager.setEnabled(enabled, getDelegatingPlayer())
                }
            }
        }
    }

    fun toggleCrossfade() {
        val enabled = !_crossfadeEnabled.value
        _crossfadeEnabled.value = enabled
        com.proj.Musicality.config.AppConfig.setCrossfadeEnabled(enabled)
        crossfadeManager.setEnabled(enabled, getDelegatingPlayer())
    }

    private fun ensureServiceStarted() {
        if (!serviceStarted) {
            val app = getApplication<Application>()
            app.startService(Intent(app, PlaybackService::class.java))
            serviceStarted = true
            Log.d(TAG, "Service start requested")
        }
    }

    private fun connectToSession() {
        if (controllerFuture != null || mediaController != null) return
        val app = getApplication<Application>()
        val sessionToken = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { controller ->
                        mediaController = controller
                        setupPlayerListener()
                        syncStateFromPlayer(controller)
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Failed to connect MediaController", error)
                        controllerFuture = null
                        mediaController = null
                    }
            },
            ContextCompat.getMainExecutor(app)
        )
    }

    private fun activePlayer(): Player? = mediaController ?: PlaybackService.delegatingPlayer

    private fun getDelegatingPlayer(): CrossfadeDelegatingPlayer? = PlaybackService.delegatingPlayer

    private fun setupPlayerListener() {
        val player = activePlayer() ?: return
        playerListener?.let { listener ->
            observedPlayer?.removeListener(listener)
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "onIsPlayingChanged: $isPlaying")
                if (_crossfadeEnabled.value && crossfadeManager.isTransitioning()) {
                    val crossfadePlaying = currentCrossfadeIsPlaying()
                    _state.update { it.copy(isPlaying = crossfadePlaying) }
                    if (crossfadePlaying) startPositionPolling() else stopPositionPolling()
                    return
                }
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startPositionPolling() else stopPositionPolling()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(TAG, "onPlaybackStateChanged: $playbackState")
                if (_crossfadeEnabled.value && crossfadeManager.isTransitioning()) {
                    // During crossfade, callbacks can still come from the outgoing player.
                    // Avoid syncing visual metadata from that stale source to prevent
                    // brief artwork/gradient flicker back to the previous track.
                    return
                }
                syncStateFromPlayer(player)
                if (playbackState == Player.STATE_READY) {
                    val dur = player.duration
                    if (dur > 0) {
                        _state.update { it.copy(durationMs = dur) }
                    }
                }
                if (playbackState == Player.STATE_ENDED) {
                    // During crossfade, the outgoing player reaching END is expected — ignore
                    if (_crossfadeEnabled.value && crossfadeManager.isTransitioning()) {
                        return
                    }
                    autoAdvance()
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _state.update { it.copy(repeatMode = repeatMode) }
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                if (_crossfadeEnabled.value && crossfadeManager.isTransitioning()) return
                syncStateFromPlayer(player)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "onPlayerError: code=${error.errorCode} msg=${error.localizedMessage}", error.cause)
                val failedId = _state.value.currentItem?.videoId ?: return
                handlePlaybackFailure(failedId, "ExoPlayer error: code=${error.errorCode}")
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                if (_crossfadeEnabled.value && crossfadeManager.isTransitioning()) return
                syncStateFromPlayer(player)
            }
        }
        playerListener = listener
        observedPlayer = player
        player.addListener(listener)
    }

    /**
     * Keep UI state in sync with the active MediaSession player.
     * Important when app is reopened from notification after process/UI recreation.
     */
    fun syncPlaybackStateFromSession() {
        syncPlaybackStateFromSessionWithRetry()
    }

    fun syncPlaybackStateFromSessionWithRetry(
        timeoutMs: Long = 3_000L,
        retryIntervalMs: Long = 100L
    ) {
        viewModelScope.launch {
            connectToSession()
            var waitedMs = 0L
            while (activePlayer() == null && waitedMs < timeoutMs) {
                delay(retryIntervalMs)
                waitedMs += retryIntervalMs
                connectToSession()
            }
            syncStateFromPlayer(activePlayer())
        }
    }

    private fun syncStateFromPlayer(player: Player?) {
        if (player == null) return
        val sessionItem = player.currentMediaItem
        val metadata = sessionItem?.mediaMetadata
        val currentState = _state.value
        val existingItem = currentState.currentItem

        val mediaId = sessionItem?.mediaId?.takeIf { it.isNotBlank() }
            ?: existingItem?.videoId
            ?: return
        val title = metadata?.title?.toString().orEmpty().ifBlank { existingItem?.title.orEmpty() }
        val artist = metadata?.artist?.toString().orEmpty().ifBlank { existingItem?.artistName.orEmpty() }
        val album = metadata?.albumTitle?.toString().orEmpty().ifBlank { existingItem?.albumName.orEmpty() }
        val artwork = metadata?.artworkUri?.toString() ?: existingItem?.thumbnailUrl

        val restored = MediaItem(
            videoId = mediaId,
            title = title.ifBlank { "Now Playing" },
            artistName = artist.ifBlank { "Unknown Artist" },
            artistId = existingItem?.artistId,
            albumName = album.ifBlank { null },
            albumId = existingItem?.albumId,
            thumbnailUrl = artwork,
            durationText = existingItem?.durationText,
            musicVideoType = existingItem?.musicVideoType
        )

        val existingQueue = currentState.queue
        val existingQueueIndex = existingQueue.items.indexOfFirst { it.videoId == mediaId }
        val queue = when {
            existingQueueIndex >= 0 -> {
                val updatedItems = existingQueue.items.toMutableList()
                updatedItems[existingQueueIndex] = restored
                existingQueue.copy(items = updatedItems, currentIndex = existingQueueIndex)
            }
            else -> PlaybackQueue(
                items = listOf(restored),
                currentIndex = 0,
                source = QueueSource.SINGLE
            )
        }

        _state.update {
            it.copy(
                currentItem = restored,
                queue = queue,
                isPlaying = player.isPlaying,
                durationMs = player.duration.takeIf { duration -> duration > 0 } ?: it.durationMs,
                repeatMode = player.repeatMode
            )
        }
        _positionMs.value = player.currentPosition.coerceAtLeast(0L)
        if (player.isPlaying) startPositionPolling() else stopPositionPolling()
    }

    private fun currentCrossfadeIsPlaying(): Boolean {
        val delegatingPlayer = getDelegatingPlayer() ?: return activePlayer()?.isPlaying == true
        return delegatingPlayer.activePlayer.isPlaying || delegatingPlayer.inactivePlayer.isPlaying
    }

    fun playQueue(queue: PlaybackQueue) {
        cancelUserDrivenCrossfade(reason = "playQueue")
        viewModelScope.launch {
            val item = queue.items[queue.currentIndex]
            resetLibraryTailExtensionContext(queue)
            Log.d(TAG, "playQueue: index=${queue.currentIndex}, videoId='${item.videoId}', title='${item.title}'")
            Log.d(
                "TapTrace",
                "playQueue initial-currentItem: videoId='${item.videoId}' title='${item.title}' " +
                    "artistName='${item.artistName}' durationText=${item.durationText} source=${queue.source}"
            )
            _state.update {
                it.copy(
                    currentItem = item,
                    queue = queue,
                    isPlaying = false,
                    durationMs = 0L
                )
            }
            _positionMs.value = 0L
            fetchAndPlay(item)
            if (queue.source == QueueSource.SINGLE || queue.source == QueueSource.SEARCH) {
                loadUpNextQueue(item.videoId)
            } else {
                prefetchNext(queue)
            }
            maybeExtendLibraryQueueFromCurrentPosition()
        }
    }

    fun playSingle(item: MediaItem, searchQuery: String? = null) {
        Log.d(TAG, "playSingle: videoId='${item.videoId}', title='${item.title}'")
        val source = if (searchQuery != null) QueueSource.SEARCH else QueueSource.SINGLE
        val queue = PlaybackQueue(listOf(item), 0, source, searchQuery = searchQuery)
        playQueue(queue)
    }

    private suspend fun resolveQueueHeadToAtv(queue: PlaybackQueue): PlaybackQueue {
        if (queue.items.isEmpty() || queue.currentIndex !in queue.items.indices) return queue
        val currentItem = queue.items[queue.currentIndex]
        val resolution = runCatching {
            SongArtResolver.resolveToAtv(currentItem)
        }.getOrNull() ?: return queue

        val resolvedVideoId = resolution.videoId.takeIf { it.isNotBlank() } ?: currentItem.videoId
        val resolvedThumb = resolution.thumbnailUrl ?: currentItem.thumbnailUrl
        val resolvedType = resolution.musicVideoType ?: currentItem.musicVideoType

        val resolvedItem = currentItem.copy(
            videoId = resolvedVideoId,
            thumbnailUrl = resolvedThumb,
            musicVideoType = resolvedType
        )
        if (resolvedItem == currentItem) return queue

        val updated = queue.items.toMutableList()
        updated[queue.currentIndex] = resolvedItem
        return queue.copy(items = updated)
    }

    fun skipNext() {
        cancelUserDrivenCrossfade(reason = "skipNext")
        advanceToNextInternal()
    }

    private fun advanceToNextInternal() {
        val current = _state.value
        val next = nextIndexForQueue(current.queue) ?: return
        Log.d(TAG, "skipNext: currentIndex=${current.queue.currentIndex}, next=$next, queueSize=${current.queue.items.size}")
        val nextItem = current.queue.items[next]
        _state.update {
            it.copy(
                currentItem = nextItem,
                queue = it.queue.copy(currentIndex = next),
                isPlaying = false,
                durationMs = 0L
            )
        }
        _positionMs.value = 0L
        fetchAndPlay(nextItem)
        prefetchNext(_state.value.queue)
        maybeExtendLibraryQueueFromCurrentPosition()
    }

    fun skipToIndex(index: Int) {
        cancelUserDrivenCrossfade(reason = "skipToIndex")
        val current = _state.value
        if (index !in current.queue.items.indices || index == current.queue.currentIndex) return
        val targetItem = current.queue.items[index]
        _state.update {
            it.copy(
                currentItem = targetItem,
                queue = it.queue.copy(currentIndex = index),
                durationMs = 0L
            )
        }
        _positionMs.value = 0L
        fetchAndPlay(targetItem)
        prefetchNext(_state.value.queue)
        maybeExtendLibraryQueueFromCurrentPosition()
    }

    fun playNext(item: MediaItem) {
        val current = _state.value
        if (current.currentItem == null) {
            playSingle(item)
            return
        }

        val updatedItems = current.queue.items.toMutableList()
        val insertIndex = (current.queue.currentIndex + 1).coerceAtMost(updatedItems.size)
        val existingIndex = updatedItems.indexOfFirst { it.videoId == item.videoId }

        if (existingIndex >= 0) {
            val existingItem = updatedItems.removeAt(existingIndex)
            val adjustedIndex = if (existingIndex < insertIndex) insertIndex - 1 else insertIndex
            updatedItems.add(adjustedIndex.coerceIn(0, updatedItems.size), existingItem)
        } else {
            updatedItems.add(insertIndex, item)
        }

        _state.update {
            it.copy(
                queue = it.queue.copy(items = updatedItems)
            )
        }
    }

    fun addToQueue(item: MediaItem) {
        val current = _state.value
        if (current.currentItem == null) {
            playSingle(item)
            return
        }

        val updatedItems = current.queue.items.toMutableList()
        val existingIndex = updatedItems.indexOfFirst { it.videoId == item.videoId }
        if (existingIndex >= 0) {
            val existingItem = updatedItems.removeAt(existingIndex)
            updatedItems.add(existingItem)
        } else {
            updatedItems.add(item)
        }

        _state.update {
            it.copy(
                queue = it.queue.copy(items = updatedItems)
            )
        }
    }

    fun skipPrev() {
        val wasTransitioning = crossfadeManager.isTransitioning()
        cancelUserDrivenCrossfade(reason = "skipPrev")
        val current = _state.value
        val currentTrackId = current.currentItem?.videoId
        val effectivePlayer = activePlayer()
        val shouldForcePrevious =
            wasTransitioning || isWithinCrossfadePreviousWindow(currentTrackId)
                || isWithinRewindPreviousWindow()

        if (!shouldForcePrevious && effectivePlayer != null && shouldRestartCurrentTrack(effectivePlayer.currentPosition)) {
            crossfadeManager.resetTriggerForTrack(currentTrackId)
            effectivePlayer.seekTo(0L)
            _positionMs.value = 0L
            lastRewindRealtimeMs = SystemClock.elapsedRealtime()
            return
        }
        val prev = prevIndexForQueue(current.queue) ?: return
        Log.d(TAG, "skipPrev: currentIndex=${current.queue.currentIndex}, prev=$prev")
        val prevItem = current.queue.items[prev]
        _state.update {
            it.copy(
                currentItem = prevItem,
                queue = it.queue.copy(currentIndex = prev),
                isPlaying = false,
                durationMs = 0L
            )
        }
        _positionMs.value = 0L
        fetchAndPlay(prevItem)
        clearCrossfadePreviousWindow()
    }

    fun removeFromQueue(index: Int) {
        val current = _state.value
        val items = current.queue.items
        if (index !in items.indices || items.size <= 1) return
        val newItems = items.toMutableList().apply { removeAt(index) }
        val currentIdx = current.queue.currentIndex
        val newIndex = when {
            index < currentIdx -> currentIdx - 1
            index == currentIdx -> {
                val nextIdx = currentIdx.coerceAtMost(newItems.lastIndex)
                val nextItem = newItems[nextIdx]
                _state.update {
                    it.copy(
                        currentItem = nextItem,
                        queue = it.queue.copy(items = newItems, currentIndex = nextIdx),
                        durationMs = 0L
                    )
                }
                _positionMs.value = 0L
                fetchAndPlay(nextItem)
                return
            }
            else -> currentIdx
        }
        _state.update { it.copy(queue = it.queue.copy(items = newItems, currentIndex = newIndex)) }
    }

    fun moveInQueue(from: Int, to: Int) {
        val current = _state.value
        val items = current.queue.items
        if (from !in items.indices || to !in items.indices || from == to) return
        val newItems = items.toMutableList().apply {
            val item = removeAt(from)
            add(to, item)
        }
        val currentIdx = current.queue.currentIndex
        val newIndex = when (currentIdx) {
            from -> to
            in (minOf(from, to)..maxOf(from, to)) -> {
                if (from < to) currentIdx - 1 else currentIdx + 1
            }
            else -> currentIdx
        }
        _state.update { it.copy(queue = it.queue.copy(items = newItems, currentIndex = newIndex)) }
    }

    fun togglePlayPause() {
        cancelUserDrivenCrossfade(reason = "togglePlayPause")

        val exo = activePlayer() ?: return
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun seekTo(positionMs: Long) {
        cancelUserDrivenCrossfade(reason = "seekTo")
        val current = _state.value
        val exo = activePlayer() ?: return
        exo.seekTo(positionMs)
        _positionMs.value = positionMs
        crossfadeManager.onManualSeek(
            trackId = current.currentItem?.videoId,
            positionMs = positionMs,
            durationMs = current.durationMs
        )
    }

    fun toggleRepeatMode() {
        val exo = activePlayer() ?: return
        exo.repeatMode = if (exo.repeatMode == Player.REPEAT_MODE_ONE) {
            Player.REPEAT_MODE_OFF
        } else {
            Player.REPEAT_MODE_ONE
        }
        _state.update { it.copy(repeatMode = exo.repeatMode) }
    }

    private fun autoAdvance() {
        val current = _state.value
        val next = nextIndexForQueue(current.queue)
        if (next != null) {
            Log.d(TAG, "autoAdvance: advancing to index $next")
            skipNext()
        } else {
            Log.d(TAG, "autoAdvance: end of queue")
            _state.update { it.copy(isPlaying = false) }
        }
    }

    private fun fetchLyrics(item: MediaItem) {
        lyricsLazyLoadJob?.cancel()
        lyricsLazyLoadJob = null
        lyricsProviderTrackId = item.videoId
        // All providers start Idle; the race winner is promoted to Loaded below.
        _lyricsProviderStates.value = LyricsHelper.providerNames.associateWith { ProviderLoadState.Idle }

        viewModelScope.launch {
            val current = _lyricsState.value
            if (current !is LyricsState.Loading) {
                _lyricsState.value = LyricsState.Loading
            }
            val state = LyricsRepository.fetchLyrics(item)
            // If the user already moved on to a new track, ignore this stale result.
            if (lyricsProviderTrackId != item.videoId) return@launch
            _lyricsState.value = state
            if (state is LyricsState.Loaded) {
                state.provider?.let { winnerName ->
                    val snapshot = com.proj.Musicality.data.model.ProviderLyricsSnapshot(
                        lines = state.lines,
                        isSynced = state.isSynced,
                    )
                    _lyricsProviderStates.update { current ->
                        current + (winnerName to ProviderLoadState.Loaded(snapshot))
                    }
                }
            } else if (state is LyricsState.NotFound) {
                // No provider had anything; mark them all unavailable so the dropdown
                // doesn't pretend we can try again.
                _lyricsProviderStates.update { current ->
                    current.mapValues { ProviderLoadState.Unavailable }
                }
            }
        }
    }

    /**
     * Fetches every still-Idle provider for the given track in parallel and updates
     * [lyricsProviderStates]. Safe to call repeatedly; the actual fetches are deduped
     * by [LyricsHelper.fetchByProvider]'s per-provider cache.
     */
    fun loadRemainingLyricsProviders(item: MediaItem) {
        if (lyricsProviderTrackId != item.videoId) return
        if (lyricsLazyLoadJob?.isActive == true) return
        val targets = _lyricsProviderStates.value
            .filter { it.value is ProviderLoadState.Idle }
            .keys
        if (targets.isEmpty()) return

        // Optimistically flip them to Loading so the dropdown shows spinners.
        _lyricsProviderStates.update { current ->
            current.mapValues { (name, st) ->
                if (name in targets) ProviderLoadState.Loading else st
            }
        }

        lyricsLazyLoadJob = viewModelScope.launch {
            targets.forEach { name ->
                launch {
                    val raw = LyricsRepository.fetchProvider(item, name)
                    if (lyricsProviderTrackId != item.videoId) return@launch
                    val snapshot = raw?.let { LyricsRepository.toSnapshot(item, it) }
                    _lyricsProviderStates.update { current ->
                        current + (name to (snapshot?.let { ProviderLoadState.Loaded(it) }
                            ?: ProviderLoadState.Unavailable))
                    }
                }
            }
        }
    }

    /** Swap the active lyrics view to the given provider. No-op if not yet loaded. */
    fun switchLyricsProvider(providerName: String) {
        val states = _lyricsProviderStates.value
        val loaded = states[providerName] as? ProviderLoadState.Loaded ?: return
        val current = _lyricsState.value as? LyricsState.Loaded
        if (current?.provider == providerName) return
        _lyricsState.value = LyricsState.Loaded(
            lines = loaded.snapshot.lines,
            isSynced = loaded.snapshot.isSynced,
            provider = providerName,
        )
    }

    private fun fetchAndPlay(item: MediaItem) {
        crossfadeManager.resetTriggerForTrack(item.videoId)
        fetchLyrics(item)
        ensureServiceStarted()
        _loadingTrackId.value = item.videoId
        viewModelScope.launch(Dispatchers.IO) {
            if (!com.proj.Musicality.config.AppConfig.listeningHistoryPaused.value) {
                runCatching { listeningHistoryRepository.recordPlayback(item) }
                HomePrefetchManager.prefetch(getApplication<Application>().applicationContext, forcePersonalization = true)
            }
        }

        AudioFileCache.unpinAll(except = pendingPlayedCacheVideoId)
        AudioFileCache.pin(item.videoId)

        playedCacheTimerJob?.cancel()
        fetchAndPlayJob?.cancel()
        fetchAndPlayJob = viewModelScope.launch(Dispatchers.Default) {
            var waited = 0
            while (activePlayer() == null && waited < 5000) {
                delay(50)
                waited += 50
            }
            if (activePlayer() == null) {
                Log.e(TAG, "fetchAndPlay: Service player not available after 5s")
                withContext(Dispatchers.Main) { handlePlaybackFailure(item.videoId, "player not available") }
                return@launch
            }

            withContext(Dispatchers.Main) {
                setupPlayerListener()
                // Atomically swap MediaSession metadata to the target track BEFORE the
                // audio resolves. Without this, the notification / Bluetooth / Auto would
                // keep showing the previous track for the entire download window. The
                // placeholder carries the new title/artist/artwork but has no URI, so the
                // player sits in STATE_IDLE — the real setMediaItem in startExoPlayback
                // then replaces both metadata and URI together when the audio is ready.
                val placeholder = buildSessionPlaceholderMedia(item)
                getDelegatingPlayer()?.let { dp ->
                    if (dp.activePlayer.isPlaying) dp.activePlayer.pause()
                    // The inactive player may be holding a primed crossfade target that's no
                    // longer relevant after a user-driven skip; clear it so it can't bleed in.
                    dp.inactivePlayer.stop()
                    dp.inactivePlayer.clearMediaItems()
                    runCatching { dp.activePlayer.setMediaItem(placeholder) }
                } ?: activePlayer()?.let { exo ->
                    if (exo.isPlaying) exo.pause()
                    runCatching { exo.setMediaItem(placeholder) }
                }
            }

            // Short-circuit on local files (downloads + played cache) so playback works offline
            // and avoids a network round-trip when duration is unknown. Local audio is always
            // short-form — long-form is streamed, never cached — so the long-form gating below
            // is irrelevant once we have a file on disk.
            val localFile = withContext(Dispatchers.IO) { libraryRepository.findLocalAudioFile(item.videoId) }
            if (localFile != null) {
                Log.d(PLAYBACK_FLOW_TAG, "source=local videoId=${item.videoId} sizeKb=${localFile.length() / 1024}")
                val fileUri = localFile.toURI().toString()
                startExoPlayback(fileUri, item)
                return@launch
            }

            // Played-section queues are a local-only contract: every entry must already be on
            // disk (played cache or downloads). If the file is gone we abort instead of falling
            // through to the network path — otherwise an offline swipe through Played stalls on
            // a doomed stream request.
            if (isLocalOnlyQueue(_state.value.queue.source)) {
                Log.w(TAG, "fetchAndPlay: PLAYED entry '${item.videoId}' has no local file; skipping network fallback")
                withContext(Dispatchers.Main) { handlePlaybackFailure(item.videoId, "PLAYED entry missing local file") }
                return@launch
            }

            val longForm = if (item.durationText != null) {
                val seconds = parseDurationToSeconds(item.durationText)
                Log.d(TAG, "fetchAndPlay: durationText='${item.durationText}' parsed=${seconds}s threshold=${LONG_FORM_THRESHOLD_SECONDS}s")
                seconds > LONG_FORM_THRESHOLD_SECONDS
            } else {
                val details = withContext(Dispatchers.IO) {
                    StreamRequestResolver.fetchSongPlaybackDetails(item.videoId)
                }
                details?.streamUrl?.let { AppCache.putStreamUrl(item.videoId, it) }
                Log.d(TAG, "fetchAndPlay: durationText=null, API lengthSeconds=${details?.lengthSeconds} threshold=${LONG_FORM_THRESHOLD_SECONDS}s")
                (details?.lengthSeconds ?: 0L) > LONG_FORM_THRESHOLD_SECONDS
            }

            Log.d(TAG, "fetchAndPlay: videoId=${item.videoId} longForm=$longForm")
            if (longForm) {
                val streamUrl = withContext(Dispatchers.IO) { resolveStreamUrl(item.videoId) }
                if (streamUrl != null) {
                    val playableUrl = streamUrl.withFullRange()
                    Log.d(TAG, "fetchAndPlay: streaming long-form '${item.videoId}' (dur=${item.durationText})")
                    startLongFormPlayback(playableUrl, item)
                } else {
                    Log.e(TAG, "fetchAndPlay: stream URL unavailable for long-form '${item.videoId}'")
                    withContext(Dispatchers.Main) { handlePlaybackFailure(item.videoId, "long-form stream URL unavailable") }
                }
                return@launch
            }

            if (!_crossfadeEnabled.value) {
                val streamUrl = withContext(Dispatchers.IO) { resolveStreamUrl(item.videoId) }
                if (streamUrl == null) {
                    Log.e(TAG, "fetchAndPlay: stream URL unavailable for '${item.videoId}'")
                    withContext(Dispatchers.Main) { handlePlaybackFailure(item.videoId, "stream URL unavailable") }
                    return@launch
                }

                Log.d(PLAYBACK_FLOW_TAG, "source=stream videoId=${item.videoId} parallelDownload=true")
                startLongFormPlayback(streamUrl.withFullRange(), item)

                val file = AudioFileCache.getOrDownload(item.videoId)
                if (file != null) {
                    Log.d(PLAYBACK_FLOW_TAG, "download=complete videoId=${item.videoId} sizeKb=${file.length() / 1024}")
                    if (_state.value.currentItem?.videoId == item.videoId) {
                        Log.d(PLAYBACK_FLOW_TAG, "source=stream retained videoId=${item.videoId}")
                        startPlayedCacheTimer(item, file)
                    } else {
                        Log.d(PLAYBACK_FLOW_TAG, "playedCache=skipped videoId=${item.videoId} reason=mediaChanged")
                    }
                } else {
                    // Streaming can continue even if the background Played-cache download fails.
                    Log.w(PLAYBACK_FLOW_TAG, "download=failed videoId=${item.videoId} streamContinues=true")
                }
                return@launch
            }

            val file = AudioFileCache.getOrDownload(item.videoId)
            if (file != null) {
                val fileUri = file.toURI().toString()
                Log.d(TAG, "fetchAndPlay: playing '${item.videoId}' (${file.length() / 1024}KB)")
                startExoPlayback(fileUri, item)
                startPlayedCacheTimer(item, file)
            } else {
                Log.e(TAG, "fetchAndPlay: Failed to download audio for '${item.videoId}'")
                withContext(Dispatchers.Main) { handlePlaybackFailure(item.videoId, "audio download failed") }
            }
        }
    }

    private fun buildSessionPlaceholderMedia(item: MediaItem): androidx.media3.common.MediaItem {
        // No URI: ExoPlayer holds this in STATE_IDLE without I/O. Only the metadata
        // propagates to MediaSession.
        return androidx.media3.common.MediaItem.Builder()
            .setMediaId(item.videoId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.artistName)
                    .setAlbumTitle(item.albumName)
                    .setArtworkUri(item.thumbnailUrl?.let { Uri.parse(it) })
                    .build()
            )
            .build()
    }

    private fun clearLoadingIfMatches(videoId: String) {
        _loadingTrackId.update { current -> if (current == videoId) null else current }
    }

    private fun handlePlaybackFailure(videoId: String, reason: String) {
        Log.w(TAG, "handlePlaybackFailure: '$videoId' — $reason (consecutive=$consecutivePlaybackFailures)")
        clearLoadingIfMatches(videoId)
        consecutivePlaybackFailures++
        if (consecutivePlaybackFailures >= MAX_CONSECUTIVE_FAILURES) {
            Log.w(TAG, "handlePlaybackFailure: $MAX_CONSECUTIVE_FAILURES consecutive failures, stopping")
            _state.update { it.copy(isPlaying = false) }
            consecutivePlaybackFailures = 0
            return
        }
        if (_state.value.currentItem?.videoId != videoId) return
        autoAdvance()
    }

    private fun startPlayedCacheTimer(item: MediaItem, sourceFile: File) {
        playedCacheTimerJob?.cancel()
        pendingPlayedCacheVideoId = item.videoId
        AudioFileCache.pin(item.videoId)
        Log.d(PLAYBACK_FLOW_TAG, "playedCache=scheduled videoId=${item.videoId} thresholdMs=$PLAYED_CACHE_MIN_PLAY_MS")
        playedCacheTimerJob = viewModelScope.launch {
            try {
                while (_positionMs.value < PLAYED_CACHE_MIN_PLAY_MS) {
                    delay(1_000L)
                    if (_state.value.currentItem?.videoId != item.videoId) return@launch
                }
                withContext(Dispatchers.IO) {
                    runCatching { libraryRepository.cachePlayedSong(item, sourceFile) }
                        .onSuccess { Log.d(PLAYBACK_FLOW_TAG, "playedCache=saved videoId=${item.videoId}") }
                        .onFailure { Log.e(PLAYBACK_FLOW_TAG, "playedCache=failed videoId=${item.videoId}", it) }
                }
            } finally {
                if (pendingPlayedCacheVideoId == item.videoId) {
                    AudioFileCache.unpin(item.videoId)
                    pendingPlayedCacheVideoId = null
                }
            }
        }
    }

    private suspend fun startExoPlayback(url: String, item: MediaItem) {
        withContext(Dispatchers.Main) {
            if (_state.value.currentItem?.videoId != item.videoId) {
                Log.d(TAG, "startExoPlayback: skipping stale playback for '${item.videoId}'")
                clearLoadingIfMatches(item.videoId)
                return@withContext
            }
            val exo = activePlayer() ?: return@withContext
            val isHttp = url.startsWith("http")
            Log.d(TAG, "startExoPlayback: ${if (isHttp) "HTTP stream" else "local file"} (${url.length} chars) for '${item.title}', url=${url.take(120)}")

            val media3Item = androidx.media3.common.MediaItem.Builder()
                .setMediaId(item.videoId)
                .setUri(url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artistName)
                        .setAlbumTitle(item.albumName)
                        .setArtworkUri(item.thumbnailUrl?.let { Uri.parse(it) })
                        .build()
                )
                .build()

            exo.setMediaItem(media3Item)
            exo.prepare()
            exo.play()
            consecutivePlaybackFailures = 0
            clearLoadingIfMatches(item.videoId)
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun startLongFormPlayback(url: String, item: MediaItem) {
        withContext(Dispatchers.Main) {
            if (_state.value.currentItem?.videoId != item.videoId) {
                Log.d(TAG, "startLongFormPlayback: skipping stale playback for '${item.videoId}'")
                clearLoadingIfMatches(item.videoId)
                return@withContext
            }
            val dp = getDelegatingPlayer() ?: return@withContext
            val exo = dp.activePlayer
            Log.d(TAG, "startLongFormPlayback: HTTP stream for '${item.title}', url=${url.take(120)}")

            val httpFactory = OkHttpDataSource.Factory(
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
            )
            val resolvingFactory = ResolvingDataSource.Factory(httpFactory) { dataSpec ->
                val uri = dataSpec.uri
                val uriStr = uri.toString()
                if (uriStr.contains("range=") && dataSpec.position > 0L) {
                    val newUri = uriStr.replace(Regex("range=[^&]*"), "range=${dataSpec.position}-")
                    Log.d(TAG, "longForm: rewriting range for position=${dataSpec.position}")
                    dataSpec.buildUpon()
                        .setUri(Uri.parse(newUri))
                        .setPosition(0)
                        .setUriPositionOffset(0)
                        .build()
                } else {
                    dataSpec
                }
            }

            val media3Item = androidx.media3.common.MediaItem.Builder()
                .setMediaId(item.videoId)
                .setUri(url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artistName)
                        .setAlbumTitle(item.albumName)
                        .setArtworkUri(item.thumbnailUrl?.let { Uri.parse(it) })
                        .build()
                )
                .build()

            val mediaSource = ProgressiveMediaSource.Factory(resolvingFactory)
                .createMediaSource(media3Item)

            exo.setMediaSource(mediaSource)
            exo.prepare()
            exo.play()
            consecutivePlaybackFailures = 0
            clearLoadingIfMatches(item.videoId)
        }
    }

    // ── Crossfade: prepare the next track for dual-player blending ──

    private suspend fun prepareCrossfadeNextTrack(): CrossfadeNextTrack? {
        val current = _state.value
        val nextIndex = nextIndexForQueue(current.queue) ?: return null

        val nextItem = current.queue.items[nextIndex]
        val localFile = withContext(Dispatchers.IO) { libraryRepository.findLocalAudioFile(nextItem.videoId) }
        val file = if (localFile != null) {
            Log.d(TAG, "prepareCrossfade: reusing local file for '${nextItem.videoId}' (${localFile.length() / 1024}KB)")
            localFile
        } else if (isLocalOnlyQueue(current.queue.source)) {
            // Local-only queues (PLAYED) must never reach for the network. If the file is
            // missing, skip the crossfade prep — the user will hard-cut at end of track.
            Log.w(TAG, "prepareCrossfade: PLAYED next '${nextItem.videoId}' has no local file; skipping prep")
            return null
        } else {
            AudioFileCache.getOrDownload(nextItem.videoId) ?: return null
        }
        val uri = file.toURI().toString()
        val media3 = androidx.media3.common.MediaItem.Builder()
            .setMediaId(nextItem.videoId)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(nextItem.title)
                    .setArtist(nextItem.artistName)
                    .setAlbumTitle(nextItem.albumName)
                    .setArtworkUri(nextItem.thumbnailUrl?.let { Uri.parse(it) })
                    .build()
            )
            .build()

        // Pin both tracks during crossfade (current + next)
        AudioFileCache.pin(nextItem.videoId)

        return CrossfadeNextTrack(
            mediaItem = media3,
            onCrossfadeStart = {
                // Switch UI to next song immediately when crossfade audio begins
                noteCrossfadeArrival(nextItem.videoId)
                _state.update {
                    it.copy(
                        currentItem = nextItem,
                        queue = it.queue.copy(currentIndex = nextIndex),
                        isPlaying = true,
                        durationMs = 0L
                    )
                }
                _positionMs.value = 0L
                fetchLyrics(nextItem)
                withContext(Dispatchers.IO) {
                    if (!com.proj.Musicality.config.AppConfig.listeningHistoryPaused.value) {
                        runCatching { listeningHistoryRepository.recordPlayback(nextItem) }
                        HomePrefetchManager.prefetch(getApplication<Application>().applicationContext, forcePersonalization = true)
                    }
                }
                maybeExtendLibraryQueueFromCurrentPosition()
                if (localFile == null) {
                    startPlayedCacheTimer(nextItem, file)
                }
            },
            onCrossfadeComplete = {
                withContext(Dispatchers.Main) { setupPlayerListener() }
                AudioFileCache.unpinAll(except = pendingPlayedCacheVideoId)
                AudioFileCache.pin(nextItem.videoId)
                prefetchNext(_state.value.queue)
            }
        )
    }

    // ── Position polling + crossfade monitoring ──

    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                val exo = activePlayer()
                val dp = getDelegatingPlayer()
                if (exo != null) {
                    // During crossfade, track the incoming player's position
                    val pos = if (dp != null && crossfadeManager.isTransitioning()) {
                        val incomingPos = crossfadeManager.getIncomingPosition(dp)
                        if (incomingPos >= 0) incomingPos else exo.currentPosition
                    } else {
                        exo.currentPosition
                    }
                    if (pos != _positionMs.value) {
                        _positionMs.value = pos
                    }
                    // Pick up duration from incoming player during crossfade
                    if (dp != null && crossfadeManager.isTransitioning()) {
                        val incomingDur = crossfadeManager.getIncomingDuration(dp)
                        if (incomingDur > 0 && _state.value.durationMs <= 0) {
                            _state.update { it.copy(durationMs = incomingDur) }
                        }
                    }

                    // Monitor for crossfade trigger (skip for long-form content)
                    if (dp != null) {
                        val currentState = _state.value
                        val currentTrackId = currentState.currentItem?.videoId
                        val duration = currentState.durationMs
                        val nextIndex = nextIndexForQueue(currentState.queue)
                        val hasNext = nextIndex != null
                        val currentIsLongForm = currentState.currentItem?.let { isLongFormContent(it) } == true
                        val nextIsLongForm = hasNext && isLongFormContent(currentState.queue.items[nextIndex!!])
                        crossfadeManager.monitor(
                            scope = viewModelScope,
                            trackId = currentTrackId,
                            hasNext = hasNext && !currentIsLongForm && !nextIsLongForm,
                            positionMs = pos,
                            durationMs = duration,
                            delegatingPlayer = dp,
                            repeatMode = currentState.repeatMode
                        ) {
                            prepareCrossfadeNextTrack()
                        }
                    }
                }
                delay(300L)
            }
        }
    }

    private fun stopPositionPolling() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun prefetchNext(queue: PlaybackQueue) {
        if (!_crossfadeEnabled.value) {
            Log.d(TAG, "prefetchNext: skipping full-file prefetch because crossfade is disabled")
            return
        }
        val currentItem = _state.value.currentItem
        if (currentItem != null && isLongFormContent(currentItem)) {
            Log.d(TAG, "prefetchNext: skipping — current track is long-form")
            return
        }
        val nextIdx = nextIndexForQueue(queue) ?: return
        val nextItem = queue.items[nextIdx]
        if (isLongFormContent(nextItem)) {
            Log.d(TAG, "prefetchNext: skipping long-form '${nextItem.videoId}' (${nextItem.durationText})")
            return
        }
        val nextId = nextItem.videoId
        val localOnly = isLocalOnlyQueue(queue.source)
        viewModelScope.launch(Dispatchers.IO) {
            val localFile = libraryRepository.findLocalAudioFile(nextId)
            if (localFile != null) {
                Log.d(TAG, "prefetchNext: already local for '$nextId' (${localFile.length() / 1024}KB)")
                return@launch
            }
            if (localOnly) {
                Log.d(TAG, "prefetchNext: PLAYED next '$nextId' missing locally; skipping network prefetch")
                return@launch
            }
            runCatching {
                AudioFileCache.getOrDownload(nextId)?.let {
                    Log.d(TAG, "prefetchNext: downloaded '$nextId' (${it.length() / 1024}KB)")
                }
            }.onFailure { Log.e(TAG, "prefetchNext: FAILED for '$nextId'", it) }
        }
    }

    private fun parseDurationToSeconds(durationText: String?): Long {
        if (durationText.isNullOrBlank()) return 0L
        val parts = durationText.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0L
        }
    }

    private fun isLongFormContent(item: MediaItem): Boolean {
        return parseDurationToSeconds(item.durationText) > LONG_FORM_THRESHOLD_SECONDS
    }

    private suspend fun resolveStreamUrl(videoId: String): String? {
        val cached = AppCache.getStreamUrl(videoId)
        if (!cached.isNullOrBlank()) return cached
        return runCatching {
            val details = StreamRequestResolver.fetchSongPlaybackDetails(videoId)
            details?.streamUrl?.also { AppCache.putStreamUrl(videoId, it) }
        }.onFailure {
            Log.e(TAG, "resolveStreamUrl: failed for '$videoId'", it)
        }.getOrNull()
    }

    private fun String.withFullRange(): String {
        if (contains("range=")) return replace(Regex("range=[^&]*"), "range=0-")
        return if (contains("?")) "$this&range=0-" else "$this?range=0-"
    }

    private fun resetLibraryTailExtensionContext(queue: PlaybackQueue) {
        libraryQueueExtensionJob?.cancel()
        libraryQueueExtensionJob = null
        if (queue.source == QueueSource.LIBRARY) {
            librarySeedQueueSize = queue.items.size
            libraryTailExtensionRequested = false
        } else {
            librarySeedQueueSize = 0
            libraryTailExtensionRequested = false
        }
    }

    private fun maybeExtendLibraryQueueFromCurrentPosition() {
        val queue = _state.value.queue
        if (queue.source != QueueSource.LIBRARY) return
        if (libraryTailExtensionRequested) return

        val seedSize = librarySeedQueueSize.coerceAtMost(queue.items.size)
        if (seedSize < 2) return

        val triggerIndex = seedSize - 2
        if (queue.currentIndex < triggerIndex) return

        val seedItems = queue.items.take(seedSize).filter { it.videoId.isNotBlank() }
        if (seedItems.isEmpty()) return

        libraryTailExtensionRequested = true
        libraryQueueExtensionJob?.cancel()
        libraryQueueExtensionJob = viewModelScope.launch(Dispatchers.IO) {
            appendMashedUpNext(seedItems)
        }
    }

    private suspend fun appendMashedUpNext(seedItems: List<MediaItem>) {
        val pickedSeeds = seedItems.shuffled().take(minOf(LIBRARY_EXTENSION_SEED_COUNT, seedItems.size))
        if (pickedSeeds.isEmpty()) return

        val existingIds = _state.value.queue.items.mapTo(mutableSetOf()) { it.videoId }
        val combined = mutableListOf<MediaItem>()
        pickedSeeds.forEach { seed ->
            val upNext = fetchUpNextQueue(seed.videoId) ?: return@forEach
            upNext.items.forEach { candidate ->
                val id = candidate.videoId
                if (id.isBlank() || !existingIds.add(id)) return@forEach
                combined.add(candidate)
            }
        }
        if (combined.isEmpty()) return

        _state.update { state ->
            if (state.queue.source != QueueSource.LIBRARY) return@update state
            val seen = state.queue.items.mapTo(mutableSetOf()) { it.videoId }
            val dedupedAppend = combined.filter { it.videoId.isNotBlank() && seen.add(it.videoId) }
            if (dedupedAppend.isEmpty()) {
                state
            } else {
                state.copy(queue = state.queue.copy(items = state.queue.items + dedupedAppend))
            }
        }

        // If extension finished after the original list already ended, continue playback immediately.
        val player = activePlayer()
        if (player?.playbackState == Player.STATE_ENDED) {
            val snapshot = _state.value
            if (snapshot.queue.currentIndex + 1 < snapshot.queue.items.size) {
                withContext(Dispatchers.Main) { advanceToNextInternal() }
            }
        }
    }

    private suspend fun fetchUpNextQueue(seedVideoId: String): PlaybackQueue? {
        val cacheKey = "next:$seedVideoId"
        val cached = AppCache.browse.get(cacheKey) as? PlaybackQueue
        if (cached != null) return cached

        val visitorId = VisitorManager.ensureBrowseVisitorId()
        if (visitorId.isBlank()) {
            Log.e(TAG, "fetchUpNextQueue: browseVisitorId is blank for seed=$seedVideoId")
            return null
        }

        val json = runCatching {
            RequestExecutor.executeNextRequest(seedVideoId, visitorId)
        }.onFailure { Log.e(TAG, "fetchUpNextQueue: executeNextRequest FAILED for seed=$seedVideoId", it) }
            .getOrDefault("")

        val recoveredJson = if (json.isBlank()) {
            val refreshedId = VisitorManager.refreshBrowseVisitorId()
            if (refreshedId.isBlank() || refreshedId == visitorId) {
                ""
            } else {
                runCatching { RequestExecutor.executeNextRequest(seedVideoId, refreshedId) }
                    .onFailure { Log.e(TAG, "fetchUpNextQueue: executeNextRequest retry FAILED for seed=$seedVideoId", it) }
                    .getOrDefault("")
            }
        } else {
            json
        }

        if (recoveredJson.isBlank()) return null
        val queue = NextParser.extractUpNextQueue(recoveredJson) ?: return null
        AppCache.browse.put(cacheKey, queue)
        return queue
    }

    private fun loadUpNextQueue(seedVideoId: String) {
        upNextJob?.cancel()
        upNextJob = viewModelScope.launch(Dispatchers.IO) {
            val queue = fetchUpNextQueue(seedVideoId) ?: run {
                Log.e(TAG, "loadUpNextQueue: failed to fetch queue for seed=$seedVideoId")
                return@launch
            }
            applyUpNextQueue(seedVideoId, queue)
        }
    }

    private fun applyUpNextQueue(seedVideoId: String, upNextQueue: PlaybackQueue) {
        val currentVideoId = _state.value.currentItem?.videoId
        if (currentVideoId != seedVideoId) return

        val idx = upNextQueue.items.indexOfFirst { it.videoId == seedVideoId }.takeIf { it >= 0 }
            ?: upNextQueue.currentIndex

        val fixedQueue = upNextQueue.copy(currentIndex = idx)

        val before = _state.value.currentItem
        val after = fixedQueue.items.getOrNull(idx)
        Log.d(
            "TapTrace",
            "applyUpNextQueue OVERRIDE: videoId='$seedVideoId' " +
                "artistName: '${before?.artistName}' -> '${after?.artistName}' | " +
                "durationText: ${before?.durationText} -> ${after?.durationText} | " +
                "title: '${before?.title}' -> '${after?.title}'"
        )

        _state.update {
            it.copy(
                currentItem = after ?: it.currentItem,
                queue = fixedQueue
            )
        }

        prefetchNext(fixedQueue)
    }

    override fun onCleared() {
        super.onCleared()
        stopPositionPolling()
        fetchAndPlayJob?.cancel()
        upNextJob?.cancel()
        libraryQueueExtensionJob?.cancel()
        crossfadeManager.release()
        observedPlayer?.let { player ->
            playerListener?.let { player.removeListener(it) }
        }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
        observedPlayer = null
    }

    // PLAYED is a local-only circular queue: every entry must already be on disk and the queue
    // loops at both ends. Other sources stop at the end; LIBRARY may extend itself via the
    // up-next mash, but that's handled separately in [maybeExtendLibraryQueueFromCurrentPosition].
    private fun isLocalOnlyQueue(source: QueueSource): Boolean = source == QueueSource.PLAYED

    private fun nextIndexForQueue(queue: PlaybackQueue): Int? {
        val size = queue.items.size
        if (size == 0) return null
        val raw = queue.currentIndex + 1
        return when {
            raw < size -> raw
            isLocalOnlyQueue(queue.source) && size > 0 -> 0
            else -> null
        }
    }

    private fun prevIndexForQueue(queue: PlaybackQueue): Int? {
        val size = queue.items.size
        if (size == 0) return null
        val raw = queue.currentIndex - 1
        return when {
            raw >= 0 -> raw
            isLocalOnlyQueue(queue.source) && size > 0 -> size - 1
            else -> null
        }
    }

    private fun cancelUserDrivenCrossfade(reason: String) {
        crossfadeManager.cancelTransition(
            delegatingPlayer = getDelegatingPlayer(),
            commitToIncoming = true,
            reason = reason
        )
    }

    private fun noteCrossfadeArrival(trackId: String) {
        lastCrossfadeArrivalTrackId = trackId
        lastCrossfadeArrivalRealtimeMs = SystemClock.elapsedRealtime()
    }

    private fun clearCrossfadePreviousWindow() {
        lastCrossfadeArrivalTrackId = null
        lastCrossfadeArrivalRealtimeMs = 0L
        lastRewindRealtimeMs = 0L
    }

    private fun isWithinCrossfadePreviousWindow(trackId: String?): Boolean {
        if (trackId == null || trackId != lastCrossfadeArrivalTrackId) return false
        val elapsed = SystemClock.elapsedRealtime() - lastCrossfadeArrivalRealtimeMs
        return elapsed in 0..CROSSFADE_PREVIOUS_GRACE_MS
    }

    private fun isWithinRewindPreviousWindow(): Boolean {
        if (lastRewindRealtimeMs == 0L) return false
        val elapsed = SystemClock.elapsedRealtime() - lastRewindRealtimeMs
        return elapsed in 0..REWIND_PREVIOUS_WINDOW_MS
    }
}
