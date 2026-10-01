package com.mobplayer.tv.server

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import com.mobplayer.tv.auth.AuthManager
import com.mobplayer.tv.repository.MediaRepository
import com.mobplayer.tv.models.PlayerCommandPayload
import com.mobplayer.tv.models.RemoteActionEvent
import com.mobplayer.tv.models.RemoteIconType
import com.mobplayer.tv.models.UploadResponse
import com.mobplayer.tv.models.VideoMetadata
import com.mobplayer.tv.models.WebSocketMessage
import com.mobplayer.tv.network.NetworkUtils
import com.mobplayer.tv.repository.ServerRepository
import com.mobplayer.tv.storage.VideoUploadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KtorServerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val serverRepository: ServerRepository,
    private val mediaRepository: MediaRepository,
    private val videoUploadManager: VideoUploadManager
) {
    private var server: ApplicationEngine? = null

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

                // Preflight CORS handler for browser REST requests
                options("/api/{...}") {
                    call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                    call.response.header(HttpHeaders.AccessControlAllowMethods, "GET, POST, DELETE, OPTIONS")
                    call.response.header(HttpHeaders.AccessControlAllowHeaders, "Content-Type, X-Auth-Token, X-Auth-PIN, Authorization, Range, X-File-Size, X-File-Name")
                    call.respond(HttpStatusCode.OK)
                }

                // 1. Upload video with progressive play-while-uploading
                post("/api/upload") {
                    call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                    val token = call.request.headers["X-Auth-Token"] ?: call.request.queryParameters["token"]
                    if (token != null && !authManager.isValidToken(token)) {
                        call.respond(HttpStatusCode.Unauthorized, "Invalid authentication token")
                        return@post
                    }

                    val playParam = call.request.queryParameters["play"] ?: call.request.queryParameters["playImmediately"]
                    var playImmediately = playParam?.toBooleanStrictOrNull() ?: true
                    var customTitle: String? = call.request.queryParameters["title"]
                    val totalSizeHeader = call.request.headers["X-File-Size"]?.toLongOrNull()
                        ?: call.request.queryParameters["totalSize"]?.toLongOrNull() ?: 0L

                    var activeUpload: VideoUploadManager.ActiveUpload? = null
                    var initialPlaybackTriggered = false

                    suspend fun processChunk(upload: VideoUploadManager.ActiveUpload, buffer: ByteArray, read: Int) {
                        videoUploadManager.writeChunk(upload.uploadId, buffer, read)

                        // Start playback as soon as initial buffer (64KB or half file) is written
                        val startThreshold = if (upload.totalSize in 1..131072L) {
                            minOf(16384L, upload.totalSize / 2)
                        } else {
                            65536L // 64 KB! Start right after reading container header/index!
                        }
                        if (playImmediately && !initialPlaybackTriggered && (upload.bytesWritten >= startThreshold || upload.bytesWritten >= 65536L)) {
                            initialPlaybackTriggered = true
                            Log.d("RemoteEvent", "⚡ Triggering progressive stream playback at ${upload.bytesWritten} bytes")
                            val streamUrl = "http://127.0.0.1:$port/api/stream/${upload.uploadId}"
                            withContext(Dispatchers.Main.immediate) {
                                serverRepository.openPlayer(upload.metadata.title)
                                mediaRepository.loadMedia(streamUrl, 0L, upload.metadata.id, upload.metadata.mimeType)
                                serverRepository.postRemoteAction(
                                    RemoteActionEvent("LOAD_MEDIA", "🎬 Streaming", upload.metadata.title, RemoteIconType.MEDIA)
                                )
                            }
                        }
                    }

                    try {
                        val isMultipart = try {
                            call.request.contentType().match(ContentType.MultiPart.FormData)
                        } catch (_: Exception) {
                            false
                        }

                        if (isMultipart) {
                            val multipart = call.receiveMultipart()
                            multipart.forEachPart { part ->
                                when (part) {
                                    is PartData.FormItem -> {
                                        if (part.name == "title") customTitle = part.value
                                        if (part.name == "play" || part.name == "playImmediately") {
                                            playImmediately = part.value.toBooleanStrictOrNull() ?: playImmediately
                                        }
                                    }
                                    is PartData.FileItem -> {
                                        val originalName = part.originalFileName ?: "uploaded_media"
                                        val detectedMime = VideoMetadata.resolveMimeType(originalName, part.contentType?.toString())
                                        val upload = videoUploadManager.createActiveUpload(
                                            originalFileName = originalName,
                                            customTitle = customTitle,
                                            totalSize = totalSizeHeader,
                                            mimeType = detectedMime
                                        )
                                        activeUpload = upload
                                        val channel = part.provider()
                                        val buffer = ByteArray(65536)
                                        while (true) {
                                            val read = channel.readAvailable(buffer, 0, buffer.size)
                                            if (read <= 0) break
                                            processChunk(upload, buffer, read)
                                        }
                                    }
                                    else -> Unit
                                }
                                part.dispose()
                            }
                        } else {
                            // Direct raw stream upload
                            val rawFileName = call.request.queryParameters["fileName"]
                                ?: call.request.queryParameters["filename"]
                                ?: call.request.headers["X-File-Name"]
                                ?: "uploaded_media.mp4"
                            val reqContentType = call.request.headers[HttpHeaders.ContentType]
                            val detectedMime = VideoMetadata.resolveMimeType(rawFileName, reqContentType)
                            val upload = videoUploadManager.createActiveUpload(
                                originalFileName = rawFileName,
                                customTitle = customTitle,
                                totalSize = totalSizeHeader,
                                mimeType = detectedMime
                            )
                            activeUpload = upload
                            val channel = call.receiveChannel()
                            val buffer = ByteArray(65536)
                            while (true) {
                                val read = channel.readAvailable(buffer, 0, buffer.size)
                                if (read <= 0) break
                                processChunk(upload, buffer, read)
                            }
                        }

                        val finalUpload = activeUpload
                        if (finalUpload != null) {
                            val finalMeta = videoUploadManager.markUploadCompleted(finalUpload.uploadId)
                            Log.d("RemoteEvent", "✅ Upload completed. initialPlaybackTriggered=$initialPlaybackTriggered, size=${finalMeta.fileSize}")

                            withContext(Dispatchers.Main.immediate) {
                                if (playImmediately && !initialPlaybackTriggered) {
                                    initialPlaybackTriggered = true
                                    serverRepository.openPlayer(finalMeta.title)
                                    mediaRepository.loadMedia("file://${finalUpload.file.absolutePath}", 0L, finalMeta.id, finalMeta.mimeType)
                                    serverRepository.postRemoteAction(
                                        RemoteActionEvent("LOAD_MEDIA", "🎬 Playing", finalMeta.title, RemoteIconType.MEDIA)
                                    )
                                } else {
                                    // The video is ALREADY streaming/playing! Do NOT interrupt ExoPlayer.
                                    serverRepository.postRemoteAction(
                                        RemoteActionEvent("UPLOAD_COMPLETE", "✅ Video Saved", finalMeta.title, RemoteIconType.MEDIA)
                                    )
                                }
                            }

                            val response = UploadResponse(
                                status = "success",
                                uploadId = finalUpload.uploadId,
                                fileName = finalMeta.fileName,
                                title = finalMeta.title,
                                size = finalMeta.fileSize,
                                videoUrl = "/api/videos/${finalMeta.fileName}",
                                streamUrl = "/api/stream/${finalUpload.uploadId}",
                                playedImmediately = playImmediately
                            )
                            call.respondText(json.encodeToString(response), ContentType.Application.Json)
                        } else {
                            call.respond(HttpStatusCode.BadRequest, "No file uploaded")
                        }
                    } catch (e: Exception) {
                        Log.e("RemoteEvent", "Error processing file upload: ${e.message}", e)
                        activeUpload?.let { videoUploadManager.markUploadFailed(it.uploadId, e) }
                        call.respond(HttpStatusCode.InternalServerError, "Upload failed: ${e.message}")
                    }
                }

                // 2. Progressive HTTP streaming for growing files (with fallback to completed video files)
                get("/api/stream/{uploadId}") {
                    call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                    val uploadId = call.parameters["uploadId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing upload ID")
                    val upload = videoUploadManager.getActiveUpload(uploadId)
                    if (upload == null) {
                        // Fallback: Check if the video has completed upload and is saved on disk
                        val completedFile = videoUploadManager.findCompletedVideoFileByUploadId(uploadId)
                        if (completedFile != null && completedFile.exists()) {
                            call.respondFile(completedFile)
                            return@get
                        }
                        return@get call.respond(HttpStatusCode.NotFound, "Active upload stream not found")
                    }

                    val rangeHeader = call.request.headers[HttpHeaders.Range]
                    val totalLength = if (upload.totalSize > 0) {
                        upload.totalSize
                    } else if (upload.isCompleted) {
                        upload.file.length()
                    } else {
                        -1L
                    }

                    val (start, end) = if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                        val rangeSpec = rangeHeader.removePrefix("bytes=").trim()
                        val parts = rangeSpec.split("-")
                        val rStart = parts[0].toLongOrNull() ?: 0L
                        val rEnd = if (parts.size > 1 && parts[1].isNotEmpty()) {
                            parts[1].toLongOrNull() ?: if (totalLength > 0) (totalLength - 1).coerceAtLeast(0L) else Long.MAX_VALUE - 1
                        } else {
                            if (totalLength > 0) (totalLength - 1).coerceAtLeast(0L) else Long.MAX_VALUE - 1
                        }
                        Pair(rStart, rEnd)
                    } else {
                        Pair(0L, if (totalLength > 0) (totalLength - 1).coerceAtLeast(0L) else Long.MAX_VALUE - 1)
                    }

                    val statusCode = if (rangeHeader != null && totalLength > 0) HttpStatusCode.PartialContent else HttpStatusCode.OK
                    val contentLength = if (totalLength > 0) (end - start + 1).coerceAtLeast(0L) else null

                    call.response.header(HttpHeaders.AcceptRanges, "bytes")
                    if (rangeHeader != null && totalLength > 0) {
                        call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$totalLength")
                    }

                    call.respondOutputStream(
                        contentType = ContentType.parse(upload.metadata.mimeType),
                        status = statusCode,
                        contentLength = contentLength
                    ) {
                        withContext(Dispatchers.IO) {
                            val randomAccessFile = RandomAccessFile(upload.file, "r")
                            try {
                                randomAccessFile.seek(start)
                                var currentPos = start
                                val buffer = ByteArray(65536)

                                while (currentPos <= end) {
                                    val available = upload.bytesWritten
                                    if (currentPos >= available) {
                                        if (upload.isCompleted) break
                                        if (upload.error != null) throw upload.error!!
                                        delay(40)
                                        continue
                                    }

                                    val toRead = minOf(buffer.size.toLong(), available - currentPos, end - currentPos + 1).toInt()
                                    if (toRead <= 0) {
                                        if (upload.isCompleted) break
                                        delay(40)
                                        continue
                                    }

                                    val read = randomAccessFile.read(buffer, 0, toRead)
                                    if (read > 0) {
                                        write(buffer, 0, read)
                                        flush()
                                        currentPos += read
                                    } else {
                                        if (upload.isCompleted) break
                                        delay(40)
                                    }
                                }
                            } finally {
                                try { randomAccessFile.close() } catch (_: Exception) {}
                            }
                        }
                    }
                }

                // 3. List all stored videos with watch progress
                get("/api/videos") {
                    call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                    val videos = videoUploadManager.uploadedVideosFlow.value
                    call.respondText(json.encodeToString(videos), ContentType.Application.Json)
                }

                // 4. Stream or download completed video file
                get("/api/videos/{fileName}") {
                    call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                    val fileName = call.parameters["fileName"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing filename")
                    val file = videoUploadManager.getVideoFile(fileName)
                    if (file != null && file.exists()) {
                        call.respondFile(file)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Video not found")
                    }
                }

                // 5. Play or resume previously uploaded video
                post("/api/videos/{id}/play") {
                    call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                    val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing video ID")
                    val meta = videoUploadManager.getVideoMetadata(id) ?: return@post call.respond(HttpStatusCode.NotFound, "Video not found")
                    val file = videoUploadManager.getVideoFile(meta.fileName) ?: return@post call.respond(HttpStatusCode.NotFound, "Video file missing on TV")

                    val resumeParam = call.request.queryParameters["resume"]?.toBooleanStrictOrNull() ?: true
                    val startPos = if (resumeParam && !meta.isCompleted && meta.lastPlayedPositionMs > 5000L) {
                        meta.lastPlayedPositionMs
                    } else {
                        0L
                    }

                    withContext(Dispatchers.Main.immediate) {
                        serverRepository.openPlayer(meta.title)
                        mediaRepository.loadMedia("file://${file.absolutePath}", startPos, meta.id, meta.mimeType)
                        val resumeText = if (startPos > 0) " (Resumed)" else ""
                        serverRepository.postRemoteAction(
                            RemoteActionEvent("PLAY", "🎬 Playing$resumeText", meta.title, RemoteIconType.PLAY)
                        )
                    }

                    call.respondText("{\"status\":\"success\",\"resumedAtMs\":$startPos}", ContentType.Application.Json)
                }

                // 6. Delete a specific video
                delete("/api/videos/{id}") {
                    call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                    val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing video ID")
                    val deleted = videoUploadManager.deleteVideo(id)
                    if (deleted) {
                        call.respondText("{\"status\":\"success\",\"deleted\":true}", ContentType.Application.Json)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Video not found or already deleted")
                    }
                }

                // 7. Clear all uploaded videos
                delete("/api/videos") {
                    call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                    val count = videoUploadManager.deleteAllVideos()
                    call.respondText("{\"status\":\"success\",\"deletedCount\":$count}", ContentType.Application.Json)
                }

                webSocket("/control") {
                    val clientInfo = this.call.request.local.let { "${it.remoteHost}:${it.serverPort}" }
                    Log.i("RemoteEvent", "==================================================")
                    Log.i("RemoteEvent", "🌐 [Client Connected] WebSocket connection established from: $clientInfo")
                    serverRepository.logRemoteEvent("Connection", "Client connected from $clientInfo")

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
                            serverRepository.onClientDisconnected()
                            serverRepository.showPin(currentPin)
                        }
                    }
                }
            }
        }.start(wait = false)

        // Connect media progress tracking and error listeners
        mediaRepository.onPlaybackProgressUpdate = { id, pos, dur ->
            videoUploadManager.updatePlaybackProgress(id, pos, dur)
        }
        mediaRepository.onPlaybackError = { error ->
            serverRepository.postRemoteAction(
                RemoteActionEvent("ERROR", "Playback Error", error.message ?: "Failed to play video", RemoteIconType.INFO)
            )
        }

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
        serverRepository.logRemoteEvent("Server", "🚀 Socket server UP at IP: $displayIp, Port: $port | Endpoint: $wsEndpoint")

        serverRepository.onRequestNewPin = {
            currentPin = authManager.generatePin()
            currentPin
        }

        serverRepository.onCloseSession = {
            closeActiveSession()
        }

        // Show PIN on TV on startup
        currentPin = authManager.generatePin()
        serverRepository.showPin(currentPin)
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
                                serverRepository.hideDialogs()
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
                    if (activeSession != session) {
                        Log.w("RemoteEvent", "⚠️ Ignoring message: session is no longer the active session")
                        session.close(CloseReason(CloseReason.Codes.NORMAL, "Session closed"))
                        return
                    }
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
            "PLAY", "PAUSE", "TOGGLE_PLAY_PAUSE", "PLAY_PAUSE", "STOP", "SEEK",
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

        withContext(Dispatchers.Main.immediate) {
            when (action) {
                "PLAY", "RESUME" -> {
                    mediaRepository.play()
                    val title = serverRepository.activeMediaTitle.value.ifEmpty { "Streaming Media" }
                    serverRepository.openPlayer(title)
                    serverRepository.postRemoteAction(
                        RemoteActionEvent("PLAY", "Playing", "Playback started", RemoteIconType.PLAY)
                    )
                    Log.i("RemoteEvent", "▶ [Action Complete] PLAY executed")
                }

                "PAUSE" -> {
                    mediaRepository.pause()
                    serverRepository.postRemoteAction(
                        RemoteActionEvent("PAUSE", "Paused", "Playback paused", RemoteIconType.PAUSE)
                    )
                    Log.i("RemoteEvent", "⏸ [Action Complete] PAUSE executed")
                }

                "TOGGLE_PLAY_PAUSE", "PLAY_PAUSE" -> {
                    val isPlaying = mediaRepository.player?.isPlaying == true
                    if (isPlaying) {
                        mediaRepository.pause()
                        serverRepository.postRemoteAction(
                            RemoteActionEvent("PAUSE", "Paused", "Playback paused", RemoteIconType.PAUSE)
                        )
                    } else {
                        mediaRepository.play()
                        val title = serverRepository.activeMediaTitle.value.ifEmpty { "Streaming Media" }
                        serverRepository.openPlayer(title)
                        serverRepository.postRemoteAction(
                            RemoteActionEvent("PLAY", "Playing", "Playback started", RemoteIconType.PLAY)
                        )
                    }
                    Log.i("RemoteEvent", "⏯ [Action Complete] TOGGLE_PLAY_PAUSE executed (wasPlaying=$isPlaying)")
                }

                "STOP" -> {
                    mediaRepository.stop()
                    serverRepository.closePlayer()
                    serverRepository.postRemoteAction(
                        RemoteActionEvent("STOP", "Stopped", "Returned to TV Home", RemoteIconType.HOME)
                    )
                    Log.i("RemoteEvent", "⏹ [Action Complete] STOP executed")
                }

                "SEEK" -> {
                    val seekTo = payload?.seekToMs ?: payload?.positionMs ?: 0L
                    mediaRepository.seekTo(seekTo)
                    serverRepository.postRemoteAction(
                        RemoteActionEvent("SEEK", "Seek", formatTime(seekTo), RemoteIconType.SEEK_FORWARD)
                    )
                    Log.i("RemoteEvent", "⏩ [Action Complete] SEEK to ${seekTo}ms executed")
                }

                "SEEK_FORWARD", "FORWARD", "FAST_FORWARD" -> {
                    val currentPos = mediaRepository.player?.currentPosition ?: 0L
                    val newPos = currentPos + 10_000L
                    mediaRepository.seekTo(newPos)
                    serverRepository.postRemoteAction(
                        RemoteActionEvent("SEEK_FORWARD", "Forward +10s", formatTime(newPos), RemoteIconType.SEEK_FORWARD)
                    )
                    Log.i("RemoteEvent", "⏩ [Action Complete] SEEK_FORWARD (+10s to ${newPos}ms)")
                }

                "SEEK_BACKWARD", "REWIND", "BACKWARD" -> {
                    val currentPos = mediaRepository.player?.currentPosition ?: 0L
                    val newPos = (currentPos - 10_000L).coerceAtLeast(0L)
                    mediaRepository.seekTo(newPos)
                    serverRepository.postRemoteAction(
                        RemoteActionEvent("SEEK_BACKWARD", "Rewind -10s", formatTime(newPos), RemoteIconType.SEEK_BACKWARD)
                    )
                    Log.i("RemoteEvent", "⏪ [Action Complete] SEEK_BACKWARD (-10s to ${newPos}ms)")
                }

                "SET_VOLUME" -> {
                    val vol = (payload?.volume ?: 0.5f).coerceIn(0f, 1f)
                    mediaRepository.setVolume(vol)
                    serverRepository.postRemoteAction(
                        RemoteActionEvent("SET_VOLUME", "Volume ${(vol * 100).toInt()}%", null, RemoteIconType.VOLUME_UP)
                    )
                    Log.i("RemoteEvent", "🔊 [Action Complete] SET_VOLUME: $vol")
                }

                "VOLUME_UP" -> {
                    val curVol = mediaRepository.player?.volume ?: 1f
                    val newVol = (curVol + 0.05f).coerceAtMost(1f)
                    mediaRepository.setVolume(newVol)
                    serverRepository.postRemoteAction(
                        RemoteActionEvent("VOLUME_UP", "Volume ${(newVol * 100).toInt()}%", null, RemoteIconType.VOLUME_UP)
                    )
                    Log.i("RemoteEvent", "🔊 [Action Complete] VOLUME_UP: $newVol")
                }

                "VOLUME_DOWN" -> {
                    val curVol = mediaRepository.player?.volume ?: 1f
                    val newVol = (curVol - 0.05f).coerceAtLeast(0f)
                    mediaRepository.setVolume(newVol)
                    serverRepository.postRemoteAction(
                        RemoteActionEvent("VOLUME_DOWN", "Volume ${(newVol * 100).toInt()}%", null, RemoteIconType.VOLUME_DOWN)
                    )
                    Log.i("RemoteEvent", "🔉 [Action Complete] VOLUME_DOWN: $newVol")
                }

                "MUTE" -> {
                    val curVol = mediaRepository.player?.volume ?: 1f
                    if (curVol > 0f) {
                        lastNonZeroVolume = curVol
                        mediaRepository.setVolume(0f)
                        serverRepository.postRemoteAction(
                            RemoteActionEvent("MUTE", "Muted", "Volume 0%", RemoteIconType.MUTE)
                        )
                        Log.i("RemoteEvent", "🔇 [Action Complete] MUTED")
                    } else {
                        val restoreVol = if (lastNonZeroVolume > 0f) lastNonZeroVolume else 0.8f
                        mediaRepository.setVolume(restoreVol)
                        serverRepository.postRemoteAction(
                            RemoteActionEvent("UNMUTE", "Unmuted", "Volume ${(restoreVol * 100).toInt()}%", RemoteIconType.VOLUME_UP)
                        )
                        Log.i("RemoteEvent", "🔊 [Action Complete] UNMUTED to $restoreVol")
                    }
                }

                // D-Pad Navigation Keys: navigate immediately without HUD animation lag
                "DPAD_UP", "UP" -> {
                    serverRepository.injectKey(KeyEvent.KEYCODE_DPAD_UP)
                    Log.i("RemoteEvent", "▲ [Action Complete] DPAD_UP injected")
                }

                "DPAD_DOWN", "DOWN" -> {
                    serverRepository.injectKey(KeyEvent.KEYCODE_DPAD_DOWN)
                    Log.i("RemoteEvent", "▼ [Action Complete] DPAD_DOWN injected")
                }

                "DPAD_LEFT", "LEFT" -> {
                    if (serverRepository.isPlayerActive.value) {
                        val currentPos = mediaRepository.player?.currentPosition ?: 0L
                        val newPos = (currentPos - 10_000L).coerceAtLeast(0L)
                        mediaRepository.seekTo(newPos)
                        serverRepository.postRemoteAction(
                            RemoteActionEvent("SEEK_BACKWARD", "Rewind -10s", formatTime(newPos), RemoteIconType.SEEK_BACKWARD)
                        )
                    } else {
                        serverRepository.injectKey(KeyEvent.KEYCODE_DPAD_LEFT)
                    }
                    Log.i("RemoteEvent", "◄ [Action Complete] DPAD_LEFT handled")
                }

                "DPAD_RIGHT", "RIGHT" -> {
                    if (serverRepository.isPlayerActive.value) {
                        val currentPos = mediaRepository.player?.currentPosition ?: 0L
                        val newPos = currentPos + 10_000L
                        mediaRepository.seekTo(newPos)
                        serverRepository.postRemoteAction(
                            RemoteActionEvent("SEEK_FORWARD", "Forward +10s", formatTime(newPos), RemoteIconType.SEEK_FORWARD)
                        )
                    } else {
                        serverRepository.injectKey(KeyEvent.KEYCODE_DPAD_RIGHT)
                    }
                    Log.i("RemoteEvent", "► [Action Complete] DPAD_RIGHT handled")
                }

                "DPAD_CENTER", "SELECT", "ENTER", "OK" -> {
                    if (serverRepository.isPlayerActive.value) {
                        val isPlaying = mediaRepository.player?.isPlaying == true
                        if (isPlaying) {
                            mediaRepository.pause()
                            serverRepository.postRemoteAction(
                                RemoteActionEvent("PAUSE", "Paused", "Playback paused", RemoteIconType.PAUSE)
                            )
                        } else {
                            mediaRepository.play()
                            serverRepository.postRemoteAction(
                                RemoteActionEvent("PLAY", "Playing", "Playback resumed", RemoteIconType.PLAY)
                            )
                        }
                    } else {
                        serverRepository.injectKey(KeyEvent.KEYCODE_DPAD_CENTER)
                    }
                    Log.i("RemoteEvent", "🔘 [Action Complete] DPAD_CENTER / SELECT handled")
                }

                "BACK" -> {
                    if (serverRepository.isPlayerActive.value) {
                        mediaRepository.stop()
                        serverRepository.closePlayer()
                        serverRepository.postRemoteAction(
                            RemoteActionEvent("BACK", "Exit to TV Home", null, RemoteIconType.BACK)
                        )
                    } else {
                        serverRepository.injectKey(KeyEvent.KEYCODE_BACK)
                        serverRepository.postRemoteAction(
                            RemoteActionEvent("BACK", "↩ Back", null, RemoteIconType.BACK)
                        )
                    }
                    Log.i("RemoteEvent", "↩ [Action Complete] BACK handled")
                }

                "HOME" -> {
                    mediaRepository.stop()
                    serverRepository.closePlayer()
                    serverRepository.postRemoteAction(
                        RemoteActionEvent("HOME", "🏠 TV Home", null, RemoteIconType.HOME)
                    )
                    Log.i("RemoteEvent", "🏠 [Action Complete] HOME handled")
                }

                else -> {
                    Log.w("RemoteEvent", "⚠️ Unhandled action command: '$action'")
                    serverRepository.postRemoteAction(
                        RemoteActionEvent(action, action, "Remote Action", RemoteIconType.INFO)
                    )
                }
            }
        }

        // Acknowledge action execution to the controller asynchronously so the incoming loop isn't blocked
        CoroutineScope(Dispatchers.IO).launch {
            try {
                session.send(Frame.Text(json.encodeToString(WebSocketMessage("COMMAND_SUCCESS", action))))
            } catch (_: Exception) {}
        }
    }

    private suspend fun handleLoadMedia(payload: String, session: DefaultWebSocketSession) {
        val (url, title) = try {
            val parsed = json.decodeFromString<PlayerCommandPayload>(payload)
            Pair(parsed.url ?: payload, parsed.title ?: "Remote Media Stream")
        } catch (e: Exception) {
            Pair(payload, "Remote Media Stream")
        }

        Log.i("RemoteEvent", "🎬 [Loading Media] URL='$url', Title='$title'")

        withContext(Dispatchers.Main.immediate) {
            serverRepository.openPlayer(title)
            mediaRepository.loadMedia(url)
            serverRepository.postRemoteAction(
                RemoteActionEvent("LOAD_MEDIA", "🎬 Loading Media", title, RemoteIconType.MEDIA)
            )
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                session.send(Frame.Text(json.encodeToString(WebSocketMessage("COMMAND_SUCCESS", "LOAD_MEDIA"))))
            } catch (_: Exception) {}
        }
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
            withContext(Dispatchers.Main.immediate) {
                serverRepository.injectKey(keyCode)
            }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    session.send(Frame.Text(json.encodeToString(WebSocketMessage("COMMAND_SUCCESS", "KEY_EVENT"))))
                } catch (_: Exception) {}
            }
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
            serverRepository.promptConnectionConflict("A new device") { result ->
                allowed.complete(result)
            }

            if (allowed.await()) {
                activeSession?.close()
                activeSession = session
                startStateBroadcast(session)
                serverRepository.onClientConnected("Mobile Controller")
            } else {
                session.send(Frame.Text(json.encodeToString(WebSocketMessage("AUTH_FAILED", "Connection rejected by TV"))))
                session.close()
                return
            }
        } else {
            activeSession = session
            startStateBroadcast(session)
            serverRepository.hideDialogs()
            serverRepository.onClientConnected("Mobile Controller")
        }
    }

    private fun startStateBroadcast(session: DefaultWebSocketSession) {
        stateBroadcastJob?.cancel()
        stateBroadcastJob = CoroutineScope(Dispatchers.IO).launch {
            mediaRepository.playerStateFlow.collect { state ->
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

    fun closeActiveSession() {
        val session = activeSession
        activeSession = null
        stateBroadcastJob?.cancel()
        stateBroadcastJob = null
        if (session != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    session.close(CloseReason(CloseReason.Codes.NORMAL, "Disconnected by TV"))
                } catch (e: Exception) {
                    Log.e("RemoteEvent", "Error closing active session: ${e.message}")
                }
            }
        }
    }

    fun stopServer() {
        mediaRepository.onPlaybackProgressUpdate = null
        mediaRepository.onPlaybackError = null
        serverRepository.onRequestNewPin = null
        serverRepository.onCloseSession = null
        closeActiveSession()
        server?.stop(1_000, 2_000)
        server = null
        Log.i("RemoteEvent", "Server stopped")
    }
}
