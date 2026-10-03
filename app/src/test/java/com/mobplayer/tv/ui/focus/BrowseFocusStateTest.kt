package com.mobplayer.tv.ui.focus

import org.junit.Assert.*
import org.junit.Test

class BrowseFocusStateTest {

    @Test
    fun `column and row states are remembered per key`() {
        val state = BrowseFocusState()
        assertSame(state.columnState("home"), state.columnState("home"))
        assertNotSame(state.columnState("home"), state.columnState("search"))
        assertSame(state.rowState("rail_1"), state.rowState("rail_1"))
        assertNotSame(state.rowState("rail_1"), state.rowState("rail_2"))
        // Column and row maps are separate even for the same key
        assertNotSame(state.columnState("x"), state.rowState("x"))
    }

    @Test
    fun `clicking an item schedules a focus restore until consumed`() {
        val state = BrowseFocusState()
        assertNull(state.lastClickedKey)
        assertFalse(state.isRestorePending)

        state.onItemClicked("yt_TRENDING_abc")
        assertEquals("yt_TRENDING_abc", state.lastClickedKey)
        assertTrue(state.isRestorePending)

        state.consumeRestore()
        assertFalse(state.isRestorePending)
        assertEquals("yt_TRENDING_abc", state.lastClickedKey)
    }
}
