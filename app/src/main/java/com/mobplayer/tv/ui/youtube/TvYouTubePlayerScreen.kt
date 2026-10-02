package com.mobplayer.tv.ui.youtube

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.mobplayer.tv.data.models.CardType
import com.mobplayer.tv.data.models.MediaItemModel
import com.mobplayer.tv.models.PlayerStatePayload
import com.mobplayer.tv.ui.components.TvMediaCard
import com.mobplayer.tv.ui.theme.TvColors
import com.mobplayer.tv.viewmodel.YouTubePlayerUiState
import kotlinx.coroutines.delay
import com.mobplayer.tv.ui.icons.Forward10
import com.mobplayer.tv.ui.icons.Pause
import com.mobplayer.tv.ui.icons.Replay10

private val YouTubeRed = Color(0xFFFF0000)
private const val SEEK_STEP_MS = 10_000L

/**
 * Fullscreen YouTube player: resolving/buffering/error states plus auto-hiding D-pad controls.
 * D-pad Down opens an "Up next" suggestions row; Up or Back closes it.
 */
@Composable
fun TvYouTubePlayerScreen(
    player: ExoPlayer,
    state: YouTubePlayerUiState,
    playerState: PlayerStatePayload?,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onPlaySuggestion: (MediaItemModel) -> Unit,
    /** Tells the host when the suggestions row opens/closes so remote Left/Right browse it instead of seeking. */
    onSuggestionsVisibleChange: (Boolean) -> Unit = {},
    /** Tells the host when the error panel shows so remote Left/Right/OK move between its buttons. */
    onErrorVisibleChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val item = state.item ?: return
    val playerStatus = rememberPlayerStatus(player)
    val isBuffering = playerStatus.isBuffering
    // A stale player error is irrelevant while a retry is re-resolving the stream.
    val error = state.error ?: playerStatus.error.takeUnless { state.isResolving }
    val currentOnErrorVisibleChange by rememberUpdatedState(onErrorVisibleChange)
    LaunchedEffect(error != null) { currentOnErrorVisibleChange(error != null) }
    DisposableEffect(Unit) { onDispose { currentOnErrorVisibleChange(false) } }
    val isPlaying = playerState?.isPlaying == true
    val position = playerState?.positionMs ?: 0L
    val duration = playerState?.durationMs ?: 0L

    var isOverlayVisible by remember { mutableStateOf(true) }
    var interactionTrigger by remember { mutableIntStateOf(0) }
    // Reset per video so picking a suggestion returns to the plain transport controls.
    var showSuggestions by remember(item.youtubeVideoId) { mutableStateOf(false) }
    val currentOnSuggestionsVisibleChange by rememberUpdatedState(onSuggestionsVisibleChange)
    LaunchedEffect(showSuggestions) { currentOnSuggestionsVisibleChange(showSuggestions) }
    DisposableEffect(Unit) { onDispose { currentOnSuggestionsVisibleChange(false) } }
    val rootFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }
    val suggestionsFocus = remember { FocusRequester() }
    val showOverlay = isOverlayVisible || showSuggestions || !isPlaying || state.isResolving || error != null

    fun seekBy(delta: Long) {
        val upper = if (duration > 0) duration else Long.MAX_VALUE
        onSeekTo((position + delta).coerceIn(0L, upper))
        interactionTrigger++
    }

    LaunchedEffect(isOverlayVisible, isPlaying, interactionTrigger, showSuggestions) {
        // Never auto-hide while the user is browsing suggestions.
        if (isOverlayVisible && isPlaying && !showSuggestions) {
            delay(5000)
            isOverlayVisible = false
        }
    }
    // Re-keyed on suggestions arriving: the loading placeholder holding focus gets replaced by cards.
    LaunchedEffect(showOverlay, showSuggestions, error, state.suggestions.isEmpty()) {
        // The error panel focuses its own Retry button.
        runCatching {
            when {
                error != null -> Unit
                showSuggestions -> suggestionsFocus.requestFocus()
                showOverlay -> playPauseFocus.requestFocus()
                else -> rootFocus.requestFocus()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // Compose 1.6 turns Back into "move focus to parent" (e.g. Retry -> this root) before
            // the activity sees it, so one press would only shift focus. Handle Back here.
            .onPreviewKeyEvent { event ->
                if (event.key != Key.Back) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyUp) {
                    // First Back only closes the suggestions row.
                    if (showSuggestions && error == null) showSuggestions = false else onBack()
                }
                true
            }
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || showOverlay) return@onKeyEvent false
                // Controls hidden: left/right seek, center toggles, anything else reveals controls.
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> seekBy(-SEEK_STEP_MS)
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seekBy(SEEK_STEP_MS)
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> onTogglePlay()
                    KeyEvent.KEYCODE_DPAD_DOWN -> showSuggestions = true
                    KeyEvent.KEYCODE_DPAD_UP -> Unit
                    else -> return@onKeyEvent false
                }
                isOverlayVisible = true
                interactionTrigger++
                true
            }
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    this.player = player
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )

        // Thumbnail backdrop until the first frame is available
        if (state.isResolving || error != null) {
            AsyncImage(
                model = item.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)))
        }

        when {
            error != null -> ErrorPanel(error, onRetry, onBack, Modifier.align(Alignment.Center))
            state.isResolving || isBuffering -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(color = YouTubeRed, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                if (state.isResolving) Text("Loading video…", color = Color.White, fontSize = 15.sp)
            }
        }

        AnimatedVisibility(
            visible = showOverlay && error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
            ) {
                // Top: back + YouTube badge
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 48.dp, vertical = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PillButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                    YouTubeBadge()
                }

                // Bottom: title, channel, timeline, transport controls and the suggestions row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(vertical = 32.dp)
                        .onKeyEvent { event ->
                            // Down from the transport controls opens suggestions.
                            if (event.type != KeyEventType.KeyDown || showSuggestions ||
                                event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                            ) return@onKeyEvent false
                            showSuggestions = true
                            true
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.subtitle.isNotEmpty()) {
                            Text(text = item.subtitle, color = TvColors.TextSecondary, fontSize = 14.sp, maxLines = 1)
                        }

                        if (!item.isLive) {
                            val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = YouTubeRed,
                                trackColor = Color.White.copy(alpha = 0.25f)
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatTime(position), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(formatTime(duration), color = TvColors.TextSecondary, fontSize = 12.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!item.isLive) RoundButton(Icons.Default.Replay10, "Back 10 seconds", 48) { seekBy(-SEEK_STEP_MS) }
                            RoundButton(
                                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                description = if (isPlaying) "Pause" else "Play",
                                sizeDp = 60,
                                modifier = Modifier.focusRequester(playPauseFocus)
                            ) {
                                onTogglePlay()
                                interactionTrigger++
                            }
                            if (!item.isLive) RoundButton(Icons.Default.Forward10, "Forward 10 seconds", 48) { seekBy(SEEK_STEP_MS) }
                        }

                        if (!showSuggestions) UpNextHint(Modifier.align(Alignment.CenterHorizontally))
                    }

                    AnimatedVisibility(
                        visible = showSuggestions,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                    ) {
                        SuggestionsRow(
                            suggestions = state.suggestions,
                            isLoading = state.isLoadingSuggestions,
                            focusRequester = suggestionsFocus,
                            onFocusLeft = { showSuggestions = false },
                            onClick = onPlaySuggestion,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpNextHint(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TvColors.TextSecondary, modifier = Modifier.size(18.dp))
        Text("Up next", color = TvColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** "Up next" carousel. Collapses (via [onFocusLeft]) once focus moves back up to the controls. */
@Composable
private fun SuggestionsRow(
    suggestions: List<MediaItemModel>,
    isLoading: Boolean,
    focusRequester: FocusRequester,
    onFocusLeft: () -> Unit,
    onClick: (MediaItemModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var hadFocus by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                if (hadFocus && !it.hasFocus) onFocusLeft()
                hadFocus = it.hasFocus
            },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Up next",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
        if (suggestions.isEmpty()) {
            // Focusable so the row still owns focus (and Up/Back still close it) while empty.
            Box(
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .height(80.dp)
                    .focusRequester(focusRequester)
                    .focusable(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = YouTubeRed, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                } else {
                    Text("No suggestions for this video", color = TvColors.TextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(suggestions, key = { _, it -> it.id }) { index, suggestion ->
                    TvMediaCard(
                        item = suggestion,
                        cardType = CardType.LANDSCAPE,
                        onClick = onClick,
                        modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier
                    )
                }
            }
        }
    }
}

private class PlayerStatus {
    var isBuffering by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

@Composable
private fun rememberPlayerStatus(player: Player): PlayerStatus {
    val status = remember(player) {
        PlayerStatus().apply {
            isBuffering = player.playbackState == Player.STATE_BUFFERING
            error = player.playerError?.let(::describe)
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                status.isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY) status.error = null
            }

            override fun onPlayerError(error: PlaybackException) {
                status.isBuffering = false
                status.error = describe(error)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return status
}

private fun describe(error: PlaybackException): String =
    "Playback failed (${error.errorCodeName})"

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { retryFocus.requestFocus() } }
    Column(
        modifier = modifier
            .widthIn(max = 520.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(TvColors.SurfaceDark)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Can't play this video", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(message, color = TvColors.TextSecondary, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PillButton(Icons.Default.Refresh, "Retry", Modifier.focusRequester(retryFocus), onRetry)
            PillButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
        }
    }
}

@Composable
private fun YouTubeBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 30.dp, height = 22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(YouTubeRed),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Text("YouTube", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun PillButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color.White else TvColors.SurfaceDark.copy(alpha = 0.7f))
            .border(1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        val tint = if (focused) Color.Black else Color.White
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    description: String,
    sizeDp: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.18f))
            .border(1.dp, Color.White.copy(alpha = if (focused) 1f else 0.4f), CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (focused) Color.Black else Color.White,
            modifier = Modifier.size((sizeDp * 0.5f).dp)
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}
