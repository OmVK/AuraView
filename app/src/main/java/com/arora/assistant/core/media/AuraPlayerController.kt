package com.arora.assistant.core.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class AuraPlayerController(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    val exoPlayer: ExoPlayer by lazy {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(playerListener)
            }
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed = _playbackSpeed.asStateFlow()

    private val _currentMedia = MutableStateFlow<ExtractedMediaInfo?>(null)
    val currentMedia = _currentMedia.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startProgressTracker()
            } else {
                progressJob?.cancel()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _isLoading.value = true
                }
                Player.STATE_READY -> {
                    _isLoading.value = false
                    _durationMs.value = exoPlayer.duration.coerceAtLeast(0L)
                }
                Player.STATE_ENDED -> {
                    _isPlaying.value = false
                    _isLoading.value = false
                    progressJob?.cancel()
                }
                Player.STATE_IDLE -> {
                    _isLoading.value = false
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _isLoading.value = false
            _isPlaying.value = false
            _errorMessage.value = "Playback error: ${error.localizedMessage}"
        }
    }

    fun playMedia(mediaInfo: ExtractedMediaInfo) {
        _currentMedia.value = mediaInfo
        _errorMessage.value = null
        _isLoading.value = true

        val streamUrl = mediaInfo.primaryStreamUrl
        val mediaItem = if (streamUrl.contains(".m3u8") || mediaInfo.hlsUrl != null) {
            MediaItem.Builder()
                .setUri(mediaInfo.hlsUrl ?: streamUrl)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
        } else {
            MediaItem.fromUri(streamUrl)
        }

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L)))
        _currentPositionMs.value = positionMs
    }

    fun seekRelative(offsetMs: Long) {
        val newPos = (exoPlayer.currentPosition + offsetMs).coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
        exoPlayer.seekTo(newPos)
        _currentPositionMs.value = newPos
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                _currentPositionMs.value = exoPlayer.currentPosition
                _durationMs.value = exoPlayer.duration.coerceAtLeast(0L)
                delay(350)
            }
        }
    }

    fun release() {
        progressJob?.cancel()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }
}
