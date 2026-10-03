package com.mobplayer.tv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobplayer.tv.data.models.CardType
import com.mobplayer.tv.data.models.MediaItemModel
import com.mobplayer.tv.data.models.MediaRailModel
import com.mobplayer.tv.repository.YouTubeCategory
import com.mobplayer.tv.repository.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class YouTubeFeedUiState(
    val selectedCategory: YouTubeCategory = YouTubeCategory.ALL,
    val rails: List<MediaRailModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class YouTubeFeedViewModel @Inject constructor(
    private val youTubeRepository: YouTubeRepository
) : ViewModel() {

    // Loading until start(): the feed isn't fetched before the app has finished starting up
    private val _uiState = MutableStateFlow(YouTubeFeedUiState(isLoading = true))
    val uiState: StateFlow<YouTubeFeedUiState> = _uiState.asStateFlow()

    val categories = YouTubeCategory.entries

    private val cache = mutableMapOf<YouTubeCategory, List<MediaItemModel>>()
    private var loadJob: Job? = null
    private var isStarted = false

    /**
     * Fetches the initial feed. Deferred by the caller until start-up is done: the parallel
     * category fetches and their parsing compete with the first frame for a TV's few cores.
     */
    fun start() {
        if (isStarted) return
        isStarted = true
        load(_uiState.value.selectedCategory)
    }

    fun selectCategory(category: YouTubeCategory) {
        if (isStarted && _uiState.value.selectedCategory == category) return
        isStarted = true
        load(category)
    }

    fun refresh() {
        isStarted = true
        cache.clear()
        load(_uiState.value.selectedCategory)
    }

    private fun load(category: YouTubeCategory) {
        loadJob?.cancel()
        _uiState.update { it.copy(selectedCategory = category, rails = emptyList(), isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            var error: String? = null
            val rails = if (category == YouTubeCategory.ALL) {
                // One rail per category, fetched in parallel; a failing category is just skipped.
                YouTubeCategory.feedCategories
                    .map { cat -> async { cat to runCatching { videosFor(cat) }.getOrDefault(emptyList()) } }
                    .awaitAll()
                    .filter { (_, videos) -> videos.isNotEmpty() }
                    .map { (cat, videos) -> rail("yt_rail_${cat.name}", cat.title, cat.subtitle, videos) }
            } else {
                runCatching { videosFor(category) }
                    .onFailure { e -> error = e.localizedMessage ?: "Failed to load YouTube" }
                    .getOrDefault(emptyList())
                    .let { gridRails("yt_rail_${category.name}", category.title, category.subtitle, it) }
            }
            ensureActive() // runCatching swallows cancellation; don't overwrite a newer category
            _uiState.update {
                it.copy(
                    rails = rails,
                    isLoading = false,
                    error = error ?: if (rails.isEmpty()) "No videos found" else null
                )
            }
        }
    }

    private suspend fun videosFor(category: YouTubeCategory): List<MediaItemModel> =
        cache[category] ?: youTubeRepository.getVideos(category).also { cache[category] = it }

    private fun rail(id: String, title: String, subtitle: String?, items: List<MediaItemModel>) =
        MediaRailModel(id = id, title = title, subtitle = subtitle, cardType = CardType.LANDSCAPE, items = items)
}

private const val GRID_COLUMNS = 5

/**
 * Wraps a full video list into grid-like rows so every video is reachable with the D-pad.
 * Only the first row carries the title.
 */
internal fun gridRails(
    idPrefix: String,
    title: String,
    subtitle: String?,
    items: List<MediaItemModel>
): List<MediaRailModel> = items.chunked(GRID_COLUMNS).mapIndexed { index, row ->
    MediaRailModel(
        id = "${idPrefix}_$index",
        title = if (index == 0) title else "",
        subtitle = if (index == 0) subtitle else null,
        cardType = CardType.LANDSCAPE,
        items = row
    )
}
