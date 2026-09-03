package com.sambhavdwivedi.videoplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.request.ImageRequest
import com.sambhavdwivedi.videoplayer.data.VideoRepository
import com.sambhavdwivedi.videoplayer.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

enum class SortMode { DATE_ADDED, NAME, DURATION, SIZE }

class VideoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository(application)

    val player: ExoPlayer = ExoPlayer.Builder(application).build()

    private val _allVideos = MutableStateFlow<List<Video>>(emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.DATE_ADDED)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    val videos: StateFlow<List<Video>> = combine(_allVideos, _searchQuery, _sortMode) { all, query, sort ->
        val filtered = if (query.isBlank()) all else all.filter { it.title.contains(query, ignoreCase = true) }
        when (sort) {
            SortMode.DATE_ADDED -> filtered.sortedByDescending { it.dateAdded }
            SortMode.NAME -> filtered.sortedBy { it.title.lowercase() }
            SortMode.DURATION -> filtered.sortedByDescending { it.durationMs }
            SortMode.SIZE -> filtered.sortedByDescending { it.sizeBytes }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo: StateFlow<Video?> = _currentVideo.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    // Seeded from MediaStore metadata the instant a video starts, and only ever
    // overwritten by a POSITIVE value from the player — never reset to 0/unknown.
    // This is the actual fix: a stale 0 here was making every seek land on 0.
    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _hasNext = MutableStateFlow(false)
    val hasNext: StateFlow<Boolean> = _hasNext.asStateFlow()

    private val _hasPrevious = MutableStateFlow(false)
    val hasPrevious: StateFlow<Boolean> = _hasPrevious.asStateFlow()

    private val lastPositions = mutableMapOf<Long, Long>()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _isBuffering.value = playbackState == Player.STATE_BUFFERING
        }
    }

    init {
        player.addListener(playerListener)
        viewModelScope.launch {
            while (true) {
                _positionMs.value = player.currentPosition.coerceAtLeast(0)

                val playerDuration = player.duration
                if (playerDuration > 0) {
                    _durationMs.value = playerDuration
                }

                delay(200)
            }
        }
    }

    fun loadVideos() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = withContext(Dispatchers.IO) { repository.getAllVideos() }

            val imageLoader = ThumbnailImageLoader.get(getApplication())
            val preloadCount = minOf(result.size, 24)
            withContext(Dispatchers.IO) {
                prefetchThumbnails(result.take(preloadCount), imageLoader)
            }

            _allVideos.value = result
            _isLoading.value = false
        }
    }

    private suspend fun prefetchThumbnails(videos: List<Video>, imageLoader: coil.ImageLoader) {
        val context = getApplication<Application>()
        val semaphore = Semaphore(4)
        coroutineScope {
            videos.forEach { video ->
                launch {
                    semaphore.withPermit {
                        val request = ImageRequest.Builder(context)
                            .data(video.thumbnailUri)
                            .build()
                        imageLoader.execute(request)
                    }
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun enterSelectionMode(initialId: Long) {
        _isSelectionMode.value = true
        _selectedIds.value = setOf(initialId)
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value
        val updated = if (id in current) current - id else current + id
        _selectedIds.value = updated
        if (updated.isEmpty()) _isSelectionMode.value = false
    }

    fun selectAll() {
        _selectedIds.value = videos.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _isSelectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun removeDeletedVideos(ids: Set<Long>) {
        _allVideos.value = _allVideos.value.filterNot { it.id in ids }
        clearSelection()
    }

    fun playVideo(video: Video) {
        _currentVideo.value = video
        updateNavAvailability()

        // Seed duration from MediaStore metadata immediately — don't wait for
        // the player to become STATE_READY before the slider/seek math works.
        _durationMs.value = video.durationMs

        val resumeFrom = (lastPositions[video.id] ?: 0L).coerceIn(0L, (video.durationMs - 500).coerceAtLeast(0L))
        val mediaItem = MediaItem.fromUri(video.uri)
        player.setMediaItem(mediaItem, resumeFrom)
        player.prepare()
        player.play()
        _positionMs.value = resumeFrom
    }

    fun playNext() {
        val list = videos.value
        val idx = list.indexOfFirst { it.id == _currentVideo.value?.id }
        if (idx in 0 until list.size - 1) playVideo(list[idx + 1])
    }

    fun playPrevious() {
        val list = videos.value
        val idx = list.indexOfFirst { it.id == _currentVideo.value?.id }
        if (idx > 0) playVideo(list[idx - 1])
    }

    private fun updateNavAvailability() {
        val list = videos.value
        val idx = list.indexOfFirst { it.id == _currentVideo.value?.id }
        _hasNext.value = idx in 0 until list.size - 1
        _hasPrevious.value = idx > 0
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(ms: Long) {
        val safeDuration = _durationMs.value
        val target = if (safeDuration > 0) ms.coerceIn(0L, safeDuration - 200) else ms.coerceAtLeast(0)
        player.seekTo(target)
        _positionMs.value = target
    }

    fun seekBy(deltaMs: Long) {
        val current = player.currentPosition.coerceAtLeast(0)
        seekTo(current + deltaMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    fun pausePlayback() {
        _currentVideo.value?.let { lastPositions[it.id] = player.currentPosition }
        player.pause()
    }

    fun stopPlayback() {
        _currentVideo.value?.let { lastPositions[it.id] = player.currentPosition }
        player.stop()
        player.clearMediaItems()
        _currentVideo.value = null
    }

    override fun onCleared() {
        player.removeListener(playerListener)
        player.release()
        super.onCleared()
    }
}
