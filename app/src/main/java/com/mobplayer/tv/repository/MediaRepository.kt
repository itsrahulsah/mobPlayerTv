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
        private set(value) {
            field = value
            _currentMediaId.value = value
        }

    /** Id of the media currently loaded (null when stopped); lets screens detect a takeover. */
    private val _currentMediaId = MutableStateFlow<String?>(null)
    val currentMediaId: StateFlow<String?> = _currentMediaId

    /** Bumped on every load, so a takeover is visible even for URL casts that carry no media id. */
    private val _loadCount = MutableStateFlow(0L)
    val loadCount: StateFlow<Long> = _loadCount
    var onPlaybackProgressUpdate: ((videoId: String, positionMs: Long, durationMs: Long) -> Unit)? = null
    var onPlaybackError: ((error: androidx.media3.common.PlaybackException) -> Unit)? = null

    /**
     * The screen (its ViewModel) the player is shown on. Remote commands create the player on
     * demand only while one is attached: once released, a cast arriving during server shutdown or
     * to a sticky-restarted service must not start audio with nothing on screen.
     */
    private var owner: Any? = null

    fun attach(owner: Any) {
        this.owner = owner
    }

    /** The player, created if a screen is attached; null otherwise (commands are then dropped). */
    private fun playerOrCreate(): ExoPlayer? = player ?: owner?.let { initialize() }

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

    /** Releases the player, unless a newer screen has attached since (a quick relaunch). */
    fun release(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        stopProgressPolling()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
    }

    fun play() {
        player?.play()
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
        // Created if needed, so a volume set before anything plays still applies to the first video
        val player = playerOrCreate() ?: return
        player.volume = volume.coerceIn(0f, 1f)
        updateState()
    }

    fun loadMedia(
        url: String,
        startPositionMs: Long = 0L,
        videoId: String? = null,
        mimeType: String? = null
    ) {
        _loadCount.value++
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
        // The player is created on first use (see MainActivity), which a remote cast can precede
        val player = playerOrCreate() ?: return
        if (isGrowingUploadStream(url)) {
            player.setMediaSource(buildGrowingUploadSource(mediaItem))
        } else {
            player.setMediaItem(mediaItem)
        }
        player.prepare()
        if (startPositionMs > 0L) {
            player.seekTo(startPositionMs)
        }
        player.play()
    }

    private fun isGrowingUploadStream(url: String): Boolean =
        url.startsWith("http://127.0.0.1:") && url.contains("/api/stream/")

    /**
     * Source for a file that is still being uploaded. The server holds the connection open until
     * the requested bytes arrive, so reads can stall far longer than ExoPlayer's 8s default.
     * Matroska seek-for-cues is disabled because Cues usually sit at the end of the file, which
     * would block startup until the whole upload finished (the stream is unseekable as a result).
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun buildGrowingUploadSource(mediaItem: MediaItem): androidx.media3.exoplayer.source.MediaSource {
        val httpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(60_000)
        val extractors = androidx.media3.extractor.DefaultExtractorsFactory()
            .setMatroskaExtractorFlags(androidx.media3.extractor.mkv.MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES)
        return androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(httpFactory, extractors)
            .setLoadErrorHandlingPolicy(
                androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(/* minimumLoadableRetryCount = */ 30)
            )
            .createMediaSource(mediaItem)
    }

    /** Plays a pre-built source (e.g. a YouTube DASH manifest that needs custom headers). */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun loadMediaSource(mediaSource: androidx.media3.exoplayer.source.MediaSource, videoId: String) {
        _loadCount.value++
        activePlayingVideoId = videoId
        val player = playerOrCreate() ?: return
        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()
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
