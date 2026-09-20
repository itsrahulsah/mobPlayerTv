package com.mobplayer.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mobplayer.tv.R
import com.mobplayer.tv.data.models.MediaItemModel
import com.mobplayer.tv.ui.theme.TvColors

@Composable
fun TvHeroBillboard(
    item: MediaItemModel,
    onPlayClick: () -> Unit,
    onWatchlistClick: () -> Unit = {},
    onDetailsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp)
    ) {
        // 1. Background Image with fallback gradient
        AsyncImage(
            model = item.backdropUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = item.gradientColors.map { Color(it) }
                    )
                )
        )

        // 2. Gradient Scrims: Left-to-Right and Top-to-Bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            TvColors.BackgroundDark,
                            TvColors.BackgroundDark.copy(alpha = 0.85f),
                            TvColors.BackgroundDark.copy(alpha = 0.3f),
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = 1400f
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            TvColors.BackgroundDark.copy(alpha = 0.4f),
                            TvColors.BackgroundDark
                        )
                    )
                )
        )

        // 3. Featured Content Details Overlay
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.58f)
                .padding(start = 48.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Badges & Rating Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top Tag / Premiere Badge
                item.badge?.let { badgeText ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(TvColors.PrimaryAccent)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // IMDb Rating
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = stringResource(R.string.rating_desc),
                        tint = TvColors.GoldAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = item.rating,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Release Year & Duration
                Text(
                    text = "${item.year} • ${item.duration}",
                    color = TvColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                // Age Rating Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .border(1.dp, TvColors.TextTertiary, RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = item.contentRating,
                        color = TvColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Movie / Show Title
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 42.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Genre Chips
            Text(
                text = item.genres.joinToString(" • "),
                color = TvColors.SecondaryAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Overview / Synopsis
            Text(
                text = item.description,
                color = TvColors.TextSecondary,
                fontSize = 14.sp,
                maxLines = 3,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons Row (TV Focusable)
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvActionButton(
                    icon = Icons.Default.PlayArrow,
                    label = stringResource(R.string.btn_watch_now),
                    isPrimary = true,
                    onClick = onPlayClick
                )

                TvActionButton(
                    icon = Icons.Default.Add,
                    label = stringResource(R.string.btn_my_list),
                    isPrimary = false,
                    onClick = onWatchlistClick
                )

                TvActionButton(
                    icon = Icons.Default.Info,
                    label = stringResource(R.string.btn_details),
                    isPrimary = false,
                    onClick = onDetailsClick
                )
            }
        }
    }
}

@Composable
fun TvActionButton(
    icon: ImageVector,
    label: String,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1f, label = "btnScale")

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isPrimary -> TvColors.PrimaryAccent
            else -> TvColors.SurfaceElevated.copy(alpha = 0.85f)
        },
        label = "btnBg"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.Black
            else -> Color.White
        },
        label = "btnTextColor"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(
                width = if (isFocused) 2.dp else if (!isPrimary) 1.dp else 0.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 11.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = textColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
