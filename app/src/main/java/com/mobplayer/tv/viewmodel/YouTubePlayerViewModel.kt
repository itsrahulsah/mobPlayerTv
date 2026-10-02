package com.mobplayer.tv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobplayer.tv.data.models.MediaItemModel
import com.mobplayer.tv.models.RemoteActionEvent
import com.mobplayer.tv.models.RemoteIconType
import com.mobplayer.tv.repository.MediaRepository
import com.mobplayer.tv.repository.ServerRepository
import com.mobplayer.tv.repository.YouTubeRepository
import com.mobplayer.tv.youtube.PlaybackSource
import com.mobplayer.tv.youtube.SmartTubePlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val isLoadingSuggestions: Boolean = false
)

/**
 * Drives the dedicated YouTube player screen. Playback goes through the shared [MediaRepository]
 * ExoPlayer so the phone remote (play/pause/seek/volume) keeps working.
 */
@HiltViewModel
class YouTubePlayerViewModel @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val mediaRepository: MediaRepository,
    private val serverRepository: ServerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(YouTubePlayerUiState())
    val uiState: StateFlow<YouTubePlayerUiState> = _uiState.asStateFlow()

    private val httpDataSourceFactory by lazy { SmartTubePlayerEngine.createHttpDataSourceFactory() }
    private var resolveJob: Job? = null
    private var suggestionsJob: Job? = null

    /** [MediaRepository.loadCount] while the player holds our video (or nothing, mid-resolve). */
    private var expectedLoadCount = 0L

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
    }

    fun play(item: MediaItemModel) {
        val videoId = item.youtubeVideoId ?: return
        resolveJob?.cancel()
        suggestionsJob?.cancel()
        _uiState.value = YouTubePlayerUiState(item = item, isResolving = true, isLoadingSuggestions = true)
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

    fun retry() {
        _uiState.value.item?.let(::play)
    }

    /** Back/exit from the YouTube screen. */
    fun close() {
        dismiss()
        serverRepository.closePlayer()
        mediaRepository.stop()
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(isResolving = false, error = message) }
    }

    private fun dismiss() {
        resolveJob?.cancel()
        suggestionsJob?.cancel()
        if (_uiState.value.item != null) _uiState.value = YouTubePlayerUiState()
    }
}
