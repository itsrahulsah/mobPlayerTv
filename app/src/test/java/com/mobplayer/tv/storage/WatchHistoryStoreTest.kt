package com.mobplayer.tv.storage

import com.mobplayer.tv.testutil.FakeSharedPreferences
import com.mobplayer.tv.testutil.youTubeItem
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WatchHistoryStoreTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var store: WatchHistoryStore

    @Before
    fun setup() {
        prefs = FakeSharedPreferences()
        store = WatchHistoryStore(prefs)
    }

    private fun ids() = store.history.value.map { it.youtubeVideoId }

    @Test
    fun `starts empty`() {
        assertTrue(store.history.value.isEmpty())
    }

    @Test
    fun `record puts the newest video first`() {
        store.record(youTubeItem("a"))
        store.record(youTubeItem("b"))
        assertEquals(listOf("b", "a"), ids())
    }

    @Test
    fun `replaying a video moves it to the front without duplicating it`() {
        store.record(youTubeItem("a"))
        store.record(youTubeItem("b"))
        store.record(youTubeItem("a"))
        assertEquals(listOf("a", "b"), ids())
    }

    @Test
    fun `items without a YouTube id are ignored`() {
        store.record(youTubeItem("a").copy(youtubeVideoId = null))
        assertTrue(store.history.value.isEmpty())
    }

    @Test
    fun `history is capped at the most recent entries`() {
        (1..WatchHistoryStore.MAX_ENTRIES + 5).forEach { store.record(youTubeItem("v$it")) }
        assertEquals(WatchHistoryStore.MAX_ENTRIES, store.history.value.size)
        assertEquals("v${WatchHistoryStore.MAX_ENTRIES + 5}", ids().first())
        assertFalse("v1" in ids())
    }

    @Test
    fun `history survives a restart with its display fields`() {
        store.record(
            youTubeItem("a", "Lofi Girl").copy(
                subtitle = "Lofi Records",
                posterUrl = "https://i.ytimg.com/a.jpg",
                backdropUrl = "https://i.ytimg.com/a_hq.jpg",
                duration = "3:21",
                isLive = true
            )
        )

        val restored = WatchHistoryStore(prefs).history.value.single()
        assertEquals("a", restored.youtubeVideoId)
        assertEquals("Lofi Girl", restored.title)
        assertEquals("Lofi Records", restored.subtitle)
        assertEquals("https://i.ytimg.com/a.jpg", restored.posterUrl)
        assertEquals("https://i.ytimg.com/a_hq.jpg", restored.backdropUrl)
        assertEquals("3:21", restored.duration)
        assertTrue(restored.isLive)
    }

    @Test
    fun `corrupt stored data starts an empty history`() {
        prefs.edit().putString(WatchHistoryStore.KEY_HISTORY, "not json").apply()
        assertTrue(WatchHistoryStore(prefs).history.value.isEmpty())
    }

    @Test
    fun `clear removes all entries`() {
        store.record(youTubeItem("a"))
        store.clear()
        assertTrue(store.history.value.isEmpty())
        assertTrue(WatchHistoryStore(prefs).history.value.isEmpty())
    }
}
