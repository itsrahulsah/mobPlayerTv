package com.mobplayer.tv.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobplayer.tv.data.models.MediaRailModel
import com.mobplayer.tv.repository.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class YouTubeSearchUiState(
    val query: String = "",
    /** Query the current [rails] belong to; null until the first search. */
    val searchedQuery: String? = null,
    val rails: List<MediaRailModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val recentSearches: List<String> = emptyList()
)

@HiltViewModel
class YouTubeSearchViewModel @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    @ApplicationContext context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(YouTubeSearchUiState(recentSearches = loadRecents()))
    val uiState: StateFlow<YouTubeSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    /** Typing searches automatically once the user pauses, so the IME doesn't need a submit. */
    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        val trimmed = query.trim()
        searchJob?.cancel()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            if (trimmed.isEmpty()) _uiState.update { it.copy(searchedQuery = null, rails = emptyList(), isLoading = false, error = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(TYPING_DEBOUNCE_MS)
            runSearch(trimmed, remember = false)
        }
    }

    /** IME "search" action or picking a recent search: run immediately and remember it. */
    fun submit(query: String = _uiState.value.query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { it.copy(query = trimmed) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(trimmed, remember = true) }
    }

    fun retry() = submit(_uiState.value.searchedQuery ?: _uiState.value.query)

    fun clearRecentSearches() {
        prefs.edit().remove(KEY_RECENTS).apply()
        _uiState.update { it.copy(recentSearches = emptyList()) }
    }

    private suspend fun runSearch(query: String, remember: Boolean) {
        if (remember) addRecent(query)
        val current = _uiState.value
        // Already showing these results (e.g. debounce fired, then the user pressed search).
        if (current.searchedQuery == query && current.error == null && !current.isLoading && current.rails.isNotEmpty()) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            val videos = youTubeRepository.searchVideos(query)
            _uiState.update {
                it.copy(
                    searchedQuery = query,
                    // Query in the id so each search gets fresh row scroll positions
                    rails = gridRails("yt_search_$query", "Results for \"$query\"", "${videos.size} videos", videos),
                    isLoading = false,
                    error = if (videos.isEmpty()) "No videos found for \"$query\"" else null
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update {
                it.copy(searchedQuery = query, rails = emptyList(), isLoading = false, error = e.localizedMessage ?: "Search failed")
            }
        }
    }

    private fun addRecent(query: String) {
        val updated = (listOf(query) + _uiState.value.recentSearches.filterNot { it.equals(query, ignoreCase = true) })
            .take(MAX_RECENTS)
        prefs.edit().putString(KEY_RECENTS, updated.joinToString(SEPARATOR)).apply()
        _uiState.update { it.copy(recentSearches = updated) }
    }

    private fun loadRecents(): List<String> =
        prefs.getString(KEY_RECENTS, null)?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        const val PREFS_NAME = "youtube_search"
        const val KEY_RECENTS = "recent_searches"
        const val SEPARATOR = "\n"
        const val MAX_RECENTS = 8
        const val MIN_QUERY_LENGTH = 2
        const val TYPING_DEBOUNCE_MS = 800L
    }
}
