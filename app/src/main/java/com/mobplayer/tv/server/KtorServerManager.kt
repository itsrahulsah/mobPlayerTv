package com.mobplayer.tv.server

import android.content.Context
import android.util.Log
import com.mobplayer.tv.auth.AuthManager
import com.mobplayer.tv.models.WebSocketMessage
import com.mobplayer.tv.viewmodel.ServerEventBus
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration

class KtorServerManager(private val context: Context) {
    private var server: ApplicationEngine? = null
    private val authManager = AuthManager(context)
    
    // Maintain a single active controller session
    private var activeSession: DefaultWebSocketSession? = null
    
    private var currentPin = ""

    fun startServer(port: Int = 8080) {
        if (server != null) return
        
        server = embeddedServer(CIO, port = port) {
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(15)
                timeout = Duration.ofSeconds(15)
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }
            
            routing {
                webSocket("/control") {
                    Log.d("KtorServer", "Client connected: $this")
                    
                    try {
                        handleClientConnection(this)
                    } catch (e: ClosedReceiveChannelException) {
                        Log.d("KtorServer", "Client closed connection")
                    } catch (e: Exception) {
                        Log.e("KtorServer", "Error in websocket connection", e)
                    } finally {
                        if (activeSession == this) {
                            activeSession = null
                            Log.d("KtorServer", "Active controller disconnected")
                            // Show the PIN again since there are no active controllers
                            ServerEventBus.showPin(currentPin)
                        }
                    }
                }
            }
        }.start(wait = false)
        Log.d("KtorServer", "Server started on port $port")
        
        // Show PIN on TV on startup
        currentPin = authManager.generatePin()
        ServerEventBus.showPin(currentPin)
    }

    private suspend fun handleClientConnection(session: DefaultWebSocketSession) {
        var isAuthenticated = false

        for (frame in session.incoming) {
            frame as? Frame.Text ?: continue
            val receivedText = frame.readText()
            
            try {
                val message = Json.decodeFromString<WebSocketMessage>(receivedText)
                
                if (!isAuthenticated) {
                    when (message.type) {
                        "AUTH_REQUEST" -> {
                            // Check token in payload
                            if (authManager.isValidToken(message.payload)) {
                                isAuthenticated = true
                                handleAuthenticatedSession(session)
                            } else {
                                // Tell client they need to submit the PIN shown on TV
                                session.send(Frame.Text(Json.encodeToString(WebSocketMessage("PIN_REQUIRED", ""))))
                            }
                        }
                        "PIN_SUBMIT" -> {
                            if (authManager.verifyPin(currentPin, message.payload)) {
                                isAuthenticated = true
                                ServerEventBus.hideDialogs()
                                val token = authManager.generateAndSaveToken()
                                session.send(Frame.Text(Json.encodeToString(WebSocketMessage("AUTH_SUCCESS", token))))
                                handleAuthenticatedSession(session)
                                
                                // Rotate PIN so the same one can't be reused later
                                currentPin = authManager.generatePin()
                            } else {
                                session.send(Frame.Text(Json.encodeToString(WebSocketMessage("AUTH_FAILED", "Invalid PIN"))))
                                session.close()
                                return
                            }
                        }
                    }
                } else {
                        Log.d("KtorServer", "Command received: ${message.type}")
                        when (message.type) {
                            "COMMAND" -> {
                                val command = Json.decodeFromString<com.mobplayer.tv.models.PlayerCommandPayload>(message.payload)
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    when (command.action) {
                                        "PLAY" -> com.mobplayer.tv.media.MediaManager.play()
                                        "PAUSE" -> com.mobplayer.tv.media.MediaManager.pause()
                                        "SEEK" -> command.seekToMs?.let { com.mobplayer.tv.media.MediaManager.seekTo(it) }
                                        else -> Log.w("KtorServer", "Unknown action: ${command.action}")
                                    }
                                }
                                session.send(Frame.Text(Json.encodeToString(WebSocketMessage("COMMAND_SUCCESS", command.action))))
                            }
                            "LOAD_MEDIA" -> {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    com.mobplayer.tv.media.MediaManager.loadMedia(message.payload)
                                }
                                session.send(Frame.Text(Json.encodeToString(WebSocketMessage("COMMAND_SUCCESS", "LOAD_MEDIA"))))
                            }
                            else -> Log.w("KtorServer", "Unknown message type: ${message.type}")
                        }
                }
            } catch (e: Exception) {
                Log.e("KtorServer", "Failed to parse message", e)
            }
        }
    }

    private var stateBroadcastJob: kotlinx.coroutines.Job? = null

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
            } else {
                session.close()
                return
            }
        } else {
            activeSession = session
            startStateBroadcast(session)
            ServerEventBus.hideDialogs()
        }
    }

    private fun startStateBroadcast(session: DefaultWebSocketSession) {
        stateBroadcastJob?.cancel()
        stateBroadcastJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            com.mobplayer.tv.media.MediaManager.playerStateFlow.collect { state ->
                state?.let {
                    val payload = Json.encodeToString(it)
                    val message = WebSocketMessage("STATE_UPDATE", payload)
                    try {
                        session.send(Frame.Text(Json.encodeToString(message)))
                    } catch (e: Exception) {
                        Log.e("KtorServer", "Failed to broadcast state", e)
                    }
                }
            }
        }
    }

    fun stopServer() {
        server?.stop(1_000, 2_000)
        server = null
        Log.d("KtorServer", "Server stopped")
    }
}
