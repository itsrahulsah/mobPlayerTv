package com.mobplayer.tv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobplayer.tv.data.models.MediaItemModel
import com.mobplayer.tv.ui.youtube.TvYouTubePlayerScreen
import com.mobplayer.tv.viewmodel.YouTubePlayerUiState
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TvYouTubePlayerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var player: ExoPlayer

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.runOnUiThread { player = ExoPlayer.Builder(context).build() }
    }

    @After
    fun tearDown() {
        composeRule.runOnUiThread { player.release() }
    }

    private fun video(id: String, title: String) = MediaItemModel(
        id = "yt_$id",
        title = title,
        description = title,
        posterUrl = "",
        backdropUrl = "",
        genres = listOf("YouTube"),
        year = "",
        duration = "",
        rating = "",
        videoUrl = "",
        youtubeVideoId = id
    )

    private fun show(history: List<MediaItemModel>, onPlay: (MediaItemModel) -> Unit = {}) =
        composeRule.setContent {
            TvYouTubePlayerScreen(
                player = player,
                state = YouTubePlayerUiState(
                    item = video("now", "Now playing"),
                    suggestions = listOf(video("s1", "Suggested video")),
                    history = history
                ),
                playerState = null,
                onTogglePlay = {},
                onSeekTo = {},
                onRetry = {},
                onBack = {},
                onPlaySuggestion = onPlay
            )
        }

    private fun openSuggestions() {
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Play").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
    }

    @Test
    fun historyIsHiddenUntilSuggestionsOpen() {
        show(history = listOf(video("old", "Old video")))
        composeRule.onNodeWithText("Watch history").assertDoesNotExist()
    }

    @Test
    fun historyRowShowsBelowUpNext() {
        show(history = listOf(video("old", "Old video")))
        openSuggestions()

        composeRule.onNodeWithText("Suggested video").assertIsDisplayed()
        composeRule.onNodeWithText("Watch history").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Old video").performScrollTo().assertIsDisplayed()
        val upNextTop = composeRule.onNodeWithText("Suggested video").fetchSemanticsNode().boundsInRoot.top
        val historyTop = composeRule.onNodeWithText("Old video").fetchSemanticsNode().boundsInRoot.top
        assertTrue("history should be below Up next", historyTop > upNextTop)
    }

    @Test
    fun emptyHistoryHidesTheRow() {
        show(history = emptyList())
        openSuggestions()

        composeRule.onNodeWithText("Suggested video").assertIsDisplayed()
        composeRule.onNodeWithText("Watch history").assertDoesNotExist()
    }

    @Test
    fun clickingHistoryVideoPlaysIt() {
        var played: MediaItemModel? = null
        show(history = listOf(video("old", "Old video")), onPlay = { played = it })
        openSuggestions()

        composeRule.onNodeWithText("Old video").performScrollTo().performClick()
        assertEquals("old", played?.youtubeVideoId)
    }

    @Test
    fun dpadDownFromUpNextFocusesHistory() {
        show(history = listOf(video("old", "Old video")))
        openSuggestions()
        composeRule.onNodeWithText("Suggested video").assertIsFocused()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Old video").assertIsFocused().assertIsDisplayed()

        // Up goes back to the suggestions, which stay open.
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Suggested video").assertIsFocused()
        composeRule.onNodeWithText("Watch history").assertExists()
    }

    @Test
    fun historyRowReturnsToTheLastFocusedCard() {
        show(history = listOf(video("old1", "Old video 1"), video("old2", "Old video 2")))
        openSuggestions()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Old video 2").assertIsFocused()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Old video 2").assertIsFocused()
    }

    @Test
    fun dpadUpFromUpNextClosesTheRows() {
        show(history = listOf(video("old", "Old video")))
        openSuggestions()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Play").assertIsFocused()
        composeRule.onNodeWithText("Watch history").assertDoesNotExist()
    }
}
