package com.mobplayer.tv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobplayer.tv.R
import com.mobplayer.tv.ui.theme.TvColors

/**
 * Full-screen loader shown after the system splash while the app finishes starting (control
 * server coming up, first PIN). Same logo as the splash icon so the hand-off looks continuous.
 */
@Composable
fun TvStartupLoader(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "StartupPulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "StartupPulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvColors.BackgroundDark),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Same artwork as the splash icon (it carries the wordmark). Full-resolution nodpi
            // image so it's only ever scaled down; the launcher mipmap is too small at this size.
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = stringResource(R.string.app_logo_description),
                modifier = Modifier
                    .scale(pulse)
                    .size(220.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            LinearProgressIndicator(
                modifier = Modifier
                    .width(220.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = TvColors.PrimaryAccent,
                trackColor = TvColors.SurfaceElevated
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.starting_up),
                color = TvColors.TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}
