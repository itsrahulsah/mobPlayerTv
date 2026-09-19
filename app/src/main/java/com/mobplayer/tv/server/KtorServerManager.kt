package com.mobplayer.tv.server

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import com.mobplayer.tv.auth.AuthManager
import com.mobplayer.tv.media.MediaManager
import com.mobplayer.tv.models.PlayerCommandPayload
import com.mobplayer.tv.models.RemoteActionEvent
import com.mobplayer.tv.models.RemoteIconType
import com.mobplayer.tv.models.WebSocketMessage
import com.mobplayer.tv.network.NetworkUtils
import com.mobplayer.tv.viewmodel.ServerEventBus
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration

class KtorServerManager(private val context: Context) {
    private var server: ApplicationEngine? = null
    private val authManager = AuthManager(context)

    // Maintain a single active controller session
    private var activeSession: DefaultWebSocketSession? = null
    private var currentPin = ""
    private var lastNonZeroVolume = 1.0f

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun startServer(port: Int = 8080) {
        if (server != null) return

        val testClientHtml = context.assets.open("test_client.html")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

        server = embeddedServer(CIO, port = port) {
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(15)
                timeout = Duration.ofSeconds(15)
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }

            routing {
                listOf("/", "/test_client.html").forEach { path ->
                    get(path) {
                        call.response.header(HttpHeaders.CacheControl, "no-store")
                        call.respondText(testClientHtml, ContentType.Text.Html)
                    }
                }

                webSocket("/control") {
                    val clientInfo = this.call.request.local.let { "${it.remoteHost}:${it.serverPort}" }
                    Log.i("RemoteEvent", "==================================================")
                    Log.i("RemoteEvent", "🌐 [Client Connected] WebSocket connection established from: $clientInfo")
                    ServerEventBus.logRemoteEvent("Connection", "Client connected from $clientInfo")

                    try {
                        handleClientConnection(this)
                    } catch (e: ClosedReceiveChannelException) {
                        Log.i("RemoteEvent", "🔌 [Client Disconnected] Connection closed cleanly by client: $clientInfo")
                    } catch (e: Exception) {
                        Log.e("RemoteEvent", "❌ [Connection Error] Error in websocket connection: ${e.message}", e)
                    } finally {
                        if (activeSession == this) {
                            activeSession = null
                            Log.i("RemoteEvent", "🔌 [Active Controller Disconnected] Showing pairing PIN again")
                            ServerEventBus.onClientDisconnected()
                            ServerEventBus.showPin(currentPin)
                        }
                    }
                }
            }
        }.start(wait = false)

        val isEmulator = NetworkUtils.isEmulator()
        val localIp = NetworkUtils.getLocalIpAddress() ?: "0.0.0.0"
        val displayIp = if (isEmulator) "10.0.2.2 (Local: $localIp)" else localIp
        val wsEndpoint = if (isEmulator) "ws://10.0.2.2:$port/control" else "ws://$localIp:$port/control"

        Log.i("RemoteEvent", "==================================================")
        Log.i("RemoteEvent", "🚀 [SOCKET SERVER UP]")
        Log.i("RemoteEvent", "   • IP Address: $displayIp")
        Log.i("RemoteEvent", "   • Port:       $port")
        Log.i("RemoteEvent", "   • Endpoint:   $wsEndpoint")
        if (isEmulator) {
            Log.i("RemoteEvent", "   • Android Emulator: Run 'adb forward tcp:$port tcp:$port' on PC")
        }
        Log.i("RemoteEvent", "==================================================")
        ServerEventBus.logRemoteEvent("Server", "🚀 Socket server UP at IP: $displayIp, Port: $port | Endpoint: $wsEndpoint")

        ServerEventBus.onRequestNewPin = {
            currentPin = authManager.generatePin()
            currentPin
        }

