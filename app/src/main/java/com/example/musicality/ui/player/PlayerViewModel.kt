package com.example.musicality.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.example.musicality.di.NetworkModule
import com.example.musicality.domain.model.QueueSong
import com.example.musicality.domain.model.SongPlaybackInfo
import com.example.musicality.domain.repository.PlayerRepository
import com.example.musicality.domain.repository.QueueRepository
import com.example.musicality.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val repository: PlayerRepository = NetworkModule.providePlayerRepository(),
    private val queueRepository: QueueRepository = NetworkModule.provideQueueRepository()
) : ViewModel() {

    private val _playerState = MutableStateFlow<UiState<SongPlaybackInfo>>(UiState.Idle)
    val playerState: StateFlow<UiState<SongPlaybackInfo>> = _playerState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

    // Playback progress tracking
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    // Queue state
    private val _queueState = MutableStateFlow<UiState<List<QueueSong>>>(UiState.Idle)
    val queueState: StateFlow<UiState<List<QueueSong>>> = _queueState.asStateFlow()
    
    private val _isQueueSheetVisible = MutableStateFlow(false)
    val isQueueSheetVisible: StateFlow<Boolean> = _isQueueSheetVisible.asStateFlow()
    
    // Buffering/loading state - true when player is loading the audio stream
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()
    
    // Store current videoId for queue fetching
    private var currentVideoId: String? = null
    
    // Track current position in queue for autoplay
    private var currentQueueIndex: Int = 0

    // Player instance (can be ExoPlayer directly or MediaController which implements Player)
    private var player: Player? = null
    private var progressJob: kotlinx.coroutines.Job? = null

    /**
     * Load and play song - called when USER initiates playback (search result click)
     * This WILL fetch a new queue for the song
     */
    fun playSong(videoId: String, thumbnailUrl: String) {
        currentVideoId = videoId
        currentQueueIndex = 0 // Reset queue index for new user-initiated playback
        viewModelScope.launch {
            _playerState.value = UiState.Loading
            _isExpanded.value = true
            // Reset queue when playing new song from search
            _queueState.value = UiState.Idle

            android.util.Log.d("PlayerViewModel", "Original thumbnail URL: $thumbnailUrl")

            repository.getSongInfo(videoId).fold(
                onSuccess = { songInfo ->
                    // Upscale the thumbnail URL from search results (w120-h120 to w544-h544)
                    val upscaledThumbnail = com.example.musicality.util.ImageUtils.upscaleThumbnail(thumbnailUrl)
                    
                    android.util.Log.d("PlayerViewModel", "Upscaled thumbnail URL: $upscaledThumbnail")
                    android.util.Log.d("PlayerViewModel", "API thumbnail URL: ${songInfo.thumbnailUrl}")
                    
                    // Replace the thumbnail URL with the upscaled version
                    val updatedSongInfo = songInfo.copy(thumbnailUrl = upscaledThumbnail)
                    
                    android.util.Log.d("PlayerViewModel", "Final thumbnail URL used: ${updatedSongInfo.thumbnailUrl}")
                    
                    _playerState.value = UiState.Success(updatedSongInfo)
                    preparePlayer(
                        url = songInfo.mainUrl,
                        title = updatedSongInfo.title,
                        artist = updatedSongInfo.author,
                        artworkUrl = updatedSongInfo.thumbnailUrl
                    )
                    
                    // Fetch queue ONLY for user-initiated playback
                    fetchQueue(videoId)
                },
                onFailure = { exception ->
                    _playerState.value = UiState.Error(
                        exception.message ?: "Failed to load song"
                    )
                }
            )
        }
    }
    
    /**
     * Play album - sets album songs as queue without fetching related songs
     * Supports shuffle mode to randomize playback order
     * 
     * @param albumSongs List of songs in the album (as QueueSong for compatibility)
     * @param albumThumbnail Album artwork URL 
     * @param shuffle Whether to shuffle the songs
     * @param startIndex Which song to start playing (0 = first, -1 = random if shuffle)
     */
    fun playAlbum(
        albumSongs: List<QueueSong>,
        albumThumbnail: String,
        shuffle: Boolean = false
    ) {
        if (albumSongs.isEmpty()) return
        
        // Create queue from album songs, optionally shuffled
        val queue = if (shuffle) albumSongs.shuffled() else albumSongs
        
        // Set the queue directly - NO API fetch
        _queueState.value = UiState.Success(queue)
        currentQueueIndex = 0
        
        android.util.Log.d("PlayerViewModel", "Playing album with ${queue.size} songs, shuffle=$shuffle")
        
        // Play the first song in the queue
        val firstSong = queue.first()
        currentVideoId = firstSong.videoId
        
        viewModelScope.launch {
            _playerState.value = UiState.Loading
            _isExpanded.value = true

            repository.getSongInfo(firstSong.videoId).fold(
                onSuccess = { songInfo ->
                    // Use album thumbnail for consistency
                    val thumbnailUrl = albumThumbnail.ifEmpty { firstSong.thumbnailUrl.ifEmpty { songInfo.thumbnailUrl } }
                    val updatedSongInfo = songInfo.copy(thumbnailUrl = thumbnailUrl)
                    
                    _playerState.value = UiState.Success(updatedSongInfo)
                    preparePlayer(
                        url = songInfo.mainUrl,
                        title = updatedSongInfo.title,
                        artist = updatedSongInfo.author,
                        artworkUrl = thumbnailUrl
                    )
                    // NO queue fetch - we already set the album songs as queue
                },
                onFailure = { exception ->
                    _playerState.value = UiState.Error(
                        exception.message ?: "Failed to load song"
                    )
                }
            )
        }
    }
    
    /**
     * Internal method to play a song from the queue WITHOUT fetching a new queue
     * Used for autoplay and manual queue item selection
     * 
     * IMPORTANT: Does NOT set Loading state to prevent UI re-render
     * The current song stays visible while the new one loads
     */
    private fun playSongFromQueueInternal(queueSong: QueueSong, queueIndex: Int) {
        currentVideoId = queueSong.videoId
        currentQueueIndex = queueIndex
        
        android.util.Log.d("PlayerViewModel", "Playing from queue: ${queueSong.name} (index: $queueIndex)")
        
        viewModelScope.launch {
            // DO NOT set Loading state here - keeps current UI stable
            // DO NOT reset queue here - we're playing from existing queue

            repository.getSongInfo(queueSong.videoId).fold(
                onSuccess = { songInfo ->
                    // Use thumbnail from queue song (already has good resolution)
                    val thumbnailUrl = queueSong.thumbnailUrl.ifEmpty { songInfo.thumbnailUrl }
                    val updatedSongInfo = songInfo.copy(thumbnailUrl = thumbnailUrl)
                    
                    // Directly update to Success - smooth transition
                    _playerState.value = UiState.Success(updatedSongInfo)
                    preparePlayer(
                        url = songInfo.mainUrl,
                        title = updatedSongInfo.title,
                        artist = updatedSongInfo.author,
                        artworkUrl = thumbnailUrl
                    )
                    // NO queue fetch here - queue stays the same
                },
                onFailure = { exception ->
                    _playerState.value = UiState.Error(
                        exception.message ?: "Failed to load song"
                    )
                }
            )
        }
    }
    
    /**
     * Auto-play the next song in queue when current song ends
     * Queue is circular - wraps to first song when reaching the end
     */
    private fun playNextInQueue() {
        val queue = (_queueState.value as? UiState.Success)?.data ?: return
        if (queue.isEmpty()) return
        
        // Move to next index, wrap around for circular queue
        val nextIndex = (currentQueueIndex + 1) % queue.size
        val nextSong = queue[nextIndex]
        
        android.util.Log.d("PlayerViewModel", "Autoplay: Moving to index $nextIndex (${nextSong.name})")
        
        playSongFromQueueInternal(nextSong, nextIndex)
    }
    
    /**
     * Play next song in queue (user presses next button)
     * Circular - wraps to first song when at the end
     */
    fun playNext() {
        val queue = (_queueState.value as? UiState.Success)?.data ?: return
        if (queue.isEmpty()) return
        
        val nextIndex = (currentQueueIndex + 1) % queue.size
        val nextSong = queue[nextIndex]
        
        android.util.Log.d("PlayerViewModel", "Manual next: Moving to index $nextIndex (${nextSong.name})")
        
        playSongFromQueueInternal(nextSong, nextIndex)
    }
    
    /**
     * Play previous song in queue (user presses previous button)
     * Circular - wraps to last song when at the beginning
     */
    fun playPrevious() {
        val queue = (_queueState.value as? UiState.Success)?.data ?: return
        if (queue.isEmpty()) return
        
        // Move to previous index, wrap around for circular queue
        val previousIndex = if (currentQueueIndex - 1 < 0) queue.size - 1 else currentQueueIndex - 1
        val previousSong = queue[previousIndex]
        
        android.util.Log.d("PlayerViewModel", "Manual previous: Moving to index $previousIndex (${previousSong.name})")
        
        playSongFromQueueInternal(previousSong, previousIndex)
    }
    
    /**
     * Fetch related songs queue for the currently playing song
     */
    private fun fetchQueue(videoId: String) {
        viewModelScope.launch {
            _queueState.value = UiState.Loading
            
            queueRepository.getQueue(videoId).fold(
                onSuccess = { queue ->
                    android.util.Log.d("PlayerViewModel", "Queue loaded with ${queue.size} songs")
                    _queueState.value = UiState.Success(queue)
                },
                onFailure = { exception ->
                    android.util.Log.e("PlayerViewModel", "Failed to load queue", exception)
                    _queueState.value = UiState.Error(
                        exception.message ?: "Failed to load queue"
                    )
                }
            )
        }
    }
    
    /**
     * Play a song from the queue (user taps on queue item)
     * Does NOT refetch the queue - uses existing queue
     */
    fun playFromQueue(queueSong: QueueSong) {
        val queue = (_queueState.value as? UiState.Success)?.data ?: return
        val index = queue.indexOfFirst { it.videoId == queueSong.videoId }
        if (index != -1) {
            _isQueueSheetVisible.value = false
            playSongFromQueueInternal(queueSong, index)
        }
    }
    
    /**
     * Toggle queue sheet visibility
     */
    fun toggleQueueSheet() {
        _isQueueSheetVisible.value = !_isQueueSheetVisible.value
    }
    
    /**
     * Set queue sheet visibility
     */
    fun setQueueSheetVisible(visible: Boolean) {
        _isQueueSheetVisible.value = visible
    }

    /**
     * Initialize player (can be ExoPlayer or MediaController)
     */
    fun initializePlayer(playerInstance: Player) {
        player = playerInstance
        playerInstance.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startProgressUpdates()
                } else {
                    progressJob?.cancel()
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _isBuffering.value = true
                    }
                    Player.STATE_READY -> {
                        _isBuffering.value = false
                        _duration.value = playerInstance.duration
                    }
                    Player.STATE_ENDED -> {
                        _isBuffering.value = false
                        // Song finished - auto-play next in queue
                        android.util.Log.d("PlayerViewModel", "Song ended, auto-playing next")
                        playNextInQueue()
                    }
                    Player.STATE_IDLE -> {
                        _isBuffering.value = false
                    }
                }
            }
        })
    }
    
    /**
     * Start updating progress periodically
     */
    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                player?.let { p ->
                    _currentPosition.value = p.currentPosition
                    if (p.duration > 0) {
                        _duration.value = p.duration
                    }
                }
                kotlinx.coroutines.delay(500) // Update every 500ms
            }
        }
    }
    
    /**
     * Seek to position
     */
    fun seekTo(position: Long) {
        player?.seekTo(position)
        _currentPosition.value = position
    }
    
    /**
     * Seek to fraction of duration (0.0 to 1.0)
     */
    fun seekToFraction(fraction: Float) {
        val targetPosition = (fraction * _duration.value).toLong()
        seekTo(targetPosition)
    }

    /**
     * Prepare player with audio URL and metadata for notification
     */
    private fun preparePlayer(
        url: String,
        title: String,
        artist: String,
        artworkUrl: String
    ) {
        player?.let { p ->
            // Build MediaMetadata for notification display
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(android.net.Uri.parse(artworkUrl))
                .build()
            
            // Build MediaItem with metadata
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(metadata)
                .build()
            
            p.setMediaItem(mediaItem)
            p.prepare()
            p.play()
        }
    }

    /**
     * Toggle play/pause
     */
    fun togglePlayPause() {
        player?.let { p ->
            if (p.isPlaying) {
                p.pause()
            } else {
                p.play()
            }
        }
    }

    /**
     * Toggle expanded/collapsed state
     */
    fun toggleExpanded() {
        _isExpanded.value = !_isExpanded.value
    }

    /**
     * Set expanded state
     */
    fun setExpanded(expanded: Boolean) {
        _isExpanded.value = expanded
    }

    /**
     * Close player
     */
    fun closePlayer() {
        player?.stop()
        _playerState.value = UiState.Idle
        _queueState.value = UiState.Idle
        _isPlaying.value = false
        _isExpanded.value = false
        _isQueueSheetVisible.value = false
        currentVideoId = null
        currentQueueIndex = 0
    }

    override fun onCleared() {
        super.onCleared()
        // Note: MediaController is released in MusicalityApp, not here
        player = null
        _isExpanded.value = false
    }
}
