package com.mobplayer.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mobplayer.tv.R
import com.mobplayer.tv.data.models.CardType
import com.mobplayer.tv.data.models.MediaItemModel
import com.mobplayer.tv.repository.YouTubeCategory
import com.mobplayer.tv.ui.components.TvContentRail
import com.mobplayer.tv.ui.components.TvHeroBillboard
import com.mobplayer.tv.ui.components.TvTopBar
import com.mobplayer.tv.ui.components.TvYouTubeCategoryBar
import com.mobplayer.tv.ui.components.TvYouTubeSearchBar
import com.mobplayer.tv.ui.focus.LocalBrowseFocus
import com.mobplayer.tv.ui.theme.TvColors
import kotlinx.coroutines.delay
import com.mobplayer.tv.viewmodel.YouTubeFeedUiState
import com.mobplayer.tv.viewmodel.YouTubeSearchUiState

@Composable
fun TvHomeScreen(
    connectedDeviceName: String?,
    uploadedVideos: List<com.mobplayer.tv.models.VideoMetadata> = emptyList(),
    /** Hoisted so the tab (e.g. Search with its results) survives a trip to the player. */
    selectedTab: String = stringResource(R.string.tab_home),
    onTabSelected: (String) -> Unit = {},
    youTubeState: YouTubeFeedUiState = YouTubeFeedUiState(),
    youTubeCategories: List<YouTubeCategory> = YouTubeCategory.entries,
    onYouTubeCategorySelected: (YouTubeCategory) -> Unit = {},
    onYouTubeRetry: () -> Unit = {},
    searchState: YouTubeSearchUiState = YouTubeSearchUiState(),
    onSearchQueryChange: (String) -> Unit = {},
    onSearchSubmit: (String) -> Unit = {},
    onSearchClearRecents: () -> Unit = {},
    onSearchRetry: () -> Unit = {},
    onPlayMedia: (MediaItemModel) -> Unit,
    onDisconnect: () -> Unit,
    /** Translucent when a minimized video plays behind the browse screens. */
    backgroundColor: Color = TvColors.BackgroundDark,
    modifier: Modifier = Modifier
) {
    val tabHome = stringResource(R.string.tab_home)
    val tabSearch = stringResource(R.string.tab_search)
    val tabMyList = stringResource(R.string.tab_my_list)

    // Per-tab scroll lives above this screen so it survives a trip to the player
    val browseFocus = LocalBrowseFocus.current
    val columnState = browseFocus.columnState(selectedTab)
    // If the clicked item is gone when we come back (e.g. list reloaded), stop waiting for it
    LaunchedEffect(Unit) {
        delay(1_000)
        browseFocus.consumeRestore()
    }

    val uploadedRail = remember(uploadedVideos) {
        if (uploadedVideos.isEmpty()) null
        else {
            val items = uploadedVideos.map { meta ->
                val progressVal = if (meta.progress > 0f) meta.progress else null
                val remainingStr = if (meta.durationMs > 0 && meta.lastPlayedPositionMs > 0) {
                    val remMin = (meta.remainingMs / 60000).toInt()
                    if (remMin > 0) "$remMin min left" else "Almost finished"
                } else null

                MediaItemModel(
                    id = meta.id,
                    title = meta.title,
                    subtitle = if (progressVal != null) "${(meta.progress * 100).toInt()}% watched" else meta.fileSizeFormatted,
                    description = "Uploaded video: ${meta.originalFileName}",
                    posterUrl = "",
                    backdropUrl = "",
                    genres = listOf("My Uploads", meta.mimeType.substringAfterLast('/')),
                    year = "Local",
                    duration = if (meta.durationMs > 0) String.format("%02d:%02d", meta.durationMs / 60000, (meta.durationMs / 1000) % 60) else meta.fileSizeFormatted,
                    rating = "HD",
                    badge = if (progressVal != null) "RESUME" else "NEW",
                    progress = progressVal,
                    remainingTime = remainingStr,
                    videoUrl = meta.fileName,
                    gradientColors = listOf(0xFF0F2027, 0xFF203A43, 0xFF2C5364)
                )
            }
            com.mobplayer.tv.data.models.MediaRailModel(
                id = "rail_uploaded_videos",
                title = "Uploaded Videos",
                subtitle = "${uploadedVideos.size} videos stored on TV",
                cardType = CardType.CONTINUE_WATCHING,
                items = items
            )
        }
    }

    // Feature the first YouTube video (top of the first loaded rail) in the hero banner. The card
    // badge holds the duration, which the hero already shows, so label it with the rail instead.
    val featuredItem = remember(youTubeState.rails) {
        youTubeState.rails.firstOrNull()?.let { rail ->
            rail.items.firstOrNull()?.let { if (it.isLive) it else it.copy(badge = rail.title.uppercase()) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        LazyColumn(
            state = columnState,
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top Navigation Bar (Integrated as top item or fixed)
            item(key = "top_bar") {
                TvTopBar(
                    connectedDeviceName = connectedDeviceName,
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    onDisconnectClick = onDisconnect
                )
            }

            // 2. Cinematic Hero Billboard Banner (Home tab, once YouTube videos are loaded)
            if (selectedTab == tabHome && featuredItem != null) {
                item(key = "hero_billboard") {
                    TvHeroBillboard(
                        item = featuredItem,
                        onPlayClick = { onPlayMedia(featuredItem) },
                        onWatchlistClick = { /* Can show watchlist state */ },
                        onDetailsClick = { onPlayMedia(featuredItem) }
                    )
                }
            }

            // 3. Uploaded Videos Rail (shown on Home and My List)
            if (uploadedRail != null && (selectedTab == tabHome || selectedTab == tabMyList)) {
                item(key = "rail_uploaded_videos") {
                    TvContentRail(
                        rail = uploadedRail,
                        onItemClick = { item ->
                            onPlayMedia(item)
                        }
                    )
                }
            }

            // 4. YouTube feed: category chips + one rail per category ("All") or a wrapped grid
            if (selectedTab == tabHome) {
                item(key = "youtube_category_bar") {
                    TvYouTubeCategoryBar(
                        categories = youTubeCategories,
                        selectedCategory = youTubeState.selectedCategory,
                        isLoading = youTubeState.isLoading,
                        error = youTubeState.error,
                        onCategorySelected = onYouTubeCategorySelected,
                        onRetry = onYouTubeRetry
                    )
                }
                items(
                    items = youTubeState.rails,
                    key = { it.id }
                ) { rail ->
                    TvContentRail(
                        rail = rail,
                        onItemClick = onPlayMedia
                    )
                }
            }

            // 5. Search tab: query field, recent searches and results grid
            if (selectedTab == tabSearch) {
                item(key = "youtube_search_bar") {
                    TvYouTubeSearchBar(
                        query = searchState.query,
                        recentSearches = searchState.recentSearches,
                        isLoading = searchState.isLoading,
                        error = searchState.error,
                        onQueryChange = onSearchQueryChange,
                        onSubmit = onSearchSubmit,
                        onClearRecents = onSearchClearRecents,
                        onRetry = onSearchRetry
                    )
                }
                items(
                    items = searchState.rails,
                    key = { it.id }
                ) { rail ->
                    TvContentRail(
                        rail = rail,
                        onItemClick = onPlayMedia
                    )
                }
            }

            // Bottom TV Overscan Safe Padding
            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}
