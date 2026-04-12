package com.proj.Musicality.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.proj.Musicality.PlaybackService
import com.proj.Musicality.api.LyricsRepository
import com.proj.Musicality.api.RequestExecutor
import com.proj.Musicality.api.VisitorManager
import com.proj.Musicality.cache.AppCache
import com.proj.Musicality.cache.AudioFileCache
import com.proj.Musicality.data.local.ListeningHistoryRepository
import com.proj.Musicality.data.model.LyricsState
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.data.model.PlaybackQueue
import com.proj.Musicality.data.model.QueueSource
import com.proj.Musicality.data.resolver.SongArtResolver
import com.proj.Musicality.crossfade.CrossfadeNextTrack
import com.proj.Musicality.crossfade.CrossfadeDelegatingPlayer
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
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _lyricsState = MutableStateFlow<LyricsState>(LyricsState.Idle)
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()

    // ── Crossfade ──
    private val crossfadeManager = SimpleCrossfadeManager(application.applicationContext)
    private val _crossfadeEnabled = MutableStateFlow(false)
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private var positionJob: Job? = null
    private var playerListener: Player.Listener? = null
    private var observedPlayer: Player? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var serviceStarted = false
    private var upNextJob: Job? = null
    private val listeningHistoryRepository =
        ListeningHistoryRepository.getInstance(application.applicationContext)

    init {
        AudioFileCache.init(application.applicationContext)
        connectToSession()
        // Collect skip events from notification controls
        viewModelScope.launch {
            PlaybackService.skipEvents.collect { direction ->
                Log.d(TAG, "Skip event from notification: direction=$direction")
                if (direction > 0) skipNext() else skipPrev()
            }
        }
    }

    fun toggleCrossfade() {
        val enabled = !_crossfadeEnabled.value
        _crossfadeEnabled.value = enabled
        crossfadeManager.setEnabled(enabled)
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
                        _state.update {
                            it.copy(
                                isPlaying = controller.isPlaying,
                                durationMs = controller.duration.takeIf { duration -> duration > 0 } ?: it.durationMs,
                                repeatMode = controller.repeatMode
                            )
                        }
                        _positionMs.value = controller.currentPosition.coerceAtLeast(0L)
                        if (controller.isPlaying) startPositionPolling()
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Failed to connect MediaController", error)
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
        }
        playerListener = listener
        observedPlayer = player
        player.addListener(listener)
    }

    private fun currentCrossfadeIsPlaying(): Boolean {
        val delegatingPlayer = getDelegatingPlayer() ?: return activePlayer()?.isPlaying == true
        return delegatingPlayer.activePlayer.isPlaying || delegatingPlayer.inactivePlayer.isPlaying
    }

    fun playQueue(queue: PlaybackQueue) {
        viewModelScope.launch {
            val item = queue.items[queue.currentIndex]
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
        }
    }

    fun playSingle(item: MediaItem) {
        Log.d(TAG, "playSingle: videoId='${item.videoId}', title='${item.title}'")
        val queue = PlaybackQueue(listOf(item), 0, QueueSource.SINGLE)
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
        crossfadeManager.cancelTransition()
        advanceToNextInternal()
    }

    private fun advanceToNextInternal() {
        val current = _state.value
        val next = current.queue.currentIndex + 1
        Log.d(TAG, "skipNext: currentIndex=${current.queue.currentIndex}, next=$next, queueSize=${current.queue.items.size}")
        if (next < current.queue.items.size) {
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
        }
    }

    fun skipToIndex(index: Int) {
        if (_crossfadeEnabled.value && crossfadeManager.isTransitioning()) {
            Log.d(TAG, "skipToIndex ignored during crossfade transition")
            return
        }
        crossfadeManager.cancelTransition()
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
        crossfadeManager.cancelTransition()
        val current = _state.value
        val effectivePlayer = activePlayer()
        if (effectivePlayer != null && shouldRestartCurrentTrack(effectivePlayer.currentPosition)) {
            effectivePlayer.seekTo(0L)
            _positionMs.value = 0L
            return
        }
        val prev = current.queue.currentIndex - 1
        Log.d(TAG, "skipPrev: currentIndex=${current.queue.currentIndex}, prev=$prev")
        if (prev >= 0) {
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
        }
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
        val delegatingPlayer = getDelegatingPlayer()
        if (_crossfadeEnabled.value && crossfadeManager.isTransitioning() && delegatingPlayer != null) {
            val shouldPause = delegatingPlayer.activePlayer.isPlaying || delegatingPlayer.inactivePlayer.isPlaying
            if (shouldPause) {
                delegatingPlayer.activePlayer.pause()
                delegatingPlayer.inactivePlayer.pause()
            } else {
                delegatingPlayer.activePlayer.play()
                delegatingPlayer.inactivePlayer.play()
            }
            _state.update { it.copy(isPlaying = !shouldPause) }
            if (shouldPause) stopPositionPolling() else startPositionPolling()
            return
        }

        val exo = activePlayer() ?: return
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun seekTo(positionMs: Long) {
        if (_crossfadeEnabled.value && crossfadeManager.isTransitioning()) {
            Log.d(TAG, "seekTo ignored during crossfade transition")
            return
        }
        val exo = activePlayer() ?: return
        exo.seekTo(positionMs)
        _positionMs.value = positionMs
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
        val next = current.queue.currentIndex + 1
        if (next < current.queue.items.size) {
            Log.d(TAG, "autoAdvance: advancing to index $next")
            skipNext()
        } else {
            Log.d(TAG, "autoAdvance: end of queue")
            _state.update { it.copy(isPlaying = false) }
        }
    }

    private fun fetchLyrics(item: MediaItem) {
        viewModelScope.launch {
            val current = _lyricsState.value
            if (current !is LyricsState.Loading) {
                _lyricsState.value = LyricsState.Loading
            }
            _lyricsState.value = LyricsRepository.fetchLyrics(item)
        }
    }

    private fun fetchAndPlay(item: MediaItem) {
        fetchLyrics(item)
        ensureServiceStarted()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { listeningHistoryRepository.recordPlayback(item) }
            HomePrefetchManager.prefetch(getApplication<Application>().applicationContext, forcePersonalization = true)
        }

        // Unpin previous track, pin current
        AudioFileCache.unpinAll()
        AudioFileCache.pin(item.videoId)

        viewModelScope.launch(Dispatchers.Default) {
            var waited = 0
            while (activePlayer() == null && waited < 5000) {
                delay(50)
                waited += 50
            }
            if (activePlayer() == null) {
                Log.e(TAG, "fetchAndPlay: Service player not available after 5s")
                return@launch
            }

            withContext(Dispatchers.Main) { setupPlayerListener() }

            val file = AudioFileCache.getOrDownload(item.videoId)
            if (file != null) {
                val fileUri = file.toURI().toString()
                Log.d(TAG, "fetchAndPlay: playing from cached file for '${item.videoId}' (${file.length() / 1024}KB)")
                startExoPlayback(fileUri, item)
            } else {
                Log.e(TAG, "fetchAndPlay: Failed to download audio for '${item.videoId}'")
            }
        }
    }

    private suspend fun startExoPlayback(url: String, item: MediaItem) {
        withContext(Dispatchers.Main) {
            val exo = activePlayer() ?: return@withContext
            Log.d(TAG, "startExoPlayback: setting URL (${url.length} chars) for '${item.title}'")

            val media3Item = androidx.media3.common.MediaItem.Builder()
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
        }
    }

    // ── Crossfade: prepare the next track for dual-player blending ──

    private suspend fun prepareCrossfadeNextTrack(): CrossfadeNextTrack? {
        val current = _state.value
        val nextIndex = current.queue.currentIndex + 1
        if (nextIndex !in current.queue.items.indices) return null

        val nextItem = current.queue.items[nextIndex]
        val file = AudioFileCache.getOrDownload(nextItem.videoId) ?: return null
        val uri = file.toURI().toString()
        val media3 = androidx.media3.common.MediaItem.Builder()
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
                    runCatching { listeningHistoryRepository.recordPlayback(nextItem) }
                    HomePrefetchManager.prefetch(getApplication<Application>().applicationContext, forcePersonalization = true)
                }
            },
            onCrossfadeComplete = {
                // Crossfade is done. The new active player is already playing.
                // Re-setup listener on the new active player via the delegating player.
                withContext(Dispatchers.Main) { setupPlayerListener() }
                AudioFileCache.unpinAll()
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

                    // Monitor for crossfade trigger
                    if (dp != null) {
                        val currentState = _state.value
                        val currentTrackId = currentState.currentItem?.videoId
                        val duration = currentState.durationMs
                        val hasNext = currentState.queue.currentIndex + 1 < currentState.queue.items.size
                        crossfadeManager.monitor(
                            scope = viewModelScope,
                            trackId = currentTrackId,
                            hasNext = hasNext,
                            positionMs = pos,
                            durationMs = duration,
                            delegatingPlayer = dp
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
        val nextIdx = queue.currentIndex + 1
        if (nextIdx < queue.items.size) {
            val nextId = queue.items[nextIdx].videoId
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    AudioFileCache.getOrDownload(nextId)?.let {
                        Log.d(TAG, "prefetchNext: downloaded '$nextId' (${it.length() / 1024}KB)")
                    }
                }.onFailure { Log.e(TAG, "prefetchNext: FAILED for '$nextId'", it) }
            }
        }
    }

    private fun loadUpNextQueue(seedVideoId: String) {
        upNextJob?.cancel()
        upNextJob = viewModelScope.launch(Dispatchers.IO) {
            val cacheKey = "next:$seedVideoId"
            val cached = AppCache.browse.get(cacheKey) as? PlaybackQueue
            if (cached != null) {
                applyUpNextQueue(seedVideoId, cached)
                return@launch
            }

            val visitorId = VisitorManager.ensureBrowseVisitorId()
            if (visitorId.isBlank()) {
                Log.e(TAG, "loadUpNextQueue: browseVisitorId is blank")
                return@launch
            }

            val json = runCatching {
                RequestExecutor.executeNextRequest(seedVideoId, visitorId)
            }.onFailure { Log.e(TAG, "loadUpNextQueue: executeNextRequest FAILED", it) }
                .getOrDefault("")

            val recoveredJson = if (json.isBlank()) {
                val refreshedId = VisitorManager.refreshBrowseVisitorId()
                if (refreshedId.isBlank() || refreshedId == visitorId) {
                    ""
                } else {
                    runCatching { RequestExecutor.executeNextRequest(seedVideoId, refreshedId) }
                        .onFailure { Log.e(TAG, "loadUpNextQueue: executeNextRequest retry FAILED", it) }
                        .getOrDefault("")
                }
            } else {
                json
            }

            if (recoveredJson.isBlank()) return@launch

            val queue = NextParser.extractUpNextQueue(recoveredJson) ?: run {
                Log.e(TAG, "loadUpNextQueue: NextParser returned null")
                return@launch
            }

            AppCache.browse.put(cacheKey, queue)
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
        upNextJob?.cancel()
        crossfadeManager.release()
        observedPlayer?.let { player ->
            playerListener?.let { player.removeListener(it) }
        }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
        observedPlayer = null
    }
}
