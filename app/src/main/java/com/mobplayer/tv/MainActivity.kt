package com.mobplayer.tv

import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.compose.material3.MaterialTheme

import com.mobplayer.tv.models.RemoteActionEvent
import com.mobplayer.tv.models.RemoteIconType
import com.mobplayer.tv.service.WebSocketServerService
import com.mobplayer.tv.ui.components.TvModalOverlay
import com.mobplayer.tv.ui.components.TvRemoteActionHud
import com.mobplayer.tv.ui.components.TvVideoPlayerOverlay
import com.mobplayer.tv.ui.focus.BrowseFocusState
import com.mobplayer.tv.ui.focus.LocalBrowseFocus
import com.mobplayer.tv.ui.home.TvHomeScreen
import com.mobplayer.tv.viewmodel.YouTubeFeedViewModel
import com.mobplayer.tv.viewmodel.YouTubePlayerViewModel
import com.mobplayer.tv.viewmodel.YouTubeSearchViewModel
import com.mobplayer.tv.ui.youtube.TvYouTubePlayerScreen
import com.mobplayer.tv.ui.pairing.TvPairingScreen
import com.mobplayer.tv.ui.theme.TvColors
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executors
import com.mobplayer.tv.viewmodel.TvMainViewModel
import com.mobplayer.tv.viewmodel.ConnectionEvent

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: TvMainViewModel by viewModels()
    private val youTubeFeedViewModel: YouTubeFeedViewModel by viewModels()
    private val youTubePlayerViewModel: YouTubePlayerViewModel by viewModels()
    private val youTubeSearchViewModel: YouTubeSearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val player = viewModel.initializePlayer()

        // Setup D-pad key event injection from mobile controller. Keys go through the real input
        // pipeline (like a hardware remote) so they reach the on-screen keyboard and leave touch
        // mode; dispatching straight into the activity bypasses both.
        viewModel.serverRepository.onInjectKeyEvent = { keyCode ->
            keyInjector.execute {
                val injected = try {
                    injectRemoteKey(keyCode)
                    true
                } catch (e: Exception) {
                    // e.g. SecurityException while another app's window has focus
                    Log.w("MainActivity", "Key injection failed, dispatching directly: ${e.message}")
                    false
                }
                if (!injected) runOnUiThread { dispatchRemoteKeyDirectly(keyCode) }
            }
        }
        // Start the WebSocket Ktor Service
        Intent(this, WebSocketServerService::class.java).also { intent ->
            startForegroundService(intent)
        }

        setContent {
            // Scroll positions + last played item for Home/Search/My List, kept across the player
            val browseFocus = remember { BrowseFocusState() }
            val connectionEvent by viewModel.connectionEvent.collectAsState()
            val connectedDeviceName by viewModel.serverRepository.connectedDeviceName.collectAsState()
            val activeMediaTitle by viewModel.serverRepository.activeMediaTitle.collectAsState()
            val isPlayerActive by viewModel.serverRepository.isPlayerActive.collectAsState()
            val uploadedVideos by viewModel.uploadedVideos.collectAsState()
            val youTubeState by youTubeFeedViewModel.uiState.collectAsState()
            val youTubePlayerState by youTubePlayerViewModel.uiState.collectAsState()
            val searchState by youTubeSearchViewModel.uiState.collectAsState()
            val homeTab = stringResource(R.string.tab_home)
            var selectedHomeTab by rememberSaveable { mutableStateOf(homeTab) }

            // Text typed on a remote goes to the YouTube search field: open the Search tab and
            // mirror the text (typing searches after a pause; TEXT_SUBMIT searches immediately).
            val searchTab = stringResource(R.string.tab_search)
            // Player hidden behind the browse screens while its media keeps playing (e.g. searching
            // mid-video); Back or starting other media brings it back up.
            var isPlayerMinimized by rememberSaveable { mutableStateOf(false) }
            // Saved alongside isPlayerMinimized so a configuration change (which re-runs effects)
            // isn't mistaken for a player change and doesn't undo a restored minimise.
            val playerKey = "$isPlayerActive|$activeMediaTitle"
            var lastPlayerKey by rememberSaveable { mutableStateOf(playerKey) }
            // loadCount catches a new video that reuses the title (untitled casts, restarting an upload).
            val loadCount by viewModel.mediaRepository.loadCount.collectAsState()
            var lastLoadCount by rememberSaveable { mutableLongStateOf(loadCount) }
            LaunchedEffect(playerKey, loadCount) {
                // The open YouTube video's stream finishing its resolve isn't new media; leave it
                // minimised so it doesn't cover a search being typed.
                val isNewLoad = loadCount != lastLoadCount && !youTubePlayerViewModel.isOwnLoad(loadCount)
                // Closed elsewhere, or new media opened (e.g. cast from the phone): show the player again
                if (playerKey != lastPlayerKey || isNewLoad) isPlayerMinimized = false
                lastPlayerKey = playerKey
                lastLoadCount = loadCount
            }
            // While browsing UI over a playing video, remote Left/Right/OK/Back navigate instead of
            // seeking / toggling playback / closing the player.
            var isYouTubeSuggestionsOpen by remember { mutableStateOf(false) }
            var isYouTubeErrorShowing by remember { mutableStateOf(false) }
            LaunchedEffect(isPlayerMinimized, isYouTubeSuggestionsOpen, isYouTubeErrorShowing) {
                viewModel.serverRepository.setUiNavigating(isPlayerMinimized || isYouTubeSuggestionsOpen || isYouTubeErrorShowing)
            }
            LaunchedEffect(Unit) {
                viewModel.serverRepository.remoteTextInput.collect { input ->
                    if (viewModel.serverRepository.isPlayerActive.value) isPlayerMinimized = true
                    browseFocus.consumeRestore() // the played card isn't on the Search tab
                    selectedHomeTab = searchTab
                    if (input.submit) youTubeSearchViewModel.submit(input.text)
                    else youTubeSearchViewModel.onQueryChange(input.text)
                }
            }

            // Back from another tab (e.g. Search) returns to Home instead of leaving the app
            BackHandler(enabled = !isPlayerActive && selectedHomeTab != homeTab) {
                selectedHomeTab = homeTab
            }
            val playerState by viewModel.playerStateFlow.collectAsState()

            // Keep the display awake while a video plays (both players share the same ExoPlayer);
            // paused/stopped playback lets the TV sleep normally.
            val keepScreenOn = isPlayerActive && playerState?.isPlaying == true
            LaunchedEffect(keepScreenOn) {
                if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            // Remote Back button handler: return to TV Home if player is active
            BackHandler(enabled = isPlayerActive) {
                if (isPlayerMinimized) {
                    isPlayerMinimized = false
                } else if (youTubePlayerState.item != null) {
                    youTubePlayerViewModel.close()
                } else {
                    viewModel.serverRepository.closePlayer()
                    viewModel.mediaRepository.stop()
                }
            }

            CompositionLocalProvider(LocalBrowseFocus provides browseFocus) {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(TvColors.BackgroundDark),
                        contentAlignment = Alignment.Center
                    ) {
                        // Running screen (home/search/player) always renders; pairing and conflict prompts overlay it
                        // The YouTube state rides in the target state so a closing YouTube screen fades out
                        // as itself (its live state is already empty) instead of as the regular player.
                        // Keyed on open/closed only, so YouTube changes while minimised don't re-key the
                        // browse screen and switching player types stays an instant swap as before.
                        AnimatedContent(
                            targetState = (isPlayerActive && !isPlayerMinimized) to youTubePlayerState.takeIf { it.item != null },
                            transitionSpec = {
                                fadeIn(animationSpec = tween(120)) togetherWith fadeOut(animationSpec = tween(120))
                            },
                            contentKey = { it.first },
                            label = "ScreenTransition"
                        ) { (playerOpen, shownYouTubeState) ->
                            if (playerOpen && shownYouTubeState != null) {
                                // Dedicated YouTube player screen
                                TvYouTubePlayerScreen(
                                    player = player,
                                    state = shownYouTubeState,
                                    playerState = playerState,
                                    onTogglePlay = {
                                        if (!shownYouTubeState.isResolving) {
                                            if (playerState?.isPlaying == true) viewModel.mediaRepository.pause()
                                            else viewModel.mediaRepository.play()
                                        }
                                    },
                                    onSeekTo = viewModel.mediaRepository::seekTo,
                                    onRetry = youTubePlayerViewModel::retry,
                                    onBack = youTubePlayerViewModel::close,
                                    onPlaySuggestion = youTubePlayerViewModel::play,
                                    onSuggestionsVisibleChange = { isYouTubeSuggestionsOpen = it },
                                    onErrorVisibleChange = { isYouTubeErrorShowing = it }
                                )
                            } else if (playerOpen) {
                                // Fullscreen TV Video Player View
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AndroidView(
                                        factory = { context ->
                                            PlayerView(context).apply {
                                                this.player = player
                                                this.useController = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    TvVideoPlayerOverlay(
                                        viewModel = viewModel,
                                        title = activeMediaTitle,
                                        connectedDeviceName = connectedDeviceName,
                                        onBackToHome = {
                                            viewModel.serverRepository.closePlayer()
                                            viewModel.mediaRepository.stop()
                                        }
                                    )
                                }
                            } else Box(modifier = Modifier.fillMaxSize()) {
                                // Minimized video keeps showing behind the translucent browse screen
                                if (isPlayerMinimized) {
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
                                }
                                // TV Streaming Home Screen
                                TvHomeScreen(
                                    backgroundColor = if (isPlayerMinimized) {
                                        TvColors.BackgroundDark.copy(alpha = 0.7f)
                                    } else {
                                        TvColors.BackgroundDark
                                    },
                                    connectedDeviceName = connectedDeviceName,
                                    uploadedVideos = uploadedVideos,
                                    selectedTab = selectedHomeTab,
                                    onTabSelected = { selectedHomeTab = it },
                                    youTubeState = youTubeState,
                                    youTubeCategories = youTubeFeedViewModel.categories,
                                    onYouTubeCategorySelected = youTubeFeedViewModel::selectCategory,
                                    onYouTubeRetry = youTubeFeedViewModel::refresh,
                                    searchState = searchState,
                                    onSearchQueryChange = youTubeSearchViewModel::onQueryChange,
                                    onSearchSubmit = youTubeSearchViewModel::submit,
                                    onSearchClearRecents = youTubeSearchViewModel::clearRecentSearches,
                                    onSearchRetry = youTubeSearchViewModel::retry,
                                    onPlayMedia = { item ->
                                        isPlayerMinimized = false
                                        val uploadedMeta = viewModel.videoUploadManager.getVideoMetadata(item.id)
                                        if (item.youtubeVideoId != null) {
                                            youTubePlayerViewModel.play(item)
                                        } else if (uploadedMeta != null) {
                                            viewModel.playUploadedVideo(uploadedMeta, resume = true)
                                        } else {
                                            viewModel.serverRepository.openPlayer(item.title)
                                            viewModel.mediaRepository.loadMedia(item.videoUrl)
                                            viewModel.serverRepository.postRemoteAction(
                                                RemoteActionEvent(
                                                    action = "PLAY",
                                                    displayName = getString(R.string.action_playing, item.title),
                                                    details = getString(R.string.starting_stream),
                                                    iconType = RemoteIconType.PLAY
                                                )
                                            )
                                        }
                                    },
                                    onDisconnect = {
                                        viewModel.serverRepository.closePlayer()
                                        viewModel.mediaRepository.stop()
                                        viewModel.serverRepository.closeActiveSession()
                                        viewModel.serverRepository.onClientDisconnected()
                                        viewModel.serverRepository.requestPin()
                                    }
                                )
                            }
                        }

                        when (val event = connectionEvent) {
                            // Back leaves the app, as it did before the overlay, for users who don't want to pair.
                            is ConnectionEvent.PinRequested -> TvModalOverlay(onBack = { finish() }) {
                                TvPairingScreen(
                                    pin = event.pin,
                                    serverIp = event.serverIp,
                                    port = event.port,
                                    isEmulator = event.isEmulator,
                                    onEnterDemoMode = {
                                        viewModel.serverRepository.enterDemoMode()
                                    }
                                )
                            }

                            // Back on the conflict prompt rejects the new device
                            is ConnectionEvent.ConnectionConflict -> TvModalOverlay(onBack = { event.onResolve(false) }) {
                                ConnectionConflictDialog(
                                    deviceName = event.deviceName,
                                    onResolve = event.onResolve
                                )
                            }

                            ConnectionEvent.None -> Unit
                        }

                        // Floating Remote Action HUD (renders on top of all screens)
                        TvRemoteActionHud(viewModel = viewModel)
                    }
                }
            }
        }
    }

    /** Single thread keeps remote key order; injection blocks until the app consumes the key. */
    private val keyInjector = Executors.newSingleThreadExecutor()

    /**
     * Injects a down/up pair into this app's window. D-pad keys are tagged SOURCE_DPAD like a real
     * TV remote: text fields only hand Up/Down to focus navigation for D-pad sources (keyboard
     * arrows move the cursor instead). Must run off the main thread.
     */
    private fun injectRemoteKey(keyCode: Int) {
        val source = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER -> InputDevice.SOURCE_DPAD
            else -> InputDevice.SOURCE_KEYBOARD
        }
        val instrumentation = Instrumentation()
        val downTime = SystemClock.uptimeMillis()
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            instrumentation.sendKeySync(
                KeyEvent(
                    downTime, SystemClock.uptimeMillis(), action, keyCode, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, source
                )
            )
        }
    }

    /** Fallback when injection is refused: deliver straight to this activity (no IME, touch mode stays). */
    private fun dispatchRemoteKeyDirectly(keyCode: Int) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBackPressedDispatcher.onBackPressed()
        } else {
            val now = SystemClock.uptimeMillis()
            dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        }
    }

    override fun onDestroy() {
        viewModel.serverRepository.onInjectKeyEvent = null
        keyInjector.shutdownNow()
        if (!isChangingConfigurations) {
            viewModel.mediaRepository.release()
            stopService(Intent(this, WebSocketServerService::class.java))
        }
        super.onDestroy()
    }
}

@Composable
private fun ConnectionConflictDialog(
    deviceName: String,
    onResolve: (Boolean) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(480.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(TvColors.SurfaceDark)
            .border(1.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .padding(32.dp)
    ) {
        Text(
            text = stringResource(R.string.device_connecting, deviceName),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.disconnect_prompt),
            fontSize = 14.sp,
            color = TvColors.TextSecondary
        )
        Spacer(modifier = Modifier.height(26.dp))
        val allowFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { allowFocus.requestFocus() } }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DialogOptionButton(
                text = stringResource(R.string.allow),
                isPrimary = true,
                onClick = { onResolve(true) },
                modifier = Modifier.focusRequester(allowFocus)
            )
            DialogOptionButton(
                text = stringResource(R.string.reject),
                isPrimary = false,
                onClick = { onResolve(false) }
            )
        }
    }
}

@Composable
private fun DialogOptionButton(
    text: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isFocused -> Color.White
                    isPrimary -> TvColors.PrimaryAccent
                    else -> TvColors.SurfaceElevated
                }
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
