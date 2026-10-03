package com.mobplayer.tv.viewmodel

import android.content.Context
import com.mobplayer.tv.repository.YouTubeRepository
import com.mobplayer.tv.testutil.FakeSharedPreferences
import com.mobplayer.tv.testutil.MainDispatcherRule
import com.mobplayer.tv.testutil.youTubeItems
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeSearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: YouTubeRepository
    private lateinit var prefs: FakeSharedPreferences
    private lateinit var context: Context

    @Before
    fun setup() {
        repository = mockk()
        coEvery { repository.searchVideos(any()) } returns youTubeItems(7)
        prefs = FakeSharedPreferences()
        context = mockk {
            every { getSharedPreferences("youtube_search", Context.MODE_PRIVATE) } returns prefs
        }
    }

    private fun newViewModel() = YouTubeSearchViewModel(repository, context)

    @Test
    fun `typing searches only after the debounce`() = runTest {
        val viewModel = newViewModel()
        viewModel.onQueryChange("cats")
        assertEquals("cats", viewModel.uiState.value.query)

        advanceTimeBy(799)
        runCurrent()
        coVerify(exactly = 0) { repository.searchVideos(any()) }

        advanceTimeBy(2)
        runCurrent()
        coVerify(exactly = 1) { repository.searchVideos("cats") }

        val state = viewModel.uiState.value
        assertEquals("cats", state.searchedQuery)
        assertFalse(state.isLoading)
        assertEquals(listOf(5, 2), state.rails.map { it.items.size })
        assertEquals("Results for \"cats\"", state.rails[0].title)
        assertEquals("7 videos", state.rails[0].subtitle)
        // Typing alone doesn't remember the query
        assertTrue(state.recentSearches.isEmpty())
    }

    @Test
    fun `typing again restarts the debounce`() = runTest {
        val viewModel = newViewModel()
        viewModel.onQueryChange("ca")
        advanceTimeBy(500)
        viewModel.onQueryChange("cat")
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.searchVideos("ca") }
        coVerify(exactly = 1) { repository.searchVideos("cat") }
    }

    @Test
    fun `queries shorter than two characters do not search`() = runTest {
        val viewModel = newViewModel()
        viewModel.onQueryChange(" a ")
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.searchVideos(any()) }
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `clearing the query clears the results`() = runTest {
        val viewModel = newViewModel()
        viewModel.submit("dogs")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.rails.isNotEmpty())

        viewModel.onQueryChange("")
        val state = viewModel.uiState.value
        assertNull(state.searchedQuery)
        assertTrue(state.rails.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun `submit searches immediately and remembers the trimmed query`() = runTest {
        val viewModel = newViewModel()
        viewModel.submit("  lofi beats  ")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.searchVideos("lofi beats") }
        assertEquals("lofi beats", viewModel.uiState.value.query)
        assertEquals(listOf("lofi beats"), viewModel.uiState.value.recentSearches)
    }

    @Test
    fun `blank submit is ignored`() = runTest {
        val viewModel = newViewModel()
        viewModel.submit("   ")
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.searchVideos(any()) }
    }

    @Test
    fun `submitting results already on screen does not search again`() = runTest {
        val viewModel = newViewModel()
        viewModel.onQueryChange("news")
        advanceUntilIdle()
        viewModel.submit()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.searchVideos("news") }
        assertEquals(listOf("news"), viewModel.uiState.value.recentSearches)
    }

    @Test
    fun `recent searches are deduplicated case-insensitively, newest first, capped at eight`() = runTest {
        val viewModel = newViewModel()
        (1..9).forEach { viewModel.submit("query $it"); advanceUntilIdle() }
        viewModel.submit("QUERY 5")
        advanceUntilIdle()

        val recents = viewModel.uiState.value.recentSearches
        assertEquals(8, recents.size)
        assertEquals("QUERY 5", recents.first())
        assertEquals(1, recents.count { it.equals("query 5", ignoreCase = true) })
        assertFalse("query 1" in recents)
    }

    @Test
    fun `line breaks in a query are flattened before saving`() = runTest {
        val viewModel = newViewModel()
        viewModel.submit("hello\r\nworld")
        advanceUntilIdle()
        assertEquals(listOf("hello world"), viewModel.uiState.value.recentSearches)
    }

    @Test
    fun `recent searches persist across view model instances`() = runTest {
        val first = newViewModel()
        first.submit("one")
        advanceUntilIdle()
        first.submit("two")
        advanceUntilIdle()

        assertEquals(listOf("two", "one"), newViewModel().uiState.value.recentSearches)
    }

    @Test
    fun `clearRecentSearches empties state and storage`() = runTest {
        val viewModel = newViewModel()
        viewModel.submit("one")
        advanceUntilIdle()
        viewModel.clearRecentSearches()

        assertTrue(viewModel.uiState.value.recentSearches.isEmpty())
        assertTrue(newViewModel().uiState.value.recentSearches.isEmpty())
    }

    @Test
    fun `empty results report no videos found`() = runTest {
        coEvery { repository.searchVideos("zzz") } returns emptyList()
        val viewModel = newViewModel()
        viewModel.submit("zzz")
        advanceUntilIdle()
        assertEquals("No videos found for \"zzz\"", viewModel.uiState.value.error)
    }

    @Test
    fun `search failure shows error and retry runs the failed query again`() = runTest {
        coEvery { repository.searchVideos("music") } throws RuntimeException("Timeout") andThen youTubeItems(3)
        val viewModel = newViewModel()
        viewModel.submit("music")
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals("Timeout", state.error)
        assertEquals("music", state.searchedQuery)
        assertTrue(state.rails.isEmpty())

        viewModel.retry()
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertNull(state.error)
        assertEquals(3, state.rails.single().items.size)
        coVerify(exactly = 2) { repository.searchVideos("music") }
    }
}
