package com.mobplayer.tv.viewmodel

import android.util.Log
import androidx.media3.exoplayer.source.MediaSource
import com.mobplayer.tv.repository.MediaRepository
import com.mobplayer.tv.repository.ServerRepository
import com.mobplayer.tv.repository.YouTubeRepository
import com.mobplayer.tv.testutil.MainDispatcherRule
import com.mobplayer.tv.testutil.youTubeItem
import com.mobplayer.tv.testutil.youTubeItems
import com.mobplayer.tv.youtube.PlaybackSource
import com.mobplayer.tv.youtube.SmartTubePlayerEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YouTubePlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var youTubeRepository: YouTubeRepository
    private lateinit var mediaRepository: MediaRepository
    private lateinit var serverRepository: ServerRepository
    private lateinit var viewModel: YouTubePlayerViewModel

    private val loadCount = MutableStateFlow(0L)
    private val currentMediaId = MutableStateFlow<String?>(null)
    private val mediaSource: MediaSource = mockk()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkObject(SmartTubePlayerEngine)
        every { SmartTubePlayerEngine.createHttpDataSourceFactory() } returns mockk()
        every { SmartTubePlayerEngine.buildMediaSource(any(), any()) } returns mediaSource

        youTubeRepository = mockk()
        coEvery { youTubeRepository.resolvePlaybackSource(any()) } returns PlaybackSource.Hls("https://example.com/live.m3u8")
        coEvery { youTubeRepository.getSuggestions(any()) } returns youTubeItems(4, prefix = "next")

        // Fake the bits of MediaRepository the view model observes
        mediaRepository = mockk(relaxed = true)
        every { mediaRepository.loadCount } returns loadCount
        every { mediaRepository.currentMediaId } returns currentMediaId
        every { mediaRepository.stop() } answers { currentMediaId.value = null }
        every { mediaRepository.loadMediaSource(any(), any()) } answers {
            loadCount.value++
            currentMediaId.value = secondArg()
        }

        serverRepository = ServerRepository()
        viewModel = YouTubePlayerViewModel(youTubeRepository, mediaRepository, serverRepository)
    }

    @After
    fun tearDown() = unmockkAll()

    /** Resolution hops to Dispatchers.Default, so wait for its result instead of only advancing time. */
    private suspend fun TestScope.awaitResolved(): YouTubePlayerUiState {
        // withTimeout inside runTest uses virtual time; run it on a real dispatcher so it can't fire early
        val state = withContext(Dispatchers.IO) {
            withTimeout(5_000) { viewModel.uiState.first { it.item == null || !it.isResolving } }
        }
        advanceUntilIdle()
        return viewModel.uiState.value.takeIf { it.item != null } ?: state
    }

    @Test
    fun `play ignores items without a YouTube id`() = runTest {
        viewModel.play(youTubeItem("x").copy(youtubeVideoId = null))
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.item)
        verify(exactly = 0) { mediaRepository.stop() }
    }

    @Test
    fun `play shows resolving state, stops old media and opens the player`() = runTest {
        advanceUntilIdle()
        val item = youTubeItem("abc", "Lofi Girl")
        viewModel.play(item)

        val state = viewModel.uiState.value
        assertEquals(item, state.item)
        assertTrue(state.isResolving)
        assertTrue(state.isLoadingSuggestions)
        verify { mediaRepository.stop() }
        assertTrue(serverRepository.isPlayerActive.value)
        assertEquals("Lofi Girl", serverRepository.activeMediaTitle.value)
    }

    @Test
    fun `successful resolve loads the source and suggestions`() = runTest {
        advanceUntilIdle()
        viewModel.play(youTubeItem("abc", "Lofi Girl"))

        val state = awaitResolved()
        assertFalse(state.isResolving)
        assertNull(state.error)
        assertEquals(listOf("next1", "next2", "next3", "next4"), state.suggestions.map { it.youtubeVideoId })
        assertFalse(state.isLoadingSuggestions)
        verify(exactly = 1) { mediaRepository.loadMediaSource(mediaSource, "abc") }
        assertTrue(viewModel.isOwnLoad(loadCount.value))
        assertEquals("PLAY", serverRepository.remoteActionEvent.value?.action)
    }

    @Test
    fun `no playable stream shows the source error`() = runTest {
        coEvery { youTubeRepository.resolvePlaybackSource("abc") } returns PlaybackSource.Error("Video unavailable")
        every { SmartTubePlayerEngine.buildMediaSource(any(), any()) } returns null
        advanceUntilIdle()

        viewModel.play(youTubeItem("abc"))
        val state = awaitResolved()

        assertEquals("Video unavailable", state.error)
        verify(exactly = 0) { mediaRepository.loadMediaSource(any(), any()) }
    }

    @Test
    fun `null media source without an Error source shows a generic message`() = runTest {
        every { SmartTubePlayerEngine.buildMediaSource(any(), any()) } returns null
        advanceUntilIdle()

        viewModel.play(youTubeItem("abc"))
        assertEquals("No playable stream found", awaitResolved().error)
    }

    @Test
    fun `resolve exception shows its message`() = runTest {
        coEvery { youTubeRepository.resolvePlaybackSource("abc") } throws IllegalStateException("Signature decipher failed")
        advanceUntilIdle()

        viewModel.play(youTubeItem("abc"))
        assertEquals("Signature decipher failed", awaitResolved().error)
    }

    @Test
    fun `suggestions failure does not block playback`() = runTest {
        coEvery { youTubeRepository.getSuggestions("abc") } throws RuntimeException("no up next")
        advanceUntilIdle()

        viewModel.play(youTubeItem("abc"))
        val state = awaitResolved()

        assertTrue(state.suggestions.isEmpty())
        assertFalse(state.isLoadingSuggestions)
        assertNull(state.error)
        verify(exactly = 1) { mediaRepository.loadMediaSource(mediaSource, "abc") }
    }

    @Test
    fun `retry plays the current item again`() = runTest {
        coEvery { youTubeRepository.resolvePlaybackSource("abc") } throws RuntimeException("fail") andThen
            PlaybackSource.Progressive("https://example.com/v.mp4")
        advanceUntilIdle()

        viewModel.play(youTubeItem("abc"))
        assertNotNull(awaitResolved().error)

        viewModel.retry()
        val state = awaitResolved()
        assertNull(state.error)
        coVerify(exactly = 2) { youTubeRepository.resolvePlaybackSource("abc") }
    }

    @Test
    fun `close resets the screen and stops playback`() = runTest {
        advanceUntilIdle()
        viewModel.play(youTubeItem("abc"))
        awaitResolved()

        viewModel.close()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.item)
        assertFalse(serverRepository.isPlayerActive.value)
        verify(atLeast = 2) { mediaRepository.stop() }
    }

    @Test
    fun `player closed elsewhere dismisses the screen`() = runTest {
        advanceUntilIdle()
        viewModel.play(youTubeItem("abc"))
        awaitResolved()

        serverRepository.closePlayer()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.item)
    }

    @Test
    fun `other media loaded over the video dismisses the screen`() = runTest {
        advanceUntilIdle()
        viewModel.play(youTubeItem("abc"))
        awaitResolved()

        loadCount.value++ // e.g. an upload or URL cast from the phone
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.item)
        assertFalse(viewModel.isOwnLoad(loadCount.value))
    }

    @Test
    fun `media stopped elsewhere dismisses the screen`() = runTest {
        advanceUntilIdle()
        viewModel.play(youTubeItem("abc"))
        awaitResolved()

        currentMediaId.value = null
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.item)
    }

    @Test
    fun `error screen stays when media id is cleared`() = runTest {
        coEvery { youTubeRepository.resolvePlaybackSource("abc") } throws RuntimeException("fail")
        advanceUntilIdle()
        viewModel.play(youTubeItem("abc"))
        awaitResolved()

        currentMediaId.value = "something"
        advanceUntilIdle()
        currentMediaId.value = null
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.item)
        assertEquals("fail", viewModel.uiState.value.error)
    }
}