        // Show PIN on TV on startup
        currentPin = authManager.generatePin()
        ServerEventBus.showPin(currentPin)
    }

    private suspend fun handleClientConnection(session: DefaultWebSocketSession) {
        var isAuthenticated = false

        for (frame in session.incoming) {
            frame as? Frame.Text ?: continue
            val receivedText = frame.readText().trim()

            Log.i("RemoteEvent", "--------------------------------------------------")
            Log.i("RemoteEvent", "📩 [RAW INCOMING FRAME] $receivedText")

            try {
                // Try decoding as WebSocketMessage envelope
                val message = try {
                    json.decodeFromString<WebSocketMessage>(receivedText)
                } catch (e: Exception) {
                    // Fallback: If sent without envelope, wrap as COMMAND or direct action
                    Log.w("RemoteEvent", "⚠️ Frame not wrapped in WebSocketMessage, attempting fallback parse")
                    if (receivedText.contains("action", ignoreCase = true)) {
                        WebSocketMessage("COMMAND", receivedText)
                    } else if (receivedText.startsWith("http://") || receivedText.startsWith("https://")) {
                        WebSocketMessage("LOAD_MEDIA", receivedText)
                    } else {
                        WebSocketMessage(receivedText.uppercase(), "")
                    }
                }

                Log.i("RemoteEvent", "📋 [Parsed Message] type='${message.type}' | payload='${message.payload}'")

                if (!isAuthenticated) {
                    when (message.type) {
                        "AUTH_REQUEST" -> {
                            Log.i("RemoteEvent", "🔑 Processing AUTH_REQUEST with token: '${message.payload}'")
                            if (authManager.isValidToken(message.payload)) {
                                isAuthenticated = true
                                Log.i("RemoteEvent", "✅ Token authenticated successfully!")
                                session.send(Frame.Text(json.encodeToString(WebSocketMessage("AUTH_SUCCESS", message.payload))))
                                handleAuthenticatedSession(session)
                            } else {
                                Log.i("RemoteEvent", "⚠️ Token invalid or missing. Requesting PIN from client.")
                                session.send(Frame.Text(json.encodeToString(WebSocketMessage("PIN_REQUIRED", ""))))
                            }
                        }

                        "PIN_SUBMIT" -> {
                            Log.i("RemoteEvent", "🔢 Processing PIN_SUBMIT: received='${message.payload}', expected='$currentPin'")
                            if (authManager.verifyPin(currentPin, message.payload.trim())) {
                                isAuthenticated = true
                                Log.i("RemoteEvent", "✅ PIN matched! Authentication successful.")
                                ServerEventBus.hideDialogs()
                                val token = authManager.generateAndSaveToken()
                                session.send(Frame.Text(json.encodeToString(WebSocketMessage("AUTH_SUCCESS", token))))
                                handleAuthenticatedSession(session)

                                // Rotate PIN for next session
                                currentPin = authManager.generatePin()
                            } else {
                                Log.w("RemoteEvent", "❌ Invalid PIN submitted ('${message.payload}'). Rejecting client.")
                                session.send(Frame.Text(json.encodeToString(WebSocketMessage("AUTH_FAILED", "Invalid PIN"))))
                                session.close()
                                return
                            }
                        }

                        else -> {
                            Log.w("RemoteEvent", "⚠️ Client attempted to send '${message.type}' before authenticating")
                            session.send(Frame.Text(json.encodeToString(WebSocketMessage("PIN_REQUIRED", ""))))
                        }
                    }
                } else {
                    // Authenticated Remote Command Execution
                    processIncomingRemoteEvent(message, session)
                }
            } catch (e: Exception) {
                Log.e("RemoteEvent", "❌ Failed to parse or process incoming remote message", e)
            }
        }
    }

    private suspend fun processIncomingRemoteEvent(
        message: WebSocketMessage,
        session: DefaultWebSocketSession
    ) {
        val type = message.type.uppercase().trim()
        val payloadStr = message.payload.trim()

        Log.i("RemoteEvent", "⚡ [Remote Action Received] Type: '$type', Payload: '$payloadStr'")

        when (type) {
            "COMMAND" -> {
                val command = try {
                    json.decodeFromString<PlayerCommandPayload>(payloadStr)
                } catch (e: Exception) {
                    // If payload is plain text action like "PLAY"
                    PlayerCommandPayload(action = payloadStr)
                }
                executeAction(command.action, command, session)
            }

            "LOAD_MEDIA" -> {
                handleLoadMedia(payloadStr, session)
            }

            "KEY_EVENT" -> {
                handleKeyEvent(payloadStr, session)
            }

            // Direct action types (e.g. {"type":"PLAY","payload":""})
            "PLAY", "PAUSE", "TOGGLE_PLAY_PAUSE", "STOP", "SEEK",
            "SEEK_FORWARD", "SEEK_BACKWARD", "FAST_FORWARD", "REWIND",
            "VOLUME_UP", "VOLUME_DOWN", "SET_VOLUME", "MUTE",
            "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT",
            "DPAD_CENTER", "SELECT", "BACK", "HOME" -> {
                val command = try {
                    if (payloadStr.isNotEmpty()) json.decodeFromString<PlayerCommandPayload>(payloadStr)
                    else PlayerCommandPayload(action = type)
                } catch (e: Exception) {
                    PlayerCommandPayload(action = type)
                }
                executeAction(type, command, session)
            }

            else -> {
                Log.w("RemoteEvent", "❓ Unknown remote message type: '$type'")
            }
        }
    }

    private suspend fun executeAction(
        actionRaw: String,
        payload: PlayerCommandPayload?,
        session: DefaultWebSocketSession
    ) {
        val action = actionRaw.uppercase().trim()
        Log.i("RemoteEvent", "🎯 [Executing Action] '$action' with payload: $payload")

        withContext(Dispatchers.Main) {
            when (action) {
                "PLAY", "RESUME" -> {
                    MediaManager.play()
                    val title = ServerEventBus.activeMediaTitle.value.ifEmpty { "Streaming Media" }
                    ServerEventBus.openPlayer(title)
                    ServerEventBus.postRemoteAction(
                        RemoteActionEvent("PLAY", "Playing", "Playback started", RemoteIconType.PLAY)
                    )
                    Log.i("RemoteEvent", "▶ [Action Complete] PLAY executed")
                }

                "PAUSE" -> {
                    MediaManager.pause()
                    ServerEventBus.postRemoteAction(
                        RemoteActionEvent("PAUSE", "Paused", "Playback paused", RemoteIconType.PAUSE)
                    )
                    Log.i("RemoteEvent", "⏸ [Action Complete] PAUSE executed")
                }

                "TOGGLE_PLAY_PAUSE", "PLAY_PAUSE" -> {
                    val isPlaying = MediaManager.player?.isPlaying == true
                    if (isPlaying) {
                        MediaManager.pause()
                        ServerEventBus.postRemoteAction(
                            RemoteActionEvent("PAUSE", "Paused", "Playback paused", RemoteIconType.PAUSE)
                        )
                    } else {
                        MediaManager.play()
                        val title = ServerEventBus.activeMediaTitle.value.ifEmpty { "Streaming Media" }
                        ServerEventBus.openPlayer(title)
                        ServerEventBus.postRemoteAction(
                            RemoteActionEvent("PLAY", "Playing", "Playback started", RemoteIconType.PLAY)
                        )
                    }
                    Log.i("RemoteEvent", "⏯ [Action Complete] TOGGLE_PLAY_PAUSE executed (wasPlaying=$isPlaying)")
                }

                "STOP" -> {
                    MediaManager.stop()
                    ServerEventBus.closePlayer()
                    ServerEventBus.postRemoteAction(
                        RemoteActionEvent("STOP", "Stopped", "Returned to TV Home", RemoteIconType.HOME)
                    )
                    Log.i("RemoteEvent", "⏹ [Action Complete] STOP executed")
                }

                "SEEK" -> {
                    val seekTo = payload?.seekToMs ?: payload?.positionMs ?: 0L
                    MediaManager.seekTo(seekTo)
                    ServerEventBus.postRemoteAction(
                        RemoteActionEvent("SEEK", "Seek", formatTime(seekTo), RemoteIconType.SEEK_FORWARD)
                    )
                    Log.i("RemoteEvent", "⏩ [Action Complete] SEEK to ${seekTo}ms executed")
                }

                "SEEK_FORWARD", "FORWARD", "FAST_FORWARD" -> {
                    val currentPos = MediaManager.player?.currentPosition ?: 0L
                    val newPos = currentPos + 10_000L
                    MediaManager.seekTo(newPos)
                    ServerEventBus.postRemoteAction(
                        RemoteActionEvent("SEEK_FORWARD", "Forward +10s", formatTime(newPos), RemoteIconType.SEEK_FORWARD)
                    )
                    Log.i("RemoteEvent", "⏩ [Action Complete] SEEK_FORWARD (+10s to ${newPos}ms)")
                }

                "SEEK_BACKWARD", "REWIND", "BACKWARD" -> {
                    val currentPos = MediaManager.player?.currentPosition ?: 0L
                    val newPos = (currentPos - 10_000L).coerceAtLeast(0L)
                    MediaManager.seekTo(newPos)
                    ServerEventBus.postRemoteAction(
                        RemoteActionEvent("SEEK_BACKWARD", "Rewind -10s", formatTime(newPos), RemoteIconType.SEEK_BACKWARD)
                    )
                    Log.i("RemoteEvent", "⏪ [Action Complete] SEEK_BACKWARD (-10s to ${newPos}ms)")
                }

                "SET_VOLUME" -> {
                    val vol = (payload?.volume ?: 0.5f).coerceIn(0f, 1f)
                    MediaManager.setVolume(vol)
                    ServerEventBus.postRemoteAction(
                        RemoteActionEvent("SET_VOLUME", "Volume ${(vol * 100).toInt()}%", null, RemoteIconType.VOLUME_UP)
                    )
                    Log.i("RemoteEvent", "🔊 [Action Complete] SET_VOLUME: $vol")
                }

                "VOLUME_UP" -> {
                    val curVol = MediaManager.player?.volume ?: 1f
                    val newVol = (curVol + 0.05f).coerceAtMost(1f)
                    MediaManager.setVolume(newVol)
                    ServerEventBus.postRemoteAction(
                        RemoteActionEvent("VOLUME_UP", "Volume ${(newVol * 100).toInt()}%", null, RemoteIconType.VOLUME_UP)
                    )
                    Log.i("RemoteEvent", "🔊 [Action Complete] VOLUME_UP: $newVol")
                }

                "VOLUME_DOWN" -> {
                    val curVol = MediaManager.player?.volume ?: 1f
                    val newVol = (curVol - 0.05f).coerceAtLeast(0f)
                    MediaManager.setVolume(newVol)
                    ServerEventBus.postRemoteAction(
                        RemoteActionEvent("VOLUME_DOWN", "Volume ${(newVol * 100).toInt()}%", null, RemoteIconType.VOLUME_DOWN)
                    )
                    Log.i("RemoteEvent", "🔉 [Action Complete] VOLUME_DOWN: $newVol")
                }

                "MUTE" -> {
                    val curVol = MediaManager.player?.volume ?: 1f
                    if (curVol > 0f) {
                        lastNonZeroVolume = curVol
                        MediaManager.setVolume(0f)
                        ServerEventBus.postRemoteAction(
                            RemoteActionEvent("MUTE", "Muted", "Volume 0%", RemoteIconType.MUTE)
                        )
                        Log.i("RemoteEvent", "🔇 [Action Complete] MUTED")
                    } else {
                        val restoreVol = if (lastNonZeroVolume > 0f) lastNonZeroVolume else 0.8f
                        MediaManager.setVolume(restoreVol)
                        ServerEventBus.postRemoteAction(
                            RemoteActionEvent("UNMUTE", "Unmuted", "Volume ${(restoreVol * 100).toInt()}%", RemoteIconType.VOLUME_UP)
                        )
                        Log.i("RemoteEvent", "🔊 [Action Complete] UNMUTED to $restoreVol")
                    }
                }

                // D-Pad Navigation Keys
                "DPAD_UP", "UP" -> {
                    ServerEventBus.injectKey(KeyEvent.KEYCODE_DPAD_UP)
                    ServerEventBus.postRemoteAction(
                        RemoteActionEvent("DPAD_UP", "▲ Up", "Navigating", RemoteIconType.DPAD_UP)
                    )
                    Log.i("RemoteEvent", "▲ [Action Complete] DPAD_UP injected")
                }

                "DPAD_DOWN", "DOWN" -> {
                    ServerEventBus.injectKey(KeyEvent.KEYCODE_DPAD_DOWN)
                    ServerEventBus.postRemoteAction(
                        RemoteActionEvent("DPAD_DOWN", "▼ Down", "Navigating", RemoteIconType.DPAD_DOWN)
                    )
                    Log.i("RemoteEvent", "▼ [Action Complete] DPAD_DOWN injected")
                }

                "DPAD_LEFT", "LEFT" -> {
                    if (ServerEventBus.isPlayerActive.value) {
                        val currentPos = MediaManager.player?.currentPosition ?: 0L
                        val newPos = (currentPos - 10_000L).coerceAtLeast(0L)
                        MediaManager.seekTo(newPos)
                        ServerEventBus.postRemoteAction(
                            RemoteActionEvent("SEEK_BACKWARD", "Rewind -10s", formatTime(newPos), RemoteIconType.SEEK_BACKWARD)
                        )
                    } else {
                        ServerEventBus.injectKey(KeyEvent.KEYCODE_DPAD_LEFT)
                        ServerEventBus.postRemoteAction(
                            RemoteActionEvent("DPAD_LEFT", "◄ Left", "Navigating", RemoteIconType.DPAD_LEFT)
                        )
                    }
                    Log.i("RemoteEvent", "◄ [Action Complete] DPAD_LEFT handled")
                }

                "DPAD_RIGHT", "RIGHT" -> {
                    if (ServerEventBus.isPlayerActive.value) {
                        val currentPos = MediaManager.player?.currentPosition ?: 0L
                        val newPos = currentPos + 10_000L
                        MediaManager.seekTo(newPos)
                        ServerEventBus.postRemoteAction(
                            RemoteActionEvent("SEEK_FORWARD", "Forward +10s", formatTime(newPos), RemoteIconType.SEEK_FORWARD)
                        )
                    } else {
                        ServerEventBus.injectKey(KeyEvent.KEYCODE_DPAD_RIGHT)
                        ServerEventBus.postRemoteAction(
                            RemoteActionEvent("DPAD_RIGHT", "► Right", "Navigating", RemoteIconType.DPAD_RIGHT)
                        )
                    }
                    Log.i("RemoteEvent", "► [Action Complete] DPAD_RIGHT handled")
                }

                "DPAD_CENTER", "SELECT", "ENTER", "OK" -> {
                    if (ServerEventBus.isPlayerActive.value) {
                        val isPlaying = MediaManager.player?.isPlaying == true
                        if (isPlaying) {
                            MediaManager.pause()
                            ServerEventBus.postRemoteAction(
                                RemoteActionEvent("PAUSE", "Paused", "Playback paused", RemoteIconType.PAUSE)
                            )
                        } else {
                            MediaManager.play()
                            ServerEventBus.postRemoteAction(
                                RemoteActionEvent("PLAY", "Playing", "Playback resumed", RemoteIconType.PLAY)
                            )
                        }
                    } else {
                        ServerEventBus.injectKey(KeyEvent.KEYCODE_DPAD_CENTER)
                        ServerEventBus.postRemoteAction(
                            RemoteActionEvent("SELECT", "🔘 Select", "Action", RemoteIconType.SELECT)
                        )
                    }
                    Log.i("RemoteEvent", "🔘 [Action Complete] DPAD_CENTER / SELECT handled")
                }

                "BACK" -> {
                    if (ServerEventBus.isPlayerActive.value) {
                        MediaManager.stop()
                        ServerEventBus.closePlayer()
                        ServerEventBus.postRemoteAction(
                            RemoteActionEvent("BACK", "Exit to TV Home", null, RemoteIconType.BACK)
                        )
                    } else {
                        ServerEventBus.injectKey(KeyEvent.KEYCODE_BACK)
                        ServerEventBus.postRemoteAction(
                            RemoteActionEvent("BACK", "↩ Back", null, RemoteIconType.BACK)
                        )
                    }
                    Log.i("RemoteEvent", "↩ [Action Complete] BACK handled")
                }

                "HOME" -> {
                    MediaManager.stop()
                    ServerEventBus.closePlayer()
                    ServerEventBus.postRemoteAction(
                        RemoteActionEvent("HOME", "🏠 TV Home", null, RemoteIconType.HOME)
                    )
                    Log.i("RemoteEvent", "🏠 [Action Complete] HOME handled")
                }

                else -> {
                    Log.w("RemoteEvent", "⚠️ Unhandled action command: '$action'")
                    ServerEventBus.postRemoteAction(
                        RemoteActionEvent(action, action, "Remote Action", RemoteIconType.INFO)
                    )
                }
            }
        }

        // Acknowledge action execution to the controller
        session.send(Frame.Text(json.encodeToString(WebSocketMessage("COMMAND_SUCCESS", action))))
    }

    private suspend fun handleLoadMedia(payload: String, session: DefaultWebSocketSession) {
        val (url, title) = try {
            val parsed = json.decodeFromString<PlayerCommandPayload>(payload)
            Pair(parsed.url ?: payload, parsed.title ?: "Remote Media Stream")
        } catch (e: Exception) {
            Pair(payload, "Remote Media Stream")
        }

        Log.i("RemoteEvent", "🎬 [Loading Media] URL='$url', Title='$title'")

        withContext(Dispatchers.Main) {
            ServerEventBus.openPlayer(title)
            MediaManager.loadMedia(url)
            ServerEventBus.postRemoteAction(
                RemoteActionEvent("LOAD_MEDIA", "🎬 Loading Media", title, RemoteIconType.MEDIA)
            )
        }

        session.send(Frame.Text(json.encodeToString(WebSocketMessage("COMMAND_SUCCESS", "LOAD_MEDIA"))))
    }

    private suspend fun handleKeyEvent(payload: String, session: DefaultWebSocketSession) {
        val keyCode = when (payload.uppercase().trim()) {
            "DPAD_UP", "UP", "19" -> KeyEvent.KEYCODE_DPAD_UP
            "DPAD_DOWN", "DOWN", "20" -> KeyEvent.KEYCODE_DPAD_DOWN
            "DPAD_LEFT", "LEFT", "21" -> KeyEvent.KEYCODE_DPAD_LEFT
            "DPAD_RIGHT", "RIGHT", "22" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "DPAD_CENTER", "SELECT", "ENTER", "OK", "23" -> KeyEvent.KEYCODE_DPAD_CENTER
            "BACK", "4" -> KeyEvent.KEYCODE_BACK
            "HOME", "3" -> KeyEvent.KEYCODE_HOME
            "MENU", "82" -> KeyEvent.KEYCODE_MENU
            else -> payload.toIntOrNull() ?: KeyEvent.KEYCODE_UNKNOWN
        }

        Log.i("RemoteEvent", "⌨️ [Key Event] KeyCode=$keyCode for payload='$payload'")

        if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
            withContext(Dispatchers.Main) {
                ServerEventBus.injectKey(keyCode)
                val icon = when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> RemoteIconType.DPAD_UP
                    KeyEvent.KEYCODE_DPAD_DOWN -> RemoteIconType.DPAD_DOWN
                    KeyEvent.KEYCODE_DPAD_LEFT -> RemoteIconType.DPAD_LEFT
                    KeyEvent.KEYCODE_DPAD_RIGHT -> RemoteIconType.DPAD_RIGHT
                    KeyEvent.KEYCODE_DPAD_CENTER -> RemoteIconType.SELECT
                    KeyEvent.KEYCODE_BACK -> RemoteIconType.BACK
                    KeyEvent.KEYCODE_HOME -> RemoteIconType.HOME
                    else -> RemoteIconType.INFO
                }
                ServerEventBus.postRemoteAction(
                    RemoteActionEvent("KEY_EVENT", "Key: $payload", "KeyCode $keyCode", icon)
                )
            }
            session.send(Frame.Text(json.encodeToString(WebSocketMessage("COMMAND_SUCCESS", "KEY_EVENT"))))
        } else {
            Log.w("RemoteEvent", "⚠️ Unknown key event code for payload: '$payload'")
        }
    }

    private fun formatTime(ms: Long): String {
        val sec = (ms / 1000) % 60
        val min = (ms / (1000 * 60)) % 60
        val hr = ms / (1000 * 60 * 60)
        return if (hr > 0) String.format("%02d:%02d:%02d", hr, min, sec) else String.format("%02d:%02d", min, sec)
    }

    private var stateBroadcastJob: Job? = null

    private suspend fun handleAuthenticatedSession(session: DefaultWebSocketSession) {
        if (activeSession != null && activeSession != session) {
            // Another controller is already active, prompt UI
            val allowed = CompletableDeferred<Boolean>()
            ServerEventBus.promptConnectionConflict("A new device") { result ->
                allowed.complete(result)
            }

            if (allowed.await()) {
                activeSession?.close()
                activeSession = session
                startStateBroadcast(session)
                ServerEventBus.onClientConnected("Mobile Controller")
            } else {
                session.send(Frame.Text(json.encodeToString(WebSocketMessage("AUTH_FAILED", "Connection rejected by TV"))))
                session.close()
                return
            }
        } else {
            activeSession = session
            startStateBroadcast(session)
            ServerEventBus.hideDialogs()
            ServerEventBus.onClientConnected("Mobile Controller")
        }
    }

    private fun startStateBroadcast(session: DefaultWebSocketSession) {
        stateBroadcastJob?.cancel()
        stateBroadcastJob = CoroutineScope(Dispatchers.IO).launch {
            MediaManager.playerStateFlow.collect { state ->
                state?.let {
                    val payload = json.encodeToString(it)
                    val message = WebSocketMessage("STATE_UPDATE", payload)
                    try {
                        session.send(Frame.Text(json.encodeToString(message)))
                    } catch (e: Exception) {
                        Log.e("RemoteEvent", "Failed to broadcast state to client", e)
                    }
                }
            }
        }
    }

    fun stopServer() {
        ServerEventBus.onRequestNewPin = null
        server?.stop(1_000, 2_000)
        server = null
        Log.i("RemoteEvent", "Server stopped")
    }
}
