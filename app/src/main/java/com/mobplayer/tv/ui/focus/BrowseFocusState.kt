package com.mobplayer.tv.ui.focus

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay

/**
 * Where the user was on the browse screens (Home / Search / My List). The browse UI leaves
 * composition while the player is showing, so scroll positions and the focused item live here,
 * above it, and are restored when the user comes back.
 */
@Stable
class BrowseFocusState {
    private val columnStates = mutableMapOf<String, LazyListState>()
    private val rowStates = mutableMapOf<String, LazyListState>()

    /** Vertical scroll of a tab's page. */
    fun columnState(tab: String): LazyListState = columnStates.getOrPut(tab) { LazyListState() }

    /** Horizontal scroll of a rail, by rail id. */
    fun rowState(railId: String): LazyListState = rowStates.getOrPut(railId) { LazyListState() }

    /** Key of the item that started playback; refocused once when the browse screen returns. */
    var lastClickedKey: String? = null
        private set
    var isRestorePending: Boolean = false
        private set

    fun onItemClicked(key: String) {
        lastClickedKey = key
        isRestorePending = true
    }

    /** Called by the item that regained focus, or when restoring no longer makes sense. */
    fun consumeRestore() {
        isRestorePending = false
    }
}

val LocalBrowseFocus = staticCompositionLocalOf { BrowseFocusState() }

/**
 * Marks a focusable as restorable under [key]: when it is (re)composed while a restore for that
 * key is pending, it takes focus. Must come before the element's own `focusable()`.
 */
fun Modifier.restoreFocus(key: String): Modifier = composed {
    val state = LocalBrowseFocus.current
    val requester = remember { FocusRequester() }
    LaunchedEffect(key) {
        if (state.isRestorePending && state.lastClickedKey == key) {
            // Let the page settle (lazy layout + screen transition) before moving focus.
            delay(RESTORE_DELAY_MS)
            if (runCatching { requester.requestFocus() }.isSuccess) state.consumeRestore()
        }
    }
    focusRequester(requester)
}

internal const val RESTORE_DELAY_MS = 150L
