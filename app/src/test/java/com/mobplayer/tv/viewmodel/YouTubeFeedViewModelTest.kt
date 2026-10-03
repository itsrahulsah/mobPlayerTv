package com.mobplayer.tv.viewmodel

import com.mobplayer.tv.data.models.CardType
import com.mobplayer.tv.repository.YouTubeCategory
import com.mobplayer.tv.repository.YouTubeRepository
import com.mobplayer.tv.testutil.MainDispatcherRule
import com.mobplayer.tv.testutil.youTubeItems
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeFeedViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: YouTubeRepository
    private lateinit var viewModel: YouTubeFeedViewModel

    @Before
    fun setup() {
        repository = mockk()
        coEvery { repository.getVideos(any()) } answers { youTubeItems(3, prefix = firstArg<YouTubeCategory>().name) }
        viewModel = YouTubeFeedViewModel(repository)
    }

    @Test
    fun `initial state is loading and nothing is fetched before start`() = runTest {
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(YouTubeCategory.ALL, viewModel.uiState.value.selectedCategory)
        coVerify(exactly = 0) { repository.getVideos(any()) }
    }

    @Test
    fun `start loads one rail per feed category`() = runTest {
        viewModel.start()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(YouTubeCategory.feedCategories.size, state.rails.size)
        assertEquals(YouTubeCategory.feedCategories.map { "yt_rail_${it.name}" }, state.rails.map { it.id })
        assertTrue(state.rails.all { it.cardType == CardType.LANDSCAPE && it.items.size == 3 })
        coVerify(exactly = 0) { repository.getVideos(YouTubeCategory.ALL) }
    }

    @Test
    fun `start is idempotent`() = runTest {
        viewModel.start()
        advanceUntilIdle()
        viewModel.start()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.getVideos(YouTubeCategory.TRENDING) }
    }

    @Test
    fun `failing category is skipped on the All feed`() = runTest {
        coEvery { repository.getVideos(YouTubeCategory.MUSIC) } throws RuntimeException("boom")
        coEvery { repository.getVideos(YouTubeCategory.NEWS) } returns emptyList()

        viewModel.start()
        advanceUntilIdle()

        val ids = viewModel.uiState.value.rails.map { it.id }
        assertFalse("yt_rail_MUSIC" in ids)
        assertFalse("yt_rail_NEWS" in ids)
        assertEquals(YouTubeCategory.feedCategories.size - 2, ids.size)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `All feed with no videos at all reports No videos found`() = runTest {
        coEvery { repository.getVideos(any()) } returns emptyList()
        viewModel.start()
        advanceUntilIdle()
        assertEquals("No videos found", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.rails.isEmpty())
    }

    @Test
    fun `selecting a category shows its videos as a grid of five per row`() = runTest {
        coEvery { repository.getVideos(YouTubeCategory.MUSIC) } returns youTubeItems(12)

        viewModel.selectCategory(YouTubeCategory.MUSIC)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(YouTubeCategory.MUSIC, state.selectedCategory)
        assertEquals(listOf(5, 5, 2), state.rails.map { it.items.size })
        assertEquals("Music", state.rails[0].title)
        assertEquals("", state.rails[1].title)
        assertNull(state.rails[1].subtitle)
    }

    @Test
    fun `category failure surfaces the error message`() = runTest {
        coEvery { repository.getVideos(YouTubeCategory.NEWS) } throws RuntimeException("Network down")

        viewModel.selectCategory(YouTubeCategory.NEWS)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Network down", state.error)
        assertTrue(state.rails.isEmpty())
    }

    @Test
    fun `reselecting the current category does not reload`() = runTest {
        viewModel.selectCategory(YouTubeCategory.SPORTS)
        advanceUntilIdle()
        viewModel.selectCategory(YouTubeCategory.SPORTS)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.getVideos(YouTubeCategory.SPORTS) }
    }

    @Test
    fun `category results are cached until refresh`() = runTest {
        viewModel.selectCategory(YouTubeCategory.GAMING)
        advanceUntilIdle()
        viewModel.selectCategory(YouTubeCategory.MOVIES)
        advanceUntilIdle()
        viewModel.selectCategory(YouTubeCategory.GAMING)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.getVideos(YouTubeCategory.GAMING) }

        viewModel.refresh()
        advanceUntilIdle()
        coVerify(exactly = 2) { repository.getVideos(YouTubeCategory.GAMING) }
    }

    @Test
    fun `switching category mid-load keeps the newer category`() = runTest {
        val slow = CompletableDeferred<List<com.mobplayer.tv.data.models.MediaItemModel>>()
        coEvery { repository.getVideos(YouTubeCategory.TRENDING) } coAnswers { slow.await() }
        coEvery { repository.getVideos(YouTubeCategory.NEWS) } returns youTubeItems(2, prefix = "news")

        viewModel.selectCategory(YouTubeCategory.TRENDING)
        advanceUntilIdle()
        viewModel.selectCategory(YouTubeCategory.NEWS)
        advanceUntilIdle()
        slow.complete(youTubeItems(4, prefix = "trend"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(YouTubeCategory.NEWS, state.selectedCategory)
        assertEquals(listOf("news1", "news2"), state.rails.flatMap { it.items }.map { it.youtubeVideoId })
    }

    @Test
    fun `gridRails chunks items and only titles the first row`() {
        val rails = gridRails("prefix", "Title", "Sub", youTubeItems(11))
        assertEquals(listOf("prefix_0", "prefix_1", "prefix_2"), rails.map { it.id })
        assertEquals(listOf("Title", "", ""), rails.map { it.title })
        assertEquals(listOf("Sub", null, null), rails.map { it.subtitle })
        assertTrue(gridRails("p", "T", null, emptyList()).isEmpty())
    }
}
