package com.mobplayer.tv.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobplayer.tv.R
import com.mobplayer.tv.models.RemoteActionEvent
import com.mobplayer.tv.models.RemoteIconType
import com.mobplayer.tv.ui.theme.TvColors
import com.mobplayer.tv.viewmodel.ServerEventBus

@Composable
fun TvRemoteActionHud(
    modifier: Modifier = Modifier
) {
    val currentEvent by ServerEventBus.remoteActionEvent.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 28.dp, end = 48.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        AnimatedVisibility(
            visible = currentEvent != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            currentEvent?.let { event ->
                RemoteActionCard(event = event)
            }
        }
    }
}

@Composable
private fun RemoteActionCard(event: RemoteActionEvent) {
    val (icon, iconTint, bgGradient) = getIconAttributes(event.iconType)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .background(TvColors.SurfaceElevated.copy(alpha = 0.95f))
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.5f),
                        TvColors.SecondaryAccent.copy(alpha = 0.6f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        // Glowing Icon Circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(bgGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = event.displayName,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        // Action Text & Subtitle
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = event.displayName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.remote_badge),
                        color = TvColors.SecondaryAccent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            event.details?.let { detail ->
                Text(
                    text = detail,
                    color = TvColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun getIconAttributes(type: RemoteIconType): Triple<ImageVector, Color, Brush> {
    return when (type) {
        RemoteIconType.PLAY -> Triple(
            Icons.Default.PlayArrow,
            Color.White,
            Brush.linearGradient(listOf(TvColors.HudPlayStart, TvColors.HudPlayEnd))
        )
        RemoteIconType.PAUSE -> Triple(
            Icons.Default.Pause,
            Color.White,
            Brush.linearGradient(listOf(TvColors.HudPauseStart, TvColors.HudPauseEnd))
        )
        RemoteIconType.VOLUME_UP -> Triple(
            Icons.AutoMirrored.Filled.VolumeUp,
            Color.White,
            Brush.linearGradient(listOf(TvColors.HudVolumeUpStart, TvColors.HudVolumeUpEnd))
        )
        RemoteIconType.VOLUME_DOWN -> Triple(
            Icons.AutoMirrored.Filled.VolumeDown,
            Color.White,
            Brush.linearGradient(listOf(TvColors.HudVolumeDownStart, TvColors.HudVolumeDownEnd))
        )
        RemoteIconType.MUTE -> Triple(
            Icons.AutoMirrored.Filled.VolumeOff,
            Color.White,
            Brush.linearGradient(listOf(TvColors.HudMuteStart, TvColors.HudMuteEnd))
        )
        RemoteIconType.SEEK_FORWARD -> Triple(
            Icons.Default.FastForward,
            Color.White,
            Brush.linearGradient(listOf(TvColors.HudSeekStart, TvColors.HudSeekEnd))
        )
        RemoteIconType.SEEK_BACKWARD -> Triple(
            Icons.Default.FastRewind,
            Color.White,
            Brush.linearGradient(listOf(TvColors.HudSeekStart, TvColors.HudSeekEnd))
        )
        RemoteIconType.DPAD_UP -> Triple(
            Icons.Default.ArrowUpward,
            Color.White,
            Brush.linearGradient(listOf(TvColors.HudDpadStart, TvColors.HudDpadEnd))
        )
        RemoteIconType.DPAD_DOWN -> Triple(
            Icons.Default.ArrowDownward,
            Color.White,
            Brush.linearGradient(listOf(TvColors.HudDpadStart, TvColors.HudDpadEnd))
        )
        RemoteIconType.DPAD_LEFT -> Triple(
            Icons.AutoMirrored.Filled.ArrowBack,
            Color.White,
            Brush.linearGradient(listOf(TvColors.HudDpadStart, TvColors.HudDpadEnd))
        )
        RemoteIconType.DPAD_RIGHT -> Triple(
            Icons.AutoMirrored.Filled.ArrowForward,
            Color.White,
            Brush.linearGradient(listOf(TvColors.HudDpadStart, TvColors.HudDpadEnd))
        )
        RemoteIconType.SELECT -> Triple(
            Icons.Default.CheckCircle,
            Color.White,
            Brush.linearGradient(listOf(TvColors.HudSelectStart, TvColors.HudSelectEnd))
        )
        RemoteIconType.BACK -> Triple(
            Icons.AutoMirrored.Filled.ArrowBack,
            Color.White,
            Brush.linearGradient(listOf(TvColors.HudBackStart, TvColors.HudBackEnd))
        )
        RemoteIconType.HOME -> Triple(
            Icons.Default.Home,
            Color.White,
            Brush.linearGradient(listOf(TvColors.HudHomeStart, TvColors.HudHomeEnd))
        )
        RemoteIconType.MEDIA -> Triple(
            Icons.Default.Movie,
            Color.White,
            Brush.linearGradient(listOf(TvColors.HudMediaStart, TvColors.HudMediaEnd))
        )
        RemoteIconType.INFO -> Triple(
            Icons.Default.Info,
            Color.White,
            Brush.linearGradient(listOf(TvColors.HudInfoStart, TvColors.HudInfoEnd))
        )
    }
}
