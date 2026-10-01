package com.mobplayer.tv.repository

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.mobplayer.tv.models.PlayerStatePayload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    var player: ExoPlayer? = null
        private set
        
    private var mediaSession: MediaSession? = null

    private val _playerStateFlow = MutableStateFlow<PlayerStatePayload?>(null)
    val playerStateFlow: StateFlow<PlayerStatePayload?> = _playerStateFlow

    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var activePlayingVideoId: String? = null
    var onPlaybackProgressUpdate: ((videoId: String, positionMs: Long, durationMs: Long) -> Unit)? = null
    var onPlaybackError: ((error: androidx.media3.common.PlaybackException) -> Unit)? = null

    fun initialize(): ExoPlayer {
        player?.let { return it }

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1000,
                /* maxBufferMs = */ 30000,
                /* bufferForPlaybackMs = */ 250,
                /* bufferForPlaybackAfterRebufferMs = */ 500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val exoPlayer = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
        player = exoPlayer
        
        mediaSession = MediaSession.Builder(context, exoPlayer).build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                android.util.Log.d("MediaRepository", "▶ onIsPlayingChanged: isPlaying=$isPlaying")
                updateState()
                if (isPlaying) {
                    startProgressPolling()
                } else {
                    stopProgressPolling()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                android.util.Log.d("MediaRepository", "🔄 onPlaybackStateChanged: state=$stateName, playWhenReady=${player?.playWhenReady}")
                updateState()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                updateState()
            }
            
            override fun onVolumeChanged(volume: Float) {
                updateState()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("MediaRepository", "❌ Playback error: ${error.message}", error)
                updateState()
                onPlaybackError?.invoke(error)
            }
        })

        return exoPlayer
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                delay(1000)
                updateState()
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressPolling()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
    }

    fun play() {
        if (player?.currentMediaItem == null) {
            // Load a demo video if nothing is currently playing
            val demoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            loadMedia(demoUrl)
        } else {
            player?.play()
        }
    }

    fun pause() {
        player?.pause()
        updateState()
    }

    fun stop() {
        updateState()
        activePlayingVideoId = null
        player?.stop()
        player?.clearMediaItems()
        updateState()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        updateState()
    }

    fun setVolume(volume: Float) {
        player?.volume = volume.coerceIn(0f, 1f)
        updateState()
    }

    fun loadMedia(
        url: String,
        startPositionMs: Long = 0L,
        videoId: String? = null,
        mimeType: String? = null
    ) {
        activePlayingVideoId = videoId
        val builder = MediaItem.Builder().setUri(url)
        val resolvedMime = if (!mimeType.isNullOrBlank()) {
            mimeType
        } else {
            com.mobplayer.tv.models.VideoMetadata.resolveMimeType(url)
        }
        if (resolvedMime.isNotBlank()) {
            builder.setMimeType(resolvedMime)
        }
        val mediaItem = builder.build()
        player?.setMediaItem(mediaItem)
        player?.prepare()
        if (startPositionMs > 0L) {
            player?.seekTo(startPositionMs)
        }
        player?.play()
    }

    private fun updateState() {
        val exo = player ?: return
        val isPlaying = exo.isPlaying
        val positionMs = exo.currentPosition
        val durationMs = if (exo.duration > 0) exo.duration else 0L
        val volume = exo.volume
        
        _playerStateFlow.value = PlayerStatePayload(isPlaying, positionMs, durationMs, volume)

        activePlayingVideoId?.let { videoId ->
            if (durationMs > 0L) {
                onPlaybackProgressUpdate?.invoke(videoId, positionMs, durationMs)
            }
        }
    }
}
