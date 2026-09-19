package com.mobplayer.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mobplayer.tv.R
import com.mobplayer.tv.data.MockMediaRepository
import com.mobplayer.tv.data.models.CardType
import com.mobplayer.tv.data.models.MediaItemModel
import com.mobplayer.tv.ui.components.TvContentRail
import com.mobplayer.tv.ui.components.TvHeroBillboard
import com.mobplayer.tv.ui.components.TvTopBar
import com.mobplayer.tv.ui.theme.TvColors

@Composable
fun TvHomeScreen(
    connectedDeviceName: String?,
    onPlayMedia: (MediaItemModel) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabHome = stringResource(R.string.tab_home)
    val tabMovies = stringResource(R.string.tab_movies)
    val tabShows = stringResource(R.string.tab_shows)
    val tabLiveTv = stringResource(R.string.tab_live_tv)
    val tabMyList = stringResource(R.string.tab_my_list)

    var selectedTab by remember { mutableStateOf(tabHome) }
    val columnState = rememberLazyListState()

    val railsToDisplay = remember(selectedTab, tabMovies, tabShows, tabLiveTv, tabMyList) {
        when (selectedTab) {
            tabMovies -> MockMediaRepository.contentRails.filter { it.cardType == CardType.POSTER }
            tabShows -> MockMediaRepository.contentRails.filter { it.cardType == CardType.LANDSCAPE }
            tabLiveTv -> MockMediaRepository.contentRails.filter { it.cardType == CardType.LIVE }
            tabMyList -> MockMediaRepository.contentRails.filter { it.cardType == CardType.CONTINUE_WATCHING }
            else -> MockMediaRepository.contentRails // "Home" displays all rails
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvColors.BackgroundDark)
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
                    onTabSelected = { selectedTab = it },
                    onDisconnectClick = onDisconnect
                )
            }

            // 2. Cinematic Hero Billboard Banner (shown on Home, Movies, and Shows tabs)
            if (selectedTab == tabHome || selectedTab == tabMovies || selectedTab == tabShows) {
                item(key = "hero_billboard") {
                    TvHeroBillboard(
                        item = MockMediaRepository.featuredItem,
                        onPlayClick = { onPlayMedia(MockMediaRepository.featuredItem) },
                        onWatchlistClick = { /* Can show watchlist state */ },
                        onDetailsClick = { onPlayMedia(MockMediaRepository.featuredItem) }
                    )
                }
            }

            // 3. Content Rails (Continue Watching, Top 10 Movies, Popular Series, Live TV)
            items(
                items = railsToDisplay,
                key = { it.id }
            ) { rail ->
                TvContentRail(
                    rail = rail,
                    onItemClick = { item ->
                        onPlayMedia(item)
                    }
                )
            }

            // Bottom TV Overscan Safe Padding
            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}
