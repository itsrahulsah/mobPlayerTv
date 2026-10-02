package com.mobplayer.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Full-screen, see-through overlay above whatever is running (home, search, player).
 *
 * Hosted in its own dialog window so D-pad focus, touches and Back can't reach the screen
 * underneath (Compose 1.6 has no stable way to trap focus inside part of one window), while the
 * running screen, including a playing video, stays visible through the scrim.
 */
@Composable
fun TvModalOverlay(
    onBack: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Dialog(
        onDismissRequest = { onBack?.invoke() },
        properties = DialogProperties(
            dismissOnBackPress = onBack != null,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // We draw our own scrim; drop the platform dim so the background isn't darkened twice.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.55f)),
                        radius = 1400f
                    )
                ),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}
