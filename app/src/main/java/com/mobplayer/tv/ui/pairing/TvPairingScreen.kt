package com.mobplayer.tv.ui.pairing

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobplayer.tv.R
import com.mobplayer.tv.ui.theme.TvColors
import com.mobplayer.tv.ui.icons.Wifi

@Composable
fun TvPairingScreen(
    pin: String,
    serverIp: String? = null,
    port: Int = 8080,
    isEmulator: Boolean = false,
    onEnterDemoMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    // "Waiting for your phone" indicator: a softly pulsing green dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val waitingPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waitingPulse"
    )

    val cardShape = RoundedCornerShape(20.dp)

    // Transparent: shown inside TvModalOverlay, so the running screen stays visible around the card
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .width(520.dp)
                // Shadow before the clip, or the clip cuts it off
                .shadow(20.dp, cardShape)
                .clip(cardShape)
                // See-through glass: the running screen shows behind, dimmed enough to keep text legible
                .background(
                    Brush.verticalGradient(
                        listOf(TvColors.SurfaceElevated.copy(alpha = 0.72f), TvColors.SurfaceDark.copy(alpha = 0.80f))
                    )
                )
                // Soft brand-red glow behind the logo
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(TvColors.PrimaryAccent.copy(alpha = 0.22f), Color.Transparent),
                            center = Offset(size.width / 2f, 0f),
                            radius = size.width * 0.45f
                        )
                    )
                }
                .border(1.dp, Color.White.copy(alpha = 0.10f), cardShape)
                .padding(horizontal = 28.dp, vertical = 18.dp)
        ) {
            // App logo (same lockup as the home top bar)
            Image(
                painter = painterResource(R.drawable.logo_wordmark),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .height(36.dp)
                    .aspectRatio(609f / 176f) // logo_wordmark is 609 x 176
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.pairing_title),
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.pairing_subtitle),
                color = TvColors.TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // PIN: one tile per digit so it reads at a glance from the sofa
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pin.forEach { digit ->
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 58.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(2.dp, TvColors.SecondaryAccent.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = digit.toString(),
                            color = TvColors.SecondaryAccent,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Live status: waiting for a phone to pair
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(waitingPulse)
                        .clip(CircleShape)
                        .background(TvColors.ConnectedGreen)
                )
                Text(
                    text = stringResource(R.string.pairing_waiting),
                    color = TvColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Server address (for entering it manually on the phone)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    // Solid fill: the card is see-through, so the address needs its own backdrop to stay legible
                    .background(TvColors.BackgroundDark)
                    .border(1.dp, TvColors.ConnectedGreen.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = stringResource(R.string.pairing_wifi_desc),
                    tint = TvColors.ConnectedGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (isEmulator) stringResource(R.string.tv_server_emulator, port)
                           else if (!serverIp.isNullOrBlank()) stringResource(R.string.tv_server_ip, serverIp, port)
                           else stringResource(R.string.tv_server_port, port),
                    color = Color.White,
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

            Spacer(modifier = Modifier.height(14.dp))

            // Demo Mode / Preview Button
            var isDemoFocused by remember { mutableStateOf(false) }
            // Only focusable element: take focus so OK works without a D-pad press first
            val demoFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { demoFocus.requestFocus() } }

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
                    .focusRequester(demoFocus)
                    .onFocusChanged { isDemoFocused = it.isFocused }
                    .focusable()
                    .clickable { onEnterDemoMode() }
                    .padding(horizontal = 20.dp, vertical = 9.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.demo_mode_desc),
                    tint = if (isDemoFocused) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.preview_tv_home),
                    color = if (isDemoFocused) Color.Black else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
