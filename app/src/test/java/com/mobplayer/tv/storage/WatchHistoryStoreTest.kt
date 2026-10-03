package com.mobplayer.tv.storage

import android.content.SharedPreferences
import com.mobplayer.tv.data.models.MediaItemModel
import com.mobplayer.tv.testutil.FakeSharedPreferences
import com.mobplayer.tv.testutil.youTubeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.util.Collections
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WatchHistoryStoreTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var store: WatchHistoryStore

    @Before
    fun setup() {
        prefs = FakeSharedPreferences()
        store = newStore()
    }

    private fun newStore() = WatchHistoryStore({ prefs }, Dispatchers.Unconfined)

    /** A store as the app sees it after startup: created, then loaded. */
    private fun loadedStore() = newStore().also { runBlocking { it.load() } }

    private fun WatchHistoryStore.recordNow(item: MediaItemModel) = runBlocking { record(item) }
    private fun WatchHistoryStore.clearNow() = runBlocking { clear() }

    private fun ids() = store.history.value.map { it.youtubeVideoId }

    @Test
    fun `starts empty`() {
        runBlocking { store.load() }
        assertTrue(store.history.value.isEmpty())
    }

    @Test
    fun `creating the store does not touch SharedPreferences`() {
        var reads = 0
        val lazyStore = WatchHistoryStore({ reads++; prefs }, Dispatchers.Unconfined)
        assertEquals(0, reads)

        runBlocking { lazyStore.load() }
        assertEquals(1, reads)
    }

    @Test
    fun `stored history appears only after load`() {
        store.recordNow(youTubeItem("a"))

        val restarted = newStore()
        assertTrue(restarted.history.value.isEmpty())
        runBlocking { restarted.load() }
        assertEquals(listOf("a"), restarted.history.value.map { it.youtubeVideoId })
    }

    @Test
    fun `record before load keeps the stored history`() {
        store.recordNow(youTubeItem("a"))

        val restarted = newStore()
        restarted.recordNow(youTubeItem("b"))
        assertEquals(listOf("b", "a"), restarted.history.value.map { it.youtubeVideoId })

        // A load finishing after the record must not replace it with the older stored list.
        runBlocking { restarted.load() }
        assertEquals(listOf("b", "a"), restarted.history.value.map { it.youtubeVideoId })
    }

    @Test
    fun `record puts the newest video first`() {
        store.recordNow(youTubeItem("a"))
        store.recordNow(youTubeItem("b"))
        assertEquals(listOf("b", "a"), ids())
    }

    @Test
    fun `replaying a video moves it to the front without duplicating it`() {
        store.recordNow(youTubeItem("a"))
        store.recordNow(youTubeItem("b"))
        store.recordNow(youTubeItem("a"))
        assertEquals(listOf("a", "b"), ids())
    }

    @Test
    fun `items without a YouTube id are ignored`() {
        store.recordNow(youTubeItem("a").copy(youtubeVideoId = null))
        assertTrue(store.history.value.isEmpty())
    }

    @Test
    fun `history is capped at the most recent entries`() {
        (1..WatchHistoryStore.MAX_ENTRIES + 5).forEach { store.recordNow(youTubeItem("v$it")) }
        assertEquals(WatchHistoryStore.MAX_ENTRIES, store.history.value.size)
        assertEquals("v${WatchHistoryStore.MAX_ENTRIES + 5}", ids().first())
        assertFalse("v1" in ids())
    }

    @Test
    fun `history survives a restart with its display fields`() {
        store.recordNow(
            youTubeItem("a", "Lofi Girl").copy(
                subtitle = "Lofi Records",
                posterUrl = "https://i.ytimg.com/a.jpg",
                backdropUrl = "https://i.ytimg.com/a_hq.jpg",
                duration = "3:21",
                isLive = true
            )
        )

        val restored = loadedStore().history.value.single()
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
        assertTrue(loadedStore().history.value.isEmpty())
    }

    @Test
    fun `clear removes all entries`() {
        store.recordNow(youTubeItem("a"))
        store.clearNow()
        assertTrue(store.history.value.isEmpty())
        assertTrue(loadedStore().history.value.isEmpty())
    }

    @Test
    fun `load, record and clear touch prefs only on the io dispatcher`() {
        val threads = Collections.synchronizedSet(mutableSetOf<String>())
        val tracked = object : SharedPreferences by prefs {
            override fun getString(key: String, defValue: String?): String? {
                threads += Thread.currentThread().name.substringBefore(" @") // debug mode appends the coroutine
                return prefs.getString(key, defValue)
            }

            override fun edit(): SharedPreferences.Editor {
                threads += Thread.currentThread().name.substringBefore(" @") // debug mode appends the coroutine
                return prefs.edit()
            }
        }
        val executor = Executors.newSingleThreadExecutor { Thread(it, "history-io") }
        try {
            val ioStore = WatchHistoryStore({ tracked }, executor.asCoroutineDispatcher())
            runBlocking {
                ioStore.load()
                ioStore.record(youTubeItem("a"))
                ioStore.clear()
            }
            assertEquals(setOf("history-io"), threads.toSet())
        } finally {
            executor.shutdown()
        }
    }
}
