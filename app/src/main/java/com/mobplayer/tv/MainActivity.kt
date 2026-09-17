package com.mobplayer.tv

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.mobplayer.tv.service.WebSocketServerService
import com.mobplayer.tv.viewmodel.ConnectionEvent
import com.mobplayer.tv.viewmodel.ServerEventBus

import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.mobplayer.tv.media.MediaManager

import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import com.mobplayer.tv.R

@OptIn(ExperimentalTvMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val player = MediaManager.initialize(this)
        
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
            
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorResource(R.color.black)),
                    contentAlignment = Alignment.Center
                ) {
                    // Media Player View
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                this.player = player
                                this.useController = false // Hide default controls since it's controlled via mobile
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay Dialogs
                    when (val event = connectionEvent) {
                        is ConnectionEvent.PinRequested -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.background(colorResource(R.color.dialog_background)).padding(32.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.enter_pin),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = colorResource(R.color.white)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = event.pin,
                                    style = MaterialTheme.typography.displayLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 12.sp
                                )
                            }
                        }
                        is ConnectionEvent.ConnectionConflict -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp).background(colorResource(R.color.dialog_background)).padding(32.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.device_connecting, event.deviceName),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = colorResource(R.color.white)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.disconnect_prompt),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colorResource(R.color.light_gray)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Row {
                                    Button(onClick = { event.onResolve(true) }) {
                                        Text(stringResource(R.string.allow))
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Button(onClick = { event.onResolve(false) }) {
                                        Text(stringResource(R.string.reject))
                                    }
                                }
                            }
                        }
                        ConnectionEvent.None -> {
                            // If player has no media, show placeholder logo
                            if (player.currentMediaItem == null) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.displayMedium,
                                    color = colorResource(R.color.white)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        MediaManager.release()
        super.onDestroy()
    }
}
