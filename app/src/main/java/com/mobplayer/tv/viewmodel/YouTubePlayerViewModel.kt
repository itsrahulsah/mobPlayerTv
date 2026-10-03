package com.mobplayer.tv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobplayer.tv.data.models.MediaItemModel
import com.mobplayer.tv.models.RemoteActionEvent
import com.mobplayer.tv.models.RemoteIconType
import com.mobplayer.tv.repository.MediaRepository
import com.mobplayer.tv.repository.ServerRepository
import com.mobplayer.tv.repository.YouTubeRepository
import com.mobplayer.tv.storage.WatchHistoryStore
import com.mobplayer.tv.youtube.PlaybackSource
import com.mobplayer.tv.youtube.SmartTubePlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class YouTubePlayerUiState(
    /** Non-null while the YouTube player screen is showing. */
    val item: MediaItemModel? = null,
    val isResolving: Boolean = false,
    val error: String? = null,
    /** "Up next" videos for [item], shown in the player's suggestions row. */
    val suggestions: List<MediaItemModel> = emptyList(),
    val isLoadingSuggestions: Boolean = false,
    /** Previously played videos (most recent first, excluding [item]), shown below "Up next". */
    val history: List<MediaItemModel> = emptyList()
)

/**
 * Drives the dedicated YouTube player screen. Playback goes through the shared [MediaRepository]
 * ExoPlayer so the phone remote (play/pause/seek/volume) keeps working. When a video ends, the
 * first "Up next" suggestion not yet watched in this session plays automatically. Every video that
 * actually starts playing (not just loads: the stream can still fail, e.g. HTTP 403) is added to the
 * persistent [WatchHistoryStore].
 */
@HiltViewModel
class YouTubePlayerViewModel @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val mediaRepository: MediaRepository,
    private val serverRepository: ServerRepository,
    private val watchHistoryStore: WatchHistoryStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(YouTubePlayerUiState())
    val uiState: StateFlow<YouTubePlayerUiState> = _uiState.asStateFlow()

    private val httpDataSourceFactory by lazy { SmartTubePlayerEngine.createHttpDataSourceFactory() }
    private var resolveJob: Job? = null
    private var suggestionsJob: Job? = null
    private var historyJob: Job? = null

    /** [MediaRepository.loadCount] while the player holds our video (or nothing, mid-resolve). */
    private var expectedLoadCount = 0L

    /** Videos played since the screen opened, so autoplay doesn't bounce between two videos. */
    private val watchedVideoIds = mutableSetOf<String>()

    init {
        // Leave the screen when the player is closed elsewhere (e.g. from the phone)...
        viewModelScope.launch {
            serverRepository.isPlayerActive.collect { active -> if (!active) dismiss() }
        }
        // ...or when other media (an upload, a phone-cast URL) is loaded, even mid-resolve or on
        // the error screen...
        viewModelScope.launch {
            mediaRepository.loadCount.collect { count ->
                if (_uiState.value.item != null && count != expectedLoadCount) dismiss()
            }
        }
        // ...or when our video is stopped elsewhere.
        viewModelScope.launch {
            mediaRepository.currentMediaId.collect { mediaId ->
                val state = _uiState.value
                if (state.item != null && !state.isResolving && state.error == null && mediaId == null) dismiss()
            }
        }
        // Off the main thread: this view model is created in the first composition.
        viewModelScope.launch { watchHistoryStore.load() }
        viewModelScope.launch {
            watchHistoryStore.history.collect { all -> _uiState.update { it.copy(history = all.without(it.item)) } }
        }
        // Autoplay the next suggestion when our video finishes.
        viewModelScope.launch {
            mediaRepository.playbackEnded.collect(::onPlaybackEnded)
        }
    }

    fun play(item: MediaItemModel) {
        val videoId = item.youtubeVideoId ?: return
        resolveJob?.cancel()
        suggestionsJob?.cancel()
        historyJob?.cancel()
        watchedVideoIds += videoId
        _uiState.value = YouTubePlayerUiState(
            item = item,
            isResolving = true,
            isLoadingSuggestions = true,
            history = watchHistoryStore.history.value.without(item)
        )
        // Stop whatever was playing so its audio doesn't continue while the stream resolves.
        mediaRepository.stop()
        serverRepository.openPlayer(item.title)
        expectedLoadCount = mediaRepository.loadCount.value

        // Fetched independently so a slow/failed suggestions call never delays playback.
        suggestionsJob = viewModelScope.launch {
            val suggestions = try {
                youTubeRepository.getSuggestions(videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("YouTubePlayerViewModel", "Failed to load suggestions for $videoId", e)
                emptyList()
            }
            _uiState.update { it.copy(suggestions = suggestions, isLoadingSuggestions = false) }
        }

        resolveJob = viewModelScope.launch {
            try {
                val source = youTubeRepository.resolvePlaybackSource(videoId)
                // Parsing a long video's DASH manifest can take a while; keep it off the main thread.
                val mediaSource = withContext(Dispatchers.Default) {
                    SmartTubePlayerEngine.buildMediaSource(source, httpDataSourceFactory)
                }
                if (mediaSource == null) {
                    fail((source as? PlaybackSource.Error)?.message ?: "No playable stream found")
                    return@launch
                }
                // Set before loading so the takeover watcher recognises our own load.
                expectedLoadCount = mediaRepository.loadCount.value + 1
                _uiState.update { it.copy(isResolving = false) }
                // Subscribed before loading (on the main thread, so the start can't arrive first).
                historyJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    mediaRepository.playbackStarted.first { it == videoId }
                    watchHistoryStore.record(item)
                }
                mediaRepository.loadMediaSource(mediaSource, videoId)
                serverRepository.postRemoteAction(
                    RemoteActionEvent("PLAY", "🎬 Playing", item.title, RemoteIconType.PLAY)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("YouTubePlayerViewModel", "Failed to resolve $videoId", e)
                fail(e.localizedMessage ?: "Failed to load video")
            }
        }
    }

    /** True when load number [count] is this screen's own video, not media replacing it. */
    fun isOwnLoad(count: Long): Boolean = _uiState.value.item != null && count == expectedLoadCount

    fun retry() {
        _uiState.value.item?.let(::play)
    }

    /** Back/exit from the YouTube screen. */
    fun close() {
        dismiss()
        serverRepository.closePlayer()
        mediaRepository.stop()
    }

    private suspend fun onPlaybackEnded(mediaId: String?) {
        val state = _uiState.value
        val item = state.item ?: return
        // Ignore other media ending (an upload or URL cast that replaced ours is dismissed anyway).
        if (mediaId == null || mediaId != item.youtubeVideoId || state.isResolving || state.error != null) return
        // A short video can end before its suggestions arrive.
        suggestionsJob?.join()
        val current = _uiState.value
        if (current.item !== item) return // closed or replaced while waiting
        // Stop once every suggestion was watched: replaying one would loop (A suggests B, B suggests A).
        val next = current.suggestions.firstOrNull { it.youtubeVideoId != null && it.youtubeVideoId !in watchedVideoIds }
            ?: return
        play(next)
    }

    private fun List<MediaItemModel>.without(item: MediaItemModel?) =
        if (item == null) this else filter { it.youtubeVideoId != item.youtubeVideoId }

    private fun fail(message: String) {
        _uiState.update { it.copy(isResolving = false, error = message) }
    }

    private fun dismiss() {
        resolveJob?.cancel()
        suggestionsJob?.cancel()
        historyJob?.cancel()
        watchedVideoIds.clear()
        if (_uiState.value.item != null) _uiState.value = YouTubePlayerUiState()
    }
}
