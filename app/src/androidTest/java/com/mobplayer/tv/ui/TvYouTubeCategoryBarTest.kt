package com.mobplayer.tv.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mobplayer.tv.repository.YouTubeCategory
import com.mobplayer.tv.ui.components.TvYouTubeCategoryBar
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvYouTubeCategoryBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        selected: YouTubeCategory = YouTubeCategory.ALL,
        isLoading: Boolean = false,
        error: String? = null,
        onSelected: (YouTubeCategory) -> Unit = {},
        onRetry: () -> Unit = {}
    ) = composeRule.setContent {
        TvYouTubeCategoryBar(
            categories = YouTubeCategory.entries.take(4),
            selectedCategory = selected,
            isLoading = isLoading,
            error = error,
            onCategorySelected = onSelected,
            onRetry = onRetry
        )
    }

    @Test
    fun showsHeaderAndCategoryChips() {
        show()
        composeRule.onNodeWithText("YouTube").assertIsDisplayed()
        YouTubeCategory.entries.take(4).forEach { composeRule.onNodeWithText(it.title).assertIsDisplayed() }
    }

    @Test
    fun clickingChipReportsCategory() {
        var picked: YouTubeCategory? = null
        show(onSelected = { picked = it })

        composeRule.onNodeWithText("Trending").performClick()
        assertEquals(YouTubeCategory.TRENDING, picked)
    }

    @Test
    fun loadingStateNamesTheSelectedCategory() {
        show(selected = YouTubeCategory.MUSIC, isLoading = true)
        composeRule.onNodeWithText("Loading Music videos…").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun errorStateShowsMessageAndRetry() {
        var retried = false
        show(error = "No videos found", onRetry = { retried = true })

        composeRule.onNodeWithText("No videos found").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun loadingTakesPrecedenceOverError() {
        show(isLoading = true, error = "stale error")
        composeRule.onNodeWithText("stale error").assertDoesNotExist()
    }
}
