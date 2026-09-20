package com.mobplayer.tv.ui.pairing

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobplayer.tv.R
import com.mobplayer.tv.ui.theme.TvColors

@Composable
fun TvPairingScreen(
    pin: String,
    serverIp: String? = null,
    port: Int = 8080,
    isEmulator: Boolean = false,
    onEnterDemoMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pulse animation for the connection indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        TvColors.BackgroundRadialCenter,
                        TvColors.BackgroundDark,
                        TvColors.BackgroundRadialEdge
                    ),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .width(620.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(TvColors.SurfaceDark.copy(alpha = 0.92f))
                .border(1.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .shadow(24.dp, RoundedCornerShape(24.dp))
                .padding(horizontal = 36.dp, vertical = 24.dp)
        ) {
            // Pulsing TV Icon
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(TvColors.PrimaryAccent, TvColors.PrimaryAccentDeep)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = stringResource(R.string.app_name),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.pairing_title),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.pairing_subtitle),
                color = TvColors.TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // PIN Display Card
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .border(2.dp, TvColors.SecondaryAccent.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .padding(horizontal = 32.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = pin.map { "$it " }.joinToString("").trimEnd(),
                    color = TvColors.SecondaryAccent,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 8.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Wi-Fi / Discovery Information
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = stringResource(R.string.pairing_wifi_desc),
                    tint = TvColors.ConnectedGreen,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isEmulator) stringResource(R.string.tv_server_emulator, port)
                           else if (!serverIp.isNullOrBlank()) stringResource(R.string.tv_server_ip, serverIp, port)
                           else stringResource(R.string.tv_server_port, port),
                    color = TvColors.TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (isEmulator) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TvColors.SecondaryAccent.copy(alpha = 0.12f))
                        .border(1.dp, TvColors.SecondaryAccent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.emulator_setup_title),
                        color = TvColors.SecondaryAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.emulator_setup_instructions, port, port, port),
                        color = TvColors.TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Demo Mode / Preview Button
            var isDemoFocused by remember { mutableStateOf(false) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .scale(if (isDemoFocused) 1.06f else 1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDemoFocused) Color.White else TvColors.SurfaceElevated)
                    .border(
                        width = if (isDemoFocused) 2.dp else 1.dp,
                        color = if (isDemoFocused) Color.White else Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .onFocusChanged { isDemoFocused = it.isFocused }
                    .focusable()
                    .clickable { onEnterDemoMode() }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.demo_mode_desc),
                    tint = if (isDemoFocused) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.preview_tv_home),
                    color = if (isDemoFocused) Color.Black else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
