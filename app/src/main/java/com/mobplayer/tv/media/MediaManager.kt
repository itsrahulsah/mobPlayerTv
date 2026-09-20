package com.mobplayer.tv.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.mobplayer.tv.models.PlayerStatePayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object MediaManager {
    var player: ExoPlayer? = null
        private set
        
    private var mediaSession: MediaSession? = null

    private val _playerStateFlow = MutableStateFlow<PlayerStatePayload?>(null)
    val playerStateFlow: StateFlow<PlayerStatePayload?> = _playerStateFlow

    fun initialize(context: Context): ExoPlayer {
        player?.let { return it }
        val appContext = context.applicationContext

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val exoPlayer = ExoPlayer.Builder(appContext)
            .setLoadControl(loadControl)
            .build()
        player = exoPlayer
        
        mediaSession = MediaSession.Builder(appContext, exoPlayer).build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
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
        })

        return exoPlayer
    }

    fun release() {
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
    }

    fun stop() {
        player?.stop()
        player?.clearMediaItems()
        updateState()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun setVolume(volume: Float) {
        player?.volume = volume.coerceIn(0f, 1f)
        updateState()
    }

    fun loadMedia(url: String) {
        val mediaItem = MediaItem.fromUri(url)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()
    }

    private fun updateState() {
        val exo = player ?: return
        val isPlaying = exo.isPlaying
        val positionMs = exo.currentPosition
        val durationMs = if (exo.duration > 0) exo.duration else 0L
        val volume = exo.volume
        
        _playerStateFlow.value = PlayerStatePayload(isPlaying, positionMs, durationMs, volume)
    }
}
