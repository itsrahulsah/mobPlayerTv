package com.mobplayer.tv.ui

import org.junit.Assert.*
import org.junit.Test

class PlayerRestoreTest {

    private val playing = PlayerSnapshot(isActive = true, title = "Lofi Girl", loadCount = 3)

    private fun restore(
        current: PlayerSnapshot,
        previous: PlayerSnapshot = playing,
        isYouTubeScreenLoad: Boolean = false,
        isYouTubeScreenTitle: Boolean = false
    ) = shouldRestoreMinimizedPlayer(previous, current, isYouTubeScreenLoad, isYouTubeScreenTitle)

    @Test
    fun `nothing changed keeps the player minimised`() {
        assertFalse(restore(playing))
    }

    @Test
    fun `player opened or closed restores it`() {
        assertTrue(restore(PlayerSnapshot(true, "Lofi Girl", 3), previous = PlayerSnapshot(false, "", 3)))
        assertTrue(restore(PlayerSnapshot(false, "", 3)))
    }

    @Test
    fun `other media cast from the phone restores it`() {
        assertTrue(restore(playing.copy(title = "Phone cast", loadCount = 4)))
    }

    @Test
    fun `new load reusing the title restores it`() {
        assertTrue(restore(playing.copy(loadCount = 4)))
    }

    @Test
    fun `YouTube screen's own stream resolving keeps it minimised`() {
        assertFalse(restore(playing.copy(loadCount = 4), isYouTubeScreenLoad = true, isYouTubeScreenTitle = true))
    }

    @Test
    fun `autoplay moving to the next video keeps it minimised`() {
        // Title switches as play(next) opens the player, then the next stream loads as our own.
        val titled = playing.copy(title = "Next video")
        assertFalse(restore(titled, isYouTubeScreenTitle = true))
        assertFalse(restore(titled.copy(loadCount = 4), previous = titled, isYouTubeScreenLoad = true, isYouTubeScreenTitle = true))
    }

    @Test
    fun `new title that isn't the YouTube screen's restores it`() {
        assertTrue(restore(playing.copy(title = "Uploaded video")))
    }
}
