package com.mobplayer.tv.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object TvColors {
    val BackgroundDark = Color(0xFF090A0F)
    val SurfaceDark = Color(0xFF13151F)
    val SurfaceElevated = Color(0xFF1E2235)
    val CardBackground = Color(0xFF161926)

    // Pairing Screen Radial Gradient Colors
    val BackgroundRadialCenter = Color(0xFF1A1D2E)
    val BackgroundRadialEdge = Color(0xFF040508)
    
    val PrimaryAccent = Color(0xFFE50914) // Netflix / Cinema Red
    val PrimaryAccentDark = Color(0xFFB71C1C)
    val PrimaryAccentDeep = Color(0xFF990000)
    val SecondaryAccent = Color(0xFF00E5FF) // Cyber Cyan
    val GoldAccent = Color(0xFFFFB300) // Star / Rating Gold
    val LiveRed = Color(0xFFFF1744) // Live TV indicator
    val ConnectedGreen = Color(0xFF00E676) // Active connection status
    
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB0B7C3)
    val TextTertiary = Color(0xFF717B90)
    
    val FocusBorder = Color(0xFFFFFFFF)
    val FocusGlow = Color(0x6600E5FF)

    // Remote HUD Gradient Colors
    val HudPlayStart = Color(0xFF00C853)
    val HudPlayEnd = Color(0xFF1B5E20)
    val HudPauseStart = Color(0xFFFF9100)
    val HudPauseEnd = Color(0xFFE65100)
    val HudVolumeUpStart = Color(0xFF00E5FF)
    val HudVolumeUpEnd = Color(0xFF0091EA)
    val HudVolumeDownStart = Color(0xFF00B0FF)
    val HudVolumeDownEnd = Color(0xFF0277BD)
    val HudMuteStart = Color(0xFFFF1744)
    val HudMuteEnd = Color(0xFFB71C1C)
    val HudSeekStart = Color(0xFF7C4DFF)
    val HudSeekEnd = Color(0xFF6200EA)
    val HudDpadStart = Color(0xFF2979FF)
    val HudDpadEnd = Color(0xFF1565C0)
    val HudSelectStart = Color(0xFF00E676)
    val HudSelectEnd = Color(0xFF00B248)
    val HudBackStart = Color(0xFFFF5252)
    val HudBackEnd = Color(0xFFD50000)
    val HudHomeStart = Color(0xFFFF4081)
    val HudHomeEnd = Color(0xFFC51162)
    val HudMediaStart = Color(0xFFE040FB)
    val HudMediaEnd = Color(0xFFAA00FF)
    val HudInfoStart = Color(0xFF00E5FF)
    val HudInfoEnd = Color(0xFF0091EA)

    val BillboardGradientVertical = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            BackgroundDark.copy(alpha = 0.5f),
            BackgroundDark
        )
    )

    val BillboardGradientHorizontal = Brush.horizontalGradient(
        colors = listOf(
            BackgroundDark.copy(alpha = 0.95f),
            BackgroundDark.copy(alpha = 0.75f),
            Color.Transparent
        )
    )
}
