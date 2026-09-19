package com.mobplayer.tv

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.mobplayer.tv.media.MediaManager
import com.mobplayer.tv.models.RemoteActionEvent
import com.mobplayer.tv.models.RemoteIconType
import com.mobplayer.tv.service.WebSocketServerService
import com.mobplayer.tv.ui.components.TvRemoteActionHud
import com.mobplayer.tv.ui.components.TvVideoPlayerOverlay
import com.mobplayer.tv.ui.home.TvHomeScreen
import com.mobplayer.tv.ui.pairing.TvPairingScreen
import com.mobplayer.tv.ui.theme.TvColors
import com.mobplayer.tv.viewmodel.ConnectionEvent
import com.mobplayer.tv.viewmodel.ServerEventBus

@OptIn(ExperimentalTvMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val player = MediaManager.initialize(this)

        // Setup D-pad key event injection from mobile controller
        ServerEventBus.onInjectKeyEvent = { keyCode ->
            runOnUiThread {
                val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
                dispatchKeyEvent(down)
                dispatchKeyEvent(up)
            }
        }

        // Start the WebSocket Ktor Service
        Intent(this, WebSocketServerService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        setContent {
            val connectionEvent by ServerEventBus.connectionEvent.collectAsState()
            val connectedDeviceName by ServerEventBus.connectedDeviceName.collectAsState()
            val activeMediaTitle by ServerEventBus.activeMediaTitle.collectAsState()
            val isPlayerActive by ServerEventBus.isPlayerActive.collectAsState()

            // Remote Back button handler: return to TV Home if player is active
            BackHandler(enabled = isPlayerActive) {
                ServerEventBus.closePlayer()
                MediaManager.stop()
            }

            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TvColors.BackgroundDark),
                    contentAlignment = Alignment.Center
                ) {
                    when (val event = connectionEvent) {
                        is ConnectionEvent.PinRequested -> {
                            TvPairingScreen(
                                pin = event.pin,
                                serverIp = event.serverIp,
                                port = event.port,
                                isEmulator = event.isEmulator,
                                onEnterDemoMode = {
                                    ServerEventBus.enterDemoMode()
                                }
                            )
                        }

                        is ConnectionEvent.ConnectionConflict -> {
                            ConnectionConflictDialog(
                                deviceName = event.deviceName,
                                onResolve = event.onResolve
                            )
                        }

                        ConnectionEvent.None -> {
                            // Connection successful: TV Streaming Experience
                            AnimatedContent(
                                targetState = isPlayerActive,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "ScreenTransition"
                            ) { playerOpen ->
                                if (playerOpen) {
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
                                            title = activeMediaTitle,
                                            connectedDeviceName = connectedDeviceName,
                                            onBackToHome = {
                                                ServerEventBus.closePlayer()
                                                MediaManager.stop()
                                            }
                                        )
                                    }
                                } else {
                                    // TV Streaming Home Screen
                                    TvHomeScreen(
                                        connectedDeviceName = connectedDeviceName,
                                        onPlayMedia = { item ->
                                            ServerEventBus.openPlayer(item.title)
                                            MediaManager.loadMedia(item.videoUrl)
                                            ServerEventBus.postRemoteAction(
                                                RemoteActionEvent(
                                                    action = "PLAY",
                                                    displayName = getString(R.string.action_playing, item.title),
                                                    details = getString(R.string.starting_stream),
                                                    iconType = RemoteIconType.PLAY
                                                )
                                            )
                                        },
                                        onDisconnect = {
                                            ServerEventBus.closePlayer()
                                            MediaManager.stop()
                                            ServerEventBus.onClientDisconnected()
                                            ServerEventBus.requestPin()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Floating Remote Action HUD (renders on top of all screens)
                    TvRemoteActionHud()
                }
            }
        }
    }

    override fun onDestroy() {
        ServerEventBus.onInjectKeyEvent = null
        MediaManager.release()
        stopService(Intent(this, WebSocketServerService::class.java))
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DialogOptionButton(
                text = stringResource(R.string.allow),
                isPrimary = true,
                onClick = { onResolve(true) }
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
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
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
