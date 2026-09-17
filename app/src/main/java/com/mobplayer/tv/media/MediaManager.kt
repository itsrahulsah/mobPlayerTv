package com.mobplayer.tv.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
        val exoPlayer = ExoPlayer.Builder(context).build()
        player = exoPlayer
        
        mediaSession = MediaSession.Builder(context, exoPlayer).build()

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

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
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
        val volume = exo.volume
        
        _playerStateFlow.value = PlayerStatePayload(isPlaying, positionMs, volume)
    }
}
