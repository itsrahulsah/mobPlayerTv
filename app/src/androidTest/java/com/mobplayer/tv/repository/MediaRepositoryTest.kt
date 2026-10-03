package com.mobplayer.tv.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against a real ExoPlayer, which must be used from the main thread. */
@RunWith(AndroidJUnit4::class)
class MediaRepositoryTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var repository: MediaRepository
    private val owner = Any()

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    @Before
    fun setup() {
        repository = onMain { MediaRepository(instrumentation.targetContext) }
    }

    @After
    fun tearDown() {
        onMain {
            repository.attach(owner)
            repository.release(owner)
        }
    }

    @Test
    fun commandsWithoutAnAttachedScreenDoNotCreateThePlayer() = onMain {
        repository.setVolume(0.5f)
        repository.loadMedia("file:///nonexistent.mp4", videoId = "vid_1")

        assertNull(repository.player)
        // The load is still counted, so a YouTube screen can tell it was replaced
        assertEquals(1L, repository.loadCount.value)
        assertEquals("vid_1", repository.currentMediaId.value)
    }

    @Test
    fun initializeIsIdempotent() = onMain {
        val first = repository.initialize()
        assertSame(first, repository.initialize())
        assertSame(first, repository.player)
    }

    @Test
    fun loadMediaCreatesPlayerForAttachedScreen() = onMain {
        repository.attach(owner)
        repository.loadMedia("file:///nonexistent.mkv", startPositionMs = 0L, videoId = "vid_2")

        val player = repository.player
        assertNotNull(player)
        assertEquals(1, player!!.mediaItemCount)
        assertEquals("video/x-matroska", player.currentMediaItem?.localConfiguration?.mimeType)
        assertEquals("vid_2", repository.activePlayingVideoId)
        assertTrue(player.playWhenReady)
    }

    @Test
    fun explicitMimeTypeOverridesUrlExtension() = onMain {
        repository.attach(owner)
        repository.loadMedia("http://example.com/stream", mimeType = "application/x-mpegURL")
        assertEquals("application/x-mpegURL", repository.player!!.currentMediaItem?.localConfiguration?.mimeType)
    }

    @Test
    fun setVolumeIsClampedAndPublished() = onMain {
        repository.attach(owner)
        repository.setVolume(1.7f)
        assertEquals(1f, repository.player!!.volume, 0f)
        assertEquals(1f, repository.playerStateFlow.value!!.volume, 0f)

        repository.setVolume(-1f)
        assertEquals(0f, repository.player!!.volume, 0f)
    }

    @Test
    fun stopClearsMediaAndCurrentId() = onMain {
        repository.attach(owner)
        repository.loadMedia("file:///nonexistent.mp4", videoId = "vid_3")

        repository.stop()

        assertNull(repository.currentMediaId.value)
        assertEquals(0, repository.player!!.mediaItemCount)
        assertNotNull(repository.playerStateFlow.value)
    }

    @Test
    fun loadCountIncrementsOnEveryLoad() = onMain {
        repository.attach(owner)
        repository.loadMedia("file:///a.mp4")
        repository.loadMedia("file:///b.mp4")
        assertEquals(2L, repository.loadCount.value)
        assertNull(repository.currentMediaId.value)
    }

    @Test
    fun releaseByAnotherOwnerIsIgnored() = onMain {
        repository.attach(owner)
        repository.initialize()

        repository.release(Any())
        assertNotNull(repository.player)

        repository.release(owner)
        assertNull(repository.player)
    }

    @Test
    fun newerScreenKeepsPlayerWhenOldScreenReleases() = onMain {
        val oldScreen = Any()
        repository.attach(oldScreen)
        repository.initialize()
        repository.attach(owner) // quick relaunch attaches before the old screen is cleared

        repository.release(oldScreen)
        assertNotNull(repository.player)
    }
}
