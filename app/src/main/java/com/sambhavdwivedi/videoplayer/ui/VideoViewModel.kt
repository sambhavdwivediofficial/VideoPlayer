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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class VideoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository(application)

    val player: ExoPlayer = ExoPlayer.Builder(application).build()

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo: StateFlow<Video?> = _currentVideo.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _hasNext = MutableStateFlow(false)
    val hasNext: StateFlow<Boolean> = _hasNext.asStateFlow()

    private val _hasPrevious = MutableStateFlow(false)
    val hasPrevious: StateFlow<Boolean> = _hasPrevious.asStateFlow()

    private val lastPositions = mutableMapOf<Long, Long>()

    // Guards the position shown to the UI right after a manual seek, so the
    // polling loop below can never briefly report a stale/older position
    // and make the progress bar appear to jump backwards.
    private var pendingSeekTarget: Long = -1L
    private var pendingSeekUntil: Long = 0L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }
    }

    init {
        player.addListener(playerListener)
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val actual = player.currentPosition.coerceAtLeast(0)

                if (pendingSeekTarget >= 0L) {
                    if (actual >= pendingSeekTarget - 300 || now > pendingSeekUntil) {
                        // Playback has genuinely caught up to (or past) the seek
                        // target, or the guard window expired — trust the player again.
                        pendingSeekTarget = -1L
                        _positionMs.value = actual
                    } else {
                        // Keep showing the seek target until playback catches up.
                        _positionMs.value = pendingSeekTarget
                    }
                } else {
                    _positionMs.value = actual
                }

                _durationMs.value = player.duration.coerceAtLeast(0)
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

            _videos.value = result
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

    fun playVideo(video: Video) {
        pendingSeekTarget = -1L
        _currentVideo.value = video
        updateNavAvailability()
        val resumeFrom = lastPositions[video.id] ?: 0L
        val mediaItem = MediaItem.fromUri(video.uri)
        player.setMediaItem(mediaItem, resumeFrom)
        player.prepare()
        player.play()
        _positionMs.value = resumeFrom
    }

    fun playNext() {
        val list = _videos.value
        val idx = list.indexOfFirst { it.id == _currentVideo.value?.id }
        if (idx in 0 until list.size - 1) playVideo(list[idx + 1])
    }

    fun playPrevious() {
        val list = _videos.value
        val idx = list.indexOfFirst { it.id == _currentVideo.value?.id }
        if (idx > 0) playVideo(list[idx - 1])
    }

    private fun updateNavAvailability() {
        val list = _videos.value
        val idx = list.indexOfFirst { it.id == _currentVideo.value?.id }
        _hasNext.value = idx in 0 until list.size - 1
        _hasPrevious.value = idx > 0
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(ms: Long) {
        val target = ms.coerceAtLeast(0)
        pendingSeekTarget = target
        pendingSeekUntil = System.currentTimeMillis() + 1200
        player.seekTo(target)
        _positionMs.value = target
    }

    fun seekBy(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0)
        pendingSeekTarget = target
        pendingSeekUntil = System.currentTimeMillis() + 1200
        player.seekTo(target)
        _positionMs.value = target
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
        pendingSeekTarget = -1L
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
