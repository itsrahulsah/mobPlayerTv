package com.mobplayer.tv.viewmodel

import android.util.Log
import androidx.media3.exoplayer.source.MediaSource
import com.mobplayer.tv.data.models.MediaItemModel
import com.mobplayer.tv.repository.MediaRepository
import com.mobplayer.tv.repository.ServerRepository
import com.mobplayer.tv.repository.YouTubeRepository
import com.mobplayer.tv.storage.WatchHistoryStore
import com.mobplayer.tv.testutil.FakeSharedPreferences
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
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
    private lateinit var watchHistoryStore: WatchHistoryStore
    private val historyPrefs = FakeSharedPreferences()
    private lateinit var viewModel: YouTubePlayerViewModel

    private val loadCount = MutableStateFlow(0L)
    private val currentMediaId = MutableStateFlow<String?>(null)
    private val playbackEnded = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    private val playbackStarted = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    /** When false, loads never start playing (e.g. YouTube refusing the stream with HTTP 403). */
    private var loadsStartPlaying = true
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
        every { mediaRepository.playbackEnded } returns playbackEnded
        every { mediaRepository.playbackStarted } returns playbackStarted
        every { mediaRepository.stop() } answers { currentMediaId.value = null }
        every { mediaRepository.loadMediaSource(any(), any()) } answers {
            loadCount.value++
            val id: String = secondArg()
            currentMediaId.value = id
            // ExoPlayer reports isPlaying later through its listener, not during the load call.
            if (loadsStartPlaying) CoroutineScope(mainDispatcherRule.dispatcher).launch { playbackStarted.emit(id) }
        }

        serverRepository = ServerRepository()
        watchHistoryStore = WatchHistoryStore({ historyPrefs }, mainDispatcherRule.dispatcher)
        viewModel = YouTubePlayerViewModel(youTubeRepository, mediaRepository, serverRepository, watchHistoryStore)
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

        // Let the resolve finish: a Dispatchers.Default hop still in flight when the test ends
        // resumes onto the reset Main dispatcher and fails the next test.
        awaitResolved()
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

    @Test
    fun `video ending autoplays the first suggestion`() = runTest {
        advanceUntilIdle()
        viewModel.play(youTubeItem("abc"))
        awaitResolved()

        playbackEnded.emit("abc")
        advanceUntilIdle()
        val state = awaitResolved()

        assertEquals("next1", state.item?.youtubeVideoId)
        verify(exactly = 1) { mediaRepository.loadMediaSource(mediaSource, "next1") }
        assertTrue(serverRepository.isPlayerActive.value)
    }

    @Test
    fun `autoplay skips videos already watched this session`() = runTest {
        coEvery { youTubeRepository.getSuggestions("next1") } returns
            listOf(youTubeItem("abc"), youTubeItem("next1b"))
        advanceUntilIdle()
        viewModel.play(youTubeItem("abc"))
        awaitResolved()
        playbackEnded.emit("abc")
        advanceUntilIdle()
        assertEquals("next1", awaitResolved().item?.youtubeVideoId)

        playbackEnded.emit("next1")
        advanceUntilIdle()
        assertEquals("next1b", awaitResolved().item?.youtubeVideoId)
    }

    @Test
    fun `autoplay stops when every suggestion was already watched`() = runTest {
        // A suggests only B and B suggests only A: autoplay must not bounce between them forever.
        coEvery { youTubeRepository.getSuggestions("a") } returns listOf(youTubeItem("b"))
        coEvery { youTubeRepository.getSuggestions("b") } returns listOf(youTubeItem("a"))
        advanceUntilIdle()
        viewModel.play(youTubeItem("a"))
        awaitResolved()
        playbackEnded.emit("a")
        advanceUntilIdle()
        assertEquals("b", awaitResolved().item?.youtubeVideoId)

        playbackEnded.emit("b")
        advanceUntilIdle()

        assertEquals("b", viewModel.uiState.value.item?.youtubeVideoId)
        coVerify(exactly = 1) { youTubeRepository.resolvePlaybackSource("a") }
        coVerify(exactly = 1) { youTubeRepository.resolvePlaybackSource("b") }
        assertEquals(listOf("b", "a"), watchHistoryStore.history.value.map { it.youtubeVideoId })
    }

    @Test
    fun `other media ending does not autoplay`() = runTest {
        advanceUntilIdle()
        viewModel.play(youTubeItem("abc"))
        awaitResolved()

        playbackEnded.emit("upload_1")
        playbackEnded.emit(null)
        advanceUntilIdle()

        assertEquals("abc", viewModel.uiState.value.item?.youtubeVideoId)
        coVerify(exactly = 1) { youTubeRepository.resolvePlaybackSource(any()) }
    }

    @Test
    fun `video ending with no suggestions stays on the video`() = runTest {
        coEvery { youTubeRepository.getSuggestions("abc") } returns emptyList()
        advanceUntilIdle()
        viewModel.play(youTubeItem("abc"))
        awaitResolved()

        playbackEnded.emit("abc")
        advanceUntilIdle()

        assertEquals("abc", viewModel.uiState.value.item?.youtubeVideoId)
        coVerify(exactly = 1) { youTubeRepository.resolvePlaybackSource(any()) }
    }

    @Test
    fun `video ending before suggestions arrive waits for them`() = runTest {
        val suggestions = CompletableDeferred<List<MediaItemModel>>()
        coEvery { youTubeRepository.getSuggestions("abc") } coAnswers { suggestions.await() }
        advanceUntilIdle()
        viewModel.play(youTubeItem("abc"))
        awaitResolved()

        playbackEnded.emit("abc")
        advanceUntilIdle()
        assertEquals("abc", viewModel.uiState.value.item?.youtubeVideoId)

        suggestions.complete(listOf(youTubeItem("late1")))
        advanceUntilIdle()
        assertEquals("late1", awaitResolved().item?.youtubeVideoId)
    }

    @Test
    fun `playing a video adds it to the watch history`() = runTest {
        advanceUntilIdle()
        viewModel.play(youTubeItem("abc", "Lofi Girl"))
        awaitResolved()

        assertEquals(listOf("abc"), watchHistoryStore.history.value.map { it.youtubeVideoId })
        assertEquals("Lofi Girl", watchHistoryStore.history.value.single().title)
    }

    @Test
    fun `video that fails to resolve is not added to the history`() = runTest {
        coEvery { youTubeRepository.resolvePlaybackSource("abc") } throws RuntimeException("fail")
        advanceUntilIdle()
        viewModel.play(youTubeItem("abc"))
        awaitResolved()

        assertTrue(watchHistoryStore.history.value.isEmpty())
    }

    @Test
    fun `ui state shows the history without the current video`() = runTest {
        watchHistoryStore.record(youTubeItem("old1"))
        watchHistoryStore.record(youTubeItem("abc"))
        watchHistoryStore.record(youTubeItem("old2"))
        advanceUntilIdle()

        viewModel.play(youTubeItem("abc"))
        assertEquals(listOf("old2", "old1"), viewModel.uiState.value.history.map { it.youtubeVideoId })

        val state = awaitResolved()
        assertEquals(listOf("old2", "old1"), state.history.map { it.youtubeVideoId })
    }

    @Test
    fun `history updates as autoplay moves to the next video`() = runTest {
        advanceUntilIdle()
        viewModel.play(youTubeItem("abc"))
        awaitResolved()
        assertTrue(viewModel.uiState.value.history.isEmpty())

        playbackEnded.emit("abc")
        advanceUntilIdle()
        val state = awaitResolved()

        assertEquals("next1", state.item?.youtubeVideoId)
        assertEquals(listOf("abc"), state.history.map { it.youtubeVideoId })
        assertEquals(listOf("next1", "abc"), watchHistoryStore.history.value.map { it.youtubeVideoId })
    }

    @Test
    fun `stored history is loaded in the background after creation`() = runTest {
        WatchHistoryStore({ historyPrefs }).record(youTubeItem("old"))
        var reads = 0
        val store = WatchHistoryStore({ reads++; historyPrefs }, mainDispatcherRule.dispatcher)

        viewModel = YouTubePlayerViewModel(youTubeRepository, mediaRepository, serverRepository, store)
        assertEquals("creating the view model must not read prefs", 0, reads)

        advanceUntilIdle()
        assertEquals(1, reads)
        assertEquals(listOf("old"), store.history.value.map { it.youtubeVideoId })

        viewModel.play(youTubeItem("abc"))
        assertEquals(listOf("old"), viewModel.uiState.value.history.map { it.youtubeVideoId })
        awaitResolved()
    }

    @Test
    fun `video that loads but never starts playing is not added to the history`() = runTest {
        loadsStartPlaying = false
        advanceUntilIdle()
        viewModel.play(youTubeItem("abc"))
        awaitResolved()
        verify(exactly = 1) { mediaRepository.loadMediaSource(mediaSource, "abc") }
        assertTrue(watchHistoryStore.history.value.isEmpty())

        playbackStarted.emit("other") // another video's start doesn't count
        advanceUntilIdle()
        assertTrue(watchHistoryStore.history.value.isEmpty())

        playbackStarted.emit("abc")
        advanceUntilIdle()
        assertEquals(listOf("abc"), watchHistoryStore.history.value.map { it.youtubeVideoId })
    }

    @Test
    fun `video replaced before it starts playing is not added to the history`() = runTest {
        loadsStartPlaying = false
        advanceUntilIdle()
        viewModel.play(youTubeItem("abc"))
        awaitResolved()

        viewModel.play(youTubeItem("def"))
        awaitResolved()
        playbackStarted.emit("abc") // a late start from the first video
        advanceUntilIdle()
        assertTrue(watchHistoryStore.history.value.isEmpty())

        playbackStarted.emit("def")
        advanceUntilIdle()
        assertEquals(listOf("def"), watchHistoryStore.history.value.map { it.youtubeVideoId })
    }

    @Test
    fun `isOwnTitle matches only the video the screen is showing`() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.isOwnTitle("Video abc"))

        viewModel.play(youTubeItem("abc"))
        awaitResolved()
        assertTrue(viewModel.isOwnTitle("Video abc"))
        assertFalse(viewModel.isOwnTitle("Phone cast"))

        viewModel.close()
        advanceUntilIdle()
        assertFalse(viewModel.isOwnTitle("Video abc"))
    }

    @Test
    fun `autoplay opens the player under a title the screen owns`() = runTest {
        advanceUntilIdle()
        viewModel.play(youTubeItem("abc"))
        awaitResolved()

        playbackEnded.emit("abc")
        advanceUntilIdle()
        awaitResolved()

        // MainActivity keeps a minimised player minimised for exactly this title change.
        assertEquals("Video next1", serverRepository.activeMediaTitle.value)
        assertTrue(viewModel.isOwnTitle(serverRepository.activeMediaTitle.value))
        assertTrue(viewModel.isOwnLoad(loadCount.value))
        assertFalse(viewModel.isOwnTitle("Video abc"))
    }
}
