package com.example.musicality.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.example.musicality.di.NetworkModule
import com.example.musicality.domain.model.PlaybackContext
import com.example.musicality.domain.model.PlaybackSource
import com.example.musicality.domain.model.QueueSong
import com.example.musicality.domain.model.SongPlaybackInfo
import com.example.musicality.domain.repository.DownloadRepository
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
    
    // Download repository - initialized lazily with context
    private var downloadRepository: DownloadRepository? = null

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
    
    // Download state - true when current song is downloaded
    private val _isDownloaded = MutableStateFlow(false)
    val isDownloaded: StateFlow<Boolean> = _isDownloaded.asStateFlow()
    
    // Download in progress state
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()
    
    // Store current videoId for queue fetching
    private var currentVideoId: String? = null
    
    // Track current position in queue for autoplay
    private var currentQueueIndex: Int = 0
    
    // Current playback context - tracks where the song is being played from
    private var currentPlaybackContext: PlaybackContext = PlaybackContext()

    // Player instance (can be ExoPlayer directly or MediaController which implements Player)
    private var player: Player? = null
    private var progressJob: kotlinx.coroutines.Job? = null

    /**
     * Load and play song from SEARCH - called when USER initiates playback from search result
     * This WILL fetch a new queue for the song
     * Shows "NOW PLAYING" in the player header
     */
    fun playSong(videoId: String, thumbnailUrl: String) {
        currentVideoId = videoId
        currentQueueIndex = 0 // Reset queue index for new user-initiated playback
        currentPlaybackContext = PlaybackContext(
            source = PlaybackSource.SEARCH,
            sourceName = ""
        )
        
        viewModelScope.launch {
            _playerState.value = UiState.Loading
            _isExpanded.value = true
            // Reset queue when playing new song from search
            _queueState.value = UiState.Idle

            android.util.Log.d("PlayerViewModel", "Playing from SEARCH: $videoId")

            repository.getSongInfo(videoId).fold(
                onSuccess = { songInfo ->
                    // Upscale the thumbnail URL from search results (w120-h120 to w544-h544)
                    val upscaledThumbnail = com.example.musicality.util.ImageUtils.upscaleThumbnail(thumbnailUrl)
                    
                    // Replace the thumbnail URL with the upscaled version and add context
                    val updatedSongInfo = songInfo.copy(
                        thumbnailUrl = upscaledThumbnail,
                        playbackContext = currentPlaybackContext
                    )
                    
                    _playerState.value = UiState.Success(updatedSongInfo)
                    preparePlayer(
                        url = songInfo.mainUrl,
                        title = updatedSongInfo.title,
                        artist = updatedSongInfo.author,
                        artworkUrl = updatedSongInfo.thumbnailUrl,
                        mimeType = songInfo.mimeType
                    )
                    
                    // Check download status
                    checkDownloadStatus(videoId)
                    
                    // Fetch queue ONLY for user-initiated playback from search
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
     * Shows "PLAYING FROM" + album name in the player header
     * 
     * @param albumSongs List of songs in the album (as QueueSong for compatibility)
     * @param albumName Name of the album for display in "Playing From"
     * @param albumThumbnail Album artwork URL 
     * @param shuffle Whether to shuffle the songs
     * @param startIndex Which song to start playing (default 0 = first)
     */
    fun playAlbum(
        albumSongs: List<QueueSong>,
        albumName: String,
        albumThumbnail: String,
        shuffle: Boolean = false,
        startIndex: Int = 0
    ) {
        if (albumSongs.isEmpty()) return
        
        // Set playback context for album
        currentPlaybackContext = PlaybackContext(
            source = PlaybackSource.ALBUM,
            sourceName = albumName
        )
        
        // Create queue from album songs, optionally shuffled
        val queue = if (shuffle) albumSongs.shuffled() else albumSongs
        
        // Set the queue directly - NO API fetch
        _queueState.value = UiState.Success(queue)
        currentQueueIndex = startIndex.coerceIn(0, queue.size - 1)
        
        android.util.Log.d("PlayerViewModel", "Playing ALBUM: $albumName with ${queue.size} songs, shuffle=$shuffle, startIndex=$startIndex")
        
        // Play the selected song in the queue
        val songToPlay = queue[currentQueueIndex]
        currentVideoId = songToPlay.videoId
        
        viewModelScope.launch {
            _playerState.value = UiState.Loading
            _isExpanded.value = true

            repository.getSongInfo(songToPlay.videoId).fold(
                onSuccess = { songInfo ->
                    // Use album thumbnail for consistency
                    val thumbnailUrl = albumThumbnail.ifEmpty { songToPlay.thumbnailUrl.ifEmpty { songInfo.thumbnailUrl } }
                    val updatedSongInfo = songInfo.copy(
                        thumbnailUrl = thumbnailUrl,
                        playbackContext = currentPlaybackContext
                    )
                    
                    _playerState.value = UiState.Success(updatedSongInfo)
                    preparePlayer(
                        url = songInfo.mainUrl,
                        title = updatedSongInfo.title,
                        artist = updatedSongInfo.author,
                        artworkUrl = thumbnailUrl,
                        mimeType = songInfo.mimeType
                    )
                    
                    // Check download status
                    checkDownloadStatus(songToPlay.videoId)
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
     * Play a specific song from an album - uses album as queue
     * Shows "PLAYING FROM" + album name in the player header
     * 
     * @param videoId The video ID of the song to play
     * @param albumSongs All songs in the album (will become the queue)
     * @param albumName Name of the album for display
     * @param albumThumbnail Album artwork URL
     */
    fun playSongFromAlbum(
        videoId: String,
        albumSongs: List<QueueSong>,
        albumName: String,
        albumThumbnail: String
    ) {
        if (albumSongs.isEmpty()) return
        
        // Find the index of the clicked song in the album
        val clickedIndex = albumSongs.indexOfFirst { it.videoId == videoId }
        if (clickedIndex == -1) {
            // Song not found in album, play from search instead
            android.util.Log.w("PlayerViewModel", "Song $videoId not found in album, falling back to search")
            playSong(videoId, albumThumbnail)
            return
        }
        
        // Play album starting from the clicked song
        playAlbum(
            albumSongs = albumSongs,
            albumName = albumName,
            albumThumbnail = albumThumbnail,
            shuffle = false,
            startIndex = clickedIndex
        )
    }
    
    /**
     * Play playlist - sets playlist songs as queue without fetching related songs
     * Shows "PLAYING FROM" + playlist name in the player header
     * 
     * @param playlistSongs List of songs in the playlist (as QueueSong for compatibility)
     * @param playlistName Name of the playlist for display in "Playing From"
     * @param playlistThumbnail Playlist artwork URL 
     * @param shuffle Whether to shuffle the songs
     * @param startIndex Which song to start playing (default 0 = first)
     */
    fun playPlaylist(
        playlistSongs: List<QueueSong>,
        playlistName: String,
        playlistThumbnail: String,
        shuffle: Boolean = false,
        startIndex: Int = 0
    ) {
        if (playlistSongs.isEmpty()) return
        
        // Set playback context for playlist
        currentPlaybackContext = PlaybackContext(
            source = PlaybackSource.PLAYLIST,
            sourceName = playlistName
        )
        
        // Create queue from playlist songs, optionally shuffled
        val queue = if (shuffle) playlistSongs.shuffled() else playlistSongs
        
        // Set the queue directly - NO API fetch
        _queueState.value = UiState.Success(queue)
        currentQueueIndex = startIndex.coerceIn(0, queue.size - 1)
        
        android.util.Log.d("PlayerViewModel", "Playing PLAYLIST: $playlistName with ${queue.size} songs, shuffle=$shuffle, startIndex=$startIndex")
        
        // Play the selected song in the queue
        val songToPlay = queue[currentQueueIndex]
        currentVideoId = songToPlay.videoId
        
        viewModelScope.launch {
            _playerState.value = UiState.Loading
            _isExpanded.value = true

            repository.getSongInfo(songToPlay.videoId).fold(
                onSuccess = { songInfo ->
                    // Use playlist thumbnail or song thumbnail
                    val thumbnailUrl = songToPlay.thumbnailUrl.ifEmpty { playlistThumbnail.ifEmpty { songInfo.thumbnailUrl } }
                    val updatedSongInfo = songInfo.copy(
                        thumbnailUrl = thumbnailUrl,
                        playbackContext = currentPlaybackContext
                    )
                    
                    _playerState.value = UiState.Success(updatedSongInfo)
                    preparePlayer(
                        url = songInfo.mainUrl,
                        title = updatedSongInfo.title,
                        artist = updatedSongInfo.author,
                        artworkUrl = thumbnailUrl,
                        mimeType = songInfo.mimeType
                    )
                    
                    // Check download status
                    checkDownloadStatus(songToPlay.videoId)
                    // NO queue fetch - we already set the playlist songs as queue
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
     * Play a specific song from a playlist - uses playlist as queue
     * Shows "PLAYING FROM" + playlist name in the player header
     * 
     * @param videoId The video ID of the song to play
     * @param playlistSongs All songs in the playlist (will become the queue)
     * @param playlistName Name of the playlist for display
     * @param playlistThumbnail Playlist artwork URL
     */
    fun playSongFromPlaylist(
        videoId: String,
        playlistSongs: List<QueueSong>,
        playlistName: String,
        playlistThumbnail: String
    ) {
        if (playlistSongs.isEmpty()) return
        
        // Find the index of the clicked song in the playlist
        val clickedIndex = playlistSongs.indexOfFirst { it.videoId == videoId }
        if (clickedIndex == -1) {
            // Song not found in playlist, play from search instead
            android.util.Log.w("PlayerViewModel", "Song $videoId not found in playlist, falling back to search")
            playSong(videoId, playlistThumbnail)
            return
        }
        
        // Play playlist starting from the clicked song
        playPlaylist(
            playlistSongs = playlistSongs,
            playlistName = playlistName,
            playlistThumbnail = playlistThumbnail,
            shuffle = false,
            startIndex = clickedIndex
        )
    }
    
    /**
     * Internal method to play a song from the queue WITHOUT fetching a new queue
     * Used for autoplay and manual queue item selection
     * Preserves the current playback context
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
                    // Preserve playback context when playing from queue
                    val updatedSongInfo = songInfo.copy(
                        thumbnailUrl = thumbnailUrl,
                        playbackContext = currentPlaybackContext
                    )
                    
                    // Directly update to Success - smooth transition
                    _playerState.value = UiState.Success(updatedSongInfo)
                    preparePlayer(
                        url = songInfo.mainUrl,
                        title = updatedSongInfo.title,
                        artist = updatedSongInfo.author,
                        artworkUrl = thumbnailUrl,
                        mimeType = songInfo.mimeType
                    )
                    
                    // Check download status
                    checkDownloadStatus(queueSong.videoId)
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
     * Only used for songs played from search
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
                        // Only show buffering if we're not ready yet
                        // This prevents the loader from staying visible during seeks
                        if (playerInstance.playbackState != Player.STATE_READY) {
                            _isBuffering.value = true
                        }
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
     * Optimistically update the position to prevent UI snap-back
     */
    fun seekTo(position: Long) {
        player?.let { p ->
            p.seekTo(position)
            // Optimistically update position immediately to prevent snap-back
            _currentPosition.value = position
        }
    }
    
    /**
     * Seek to fraction of duration (0.0 to 1.0)
     */
    fun seekToFraction(fraction: Float) {
        val duration = _duration.value
        if (duration <= 0) return
        
        val targetPosition = (fraction * duration).toLong()
        seekTo(targetPosition)
    }

    /**
     * Initialize download repository with context
     * Must be called before download functionality can be used
     */
    fun initializeDownloadRepository(context: Context) {
        if (downloadRepository == null) {
            downloadRepository = NetworkModule.provideDownloadRepository(context)
        }
    }
    
    /**
     * Check if current song is downloaded
     */
    private fun checkDownloadStatus(videoId: String) {
        viewModelScope.launch {
            val isDownloaded = downloadRepository?.isDownloaded(videoId) ?: false
            _isDownloaded.value = isDownloaded
        }
    }
    
    /**
     * Download current song
     */
    fun downloadCurrentSong() {
        val songInfo = (_playerState.value as? UiState.Success)?.data ?: return
        val repo = downloadRepository ?: run {
            android.util.Log.e("PlayerViewModel", "DownloadRepository not initialized")
            return
        }
        
        viewModelScope.launch {
            _isDownloading.value = true
            android.util.Log.d("PlayerViewModel", "Starting download for: ${songInfo.title}")
            
            repo.downloadSong(
                videoId = songInfo.videoId,
                url = songInfo.mainUrl,
                title = songInfo.title,
                author = songInfo.author,
                thumbnailUrl = songInfo.thumbnailUrl,
                duration = songInfo.lengthSeconds,
                channelId = songInfo.channelId,
                mimeType = songInfo.mimeType
            ).fold(
                onSuccess = { downloadedSong ->
                    android.util.Log.d("PlayerViewModel", "Download complete: ${downloadedSong.filePath}")
                    _isDownloaded.value = true
                    _isDownloading.value = false
                },
                onFailure = { exception ->
                    android.util.Log.e("PlayerViewModel", "Download failed", exception)
                    _isDownloading.value = false
                }
            )
        }
    }
    
    /**
     * Delete downloaded song
     */
    fun deleteDownloadedSong() {
        val songInfo = (_playerState.value as? UiState.Success)?.data ?: return
        val repo = downloadRepository ?: return
        
        viewModelScope.launch {
            repo.deleteDownloadedSong(songInfo.videoId).fold(
                onSuccess = {
                    android.util.Log.d("PlayerViewModel", "Deleted download: ${songInfo.title}")
                    _isDownloaded.value = false
                },
                onFailure = { exception ->
                    android.util.Log.e("PlayerViewModel", "Delete failed", exception)
                }
            )
        }
    }
    
    /**
     * Prepare player with audio URL and metadata for notification
     * Let ExoPlayer auto-detect the stream format for better seeking support
     */
    private fun preparePlayer(
        url: String,
        title: String,
        artist: String,
        artworkUrl: String,
        mimeType: String = "audio/webm"
    ) {
        player?.let { p ->
            // Build MediaMetadata for notification display
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(android.net.Uri.parse(artworkUrl))
                .build()
            
            // Build MediaItem WITHOUT explicit MIME type
            // Let ExoPlayer's DefaultExtractorsFactory auto-detect the container format
            // This prevents seeking issues with YouTube Music streams that lack proper index seek points
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(metadata)
                .build()
            
            android.util.Log.d("PlayerViewModel", "Preparing player - url prefix: ${url.take(80)}...")
            
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
        currentPlaybackContext = PlaybackContext()
    }

    override fun onCleared() {
        super.onCleared()
        // Note: MediaController is released in MusicalityApp, not here
        player = null
        _isExpanded.value = false
    }
}
