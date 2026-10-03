package com.mobplayer.tv.server

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobplayer.tv.auth.AuthManager
import com.mobplayer.tv.models.UploadResponse
import com.mobplayer.tv.models.VideoMetadata
import com.mobplayer.tv.models.WebSocketMessage
import com.mobplayer.tv.repository.MediaRepository
import com.mobplayer.tv.repository.RemoteTextInput
import com.mobplayer.tv.repository.ServerRepository
import com.mobplayer.tv.storage.VideoUploadManager
import com.mobplayer.tv.viewmodel.ConnectionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Starts the real Ktor server on a free port and drives the REST API and /control WebSocket. */
@RunWith(AndroidJUnit4::class)
class KtorServerManagerTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var uploadDir: File
    private lateinit var authManager: AuthManager
    private lateinit var serverRepository: ServerRepository
    private lateinit var mediaRepository: MediaRepository
    private lateinit var uploadManager: VideoUploadManager
    private lateinit var server: KtorServerManager
    private var port = 0

    private val okHttp = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()
    private val openSockets = mutableListOf<WebSocket>()

    @Before
    fun setup() {
        uploadDir = File(context.cacheDir, "ktor_test_uploads_${System.nanoTime()}")
        authManager = AuthManager(context).apply { clearToken() }
        serverRepository = ServerRepository()
        mediaRepository = MediaRepository(context)
        uploadManager = VideoUploadManager(uploadDir)
        server = KtorServerManager(context, authManager, serverRepository, mediaRepository, uploadManager)
        port = ServerSocket(0).use { it.localPort }
        server.startServer(port)
        // Starts as "Mobile Controller"; clear it so pairing can be detected by onClientConnected
        serverRepository.onClientDisconnected()
        waitUntilUp()
    }

    @After
    fun tearDown() {
        openSockets.forEach { it.cancel() }
        server.stopServer()
        authManager.clearToken()
        uploadDir.deleteRecursively()
    }

    // ---- HTTP helpers ----

    private class HttpResult(val code: Int, val body: String, val headers: Map<String, List<String>>) {
        fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
    }

    private fun http(
        method: String,
        path: String,
        body: ByteArray? = null,
        headers: Map<String, String> = emptyMap()
    ): HttpResult {
        val conn = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 5_000
        conn.readTimeout = 10_000
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
        }
        val code = conn.responseCode
        val stream = if (code < 400) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return HttpResult(code, text, conn.headerFields.filterKeys { it != null }).also { conn.disconnect() }
    }

    private fun waitUntilUp() {
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            try {
                Socket("127.0.0.1", port).close()
                return
            } catch (e: Exception) {
                if (System.currentTimeMillis() > deadline) throw AssertionError("Server did not start on $port", e)
                Thread.sleep(50)
            }
        }
    }

    private fun upload(bytes: ByteArray, query: String): UploadResponse {
        val result = http("POST", "/api/upload?$query", bytes, mapOf("Content-Type" to "application/octet-stream"))
        assertEquals(result.body, 200, result.code)
        return json.decodeFromString(result.body)
    }

    private fun storedVideos(): List<VideoMetadata> = json.decodeFromString(http("GET", "/api/videos").body)

    // ---- WebSocket helpers ----

    private inner class Client {
        val messages = LinkedBlockingQueue<WebSocketMessage>()
        val closed = LinkedBlockingQueue<Int>()
        val socket: WebSocket = okHttp.newWebSocket(
            Request.Builder().url("ws://127.0.0.1:$port/control").build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    messages += json.decodeFromString<WebSocketMessage>(text)
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    closed += code
                    webSocket.close(code, null)
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    closed += -1
                }
            }
        ).also { openSockets += it }

        fun send(type: String, payload: String = "") {
            assertTrue(socket.send(json.encodeToString(WebSocketMessage(type, payload))))
        }

        /** Next message of [type], skipping others such as STATE_UPDATE. */
        fun await(type: String): WebSocketMessage {
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline) {
                val msg = messages.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS) ?: break
                if (msg.type == type) return msg
            }
            throw AssertionError("No $type message received")
        }
    }

    private fun currentPin(): String =
        (serverRepository.connectionEvent.value as ConnectionEvent.PinRequested).pin

    private fun pairNewClient(): Pair<Client, String> {
        val client = Client()
        client.send("PIN_SUBMIT", currentPin())
        val token = client.await("AUTH_SUCCESS").payload
        // AUTH_SUCCESS is sent just before the session becomes the active one
        waitFor { serverRepository.connectedDeviceName.value == "Mobile Controller" }
        return client to token
    }

    // ---- Tests: startup & static content ----

    @Test
    fun startupShowsPairingPin() {
        val event = serverRepository.connectionEvent.value
        assertTrue(event is ConnectionEvent.PinRequested)
        assertEquals(4, (event as ConnectionEvent.PinRequested).pin.length)
        assertNotNull(serverRepository.onRequestNewPin)
        assertNotNull(mediaRepository.onPlaybackProgressUpdate)
    }

    @Test
    fun stopServerClearsCallbacks() {
        server.stopServer()
        assertNull(serverRepository.onRequestNewPin)
        assertNull(serverRepository.onCloseSession)
        assertNull(mediaRepository.onPlaybackProgressUpdate)
        assertNull(mediaRepository.onPlaybackError)
    }

    @Test
    fun rootServesWebController() {
        val result = http("GET", "/")
        assertEquals(200, result.code)
        assertTrue(result.header("Content-Type")!!.startsWith("text/html"))
        assertEquals("no-store", result.header("Cache-Control"))
        assertTrue(result.body.contains("<title>MobPlayer TV Remote</title>"))
        assertEquals(result.body, http("GET", "/test_client.html").body)
    }

    @Test
    fun corsPreflightAllowsBrowserClients() {
        val result = http("OPTIONS", "/api/videos")
        assertEquals(200, result.code)
        assertEquals("*", result.header("Access-Control-Allow-Origin"))
        assertTrue(result.header("Access-Control-Allow-Methods")!!.contains("DELETE"))
        assertTrue(result.header("Access-Control-Allow-Headers")!!.contains("X-Auth-Token"))
    }

    // ---- Tests: REST API ----

    @Test
    fun videoListIsEmptyInitially() {
        val result = http("GET", "/api/videos")
        assertEquals(200, result.code)
        assertEquals("*", result.header("Access-Control-Allow-Origin"))
        assertTrue(storedVideos().isEmpty())
    }

    @Test
    fun uploadWithInvalidTokenIsRejected() {
        val result = http("POST", "/api/upload?play=false", ByteArray(10), mapOf("X-Auth-Token" to "bogus"))
        assertEquals(401, result.code)
        assertTrue(storedVideos().isEmpty())
    }

    @Test
    fun uploadWithValidTokenIsAccepted() {
        val token = authManager.generateAndSaveToken()
        val response = upload(ByteArray(64), "play=false&fileName=a.mp4&token=$token")
        assertEquals("success", response.status)
    }

    @Test
    fun rawUploadIsStoredAndListed() {
        val bytes = ByteArray(5_000) { (it % 251).toByte() }
        val response = upload(bytes, "play=false&fileName=holiday.mkv&title=Holiday%20Clip")

        assertEquals("success", response.status)
        assertEquals("Holiday Clip", response.title)
        assertEquals(5_000L, response.size)
        assertFalse(response.playedImmediately)
        assertEquals("/api/videos/${response.fileName}", response.videoUrl)
        assertFalse(serverRepository.isPlayerActive.value)

        val videos = storedVideos()
        assertEquals(1, videos.size)
        assertEquals("Holiday Clip", videos[0].title)
        assertEquals("video/x-matroska", videos[0].mimeType)
    }

    @Test
    fun storedVideoCanBeDownloadedAndStreamed() {
        val bytes = ByteArray(3_000) { (it % 97).toByte() }
        val response = upload(bytes, "play=false&fileName=clip.mp4")

        val download = URL("http://127.0.0.1:$port${response.videoUrl}").readBytes()
        assertArrayEquals(bytes, download)

        // Finished uploads fall back to the stored file
        val streamed = URL("http://127.0.0.1:$port${response.streamUrl}").readBytes()
        assertArrayEquals(bytes, streamed)
    }

    @Test
    fun unknownVideoAndStreamReturn404() {
        assertEquals(404, http("GET", "/api/videos/missing.mp4").code)
        assertEquals(404, http("GET", "/api/stream/upload_0").code)
        assertEquals(404, http("POST", "/api/videos/vid_missing/play").code)
        assertEquals(404, http("DELETE", "/api/videos/vid_missing").code)
    }

    @Test
    fun pathTraversalIsNotServed() {
        upload(ByteArray(10), "play=false&fileName=x.mp4")
        assertEquals(404, http("GET", "/api/videos/..%2F..%2Fshared_prefs%2Fauth_prefs.xml").code)
    }

    @Test
    fun uploadWithPlayImmediatelyOpensThePlayer() {
        val response = upload(ByteArray(1_000), "fileName=now.mp4&title=Now")

        assertTrue(response.playedImmediately)
        assertTrue(serverRepository.isPlayerActive.value)
        assertEquals("Now", serverRepository.activeMediaTitle.value)
        assertEquals(storedVideos().single().id, mediaRepository.currentMediaId.value)
    }

    @Test
    fun playStoredVideoResumesFromSavedPosition() {
        upload(ByteArray(100), "play=false&fileName=film.mp4&title=Film")
        val meta = storedVideos().single()
        uploadManager.updatePlaybackProgress(meta.id, 42_000L, 600_000L)

        val result = http("POST", "/api/videos/${meta.id}/play")
        assertEquals(200, result.code)
        assertTrue(result.body.contains("\"resumedAtMs\":42000"))
        assertTrue(serverRepository.isPlayerActive.value)
        assertEquals(meta.id, mediaRepository.currentMediaId.value)

        val fromStart = http("POST", "/api/videos/${meta.id}/play?resume=false")
        assertTrue(fromStart.body.contains("\"resumedAtMs\":0"))
    }

    @Test
    fun deleteVideoAndDeleteAll() {
        upload(ByteArray(10), "play=false&fileName=one.mp4")
        Thread.sleep(5) // upload ids are millisecond timestamps
        upload(ByteArray(10), "play=false&fileName=two.mp4")
        Thread.sleep(5)
        upload(ByteArray(10), "play=false&fileName=three.mp4")
        assertEquals(3, storedVideos().size)

        val first = storedVideos().first()
        val deleted = http("DELETE", "/api/videos/${first.id}")
        assertEquals(200, deleted.code)
        assertTrue(deleted.body.contains("\"deleted\":true"))
        assertEquals(2, storedVideos().size)

        val cleared = http("DELETE", "/api/videos")
        assertTrue(cleared.body.contains("\"deletedCount\":2"))
        assertTrue(storedVideos().isEmpty())
    }

    // ---- Tests: /control WebSocket pairing ----

    @Test
    fun unknownTokenIsAskedForPin() {
        val client = Client()
        client.send("AUTH_REQUEST", "not-a-token")
        client.await("PIN_REQUIRED")
    }

    @Test
    fun commandsBeforeAuthenticatingAreRefused() {
        val client = Client()
        client.send("PLAY")
        client.await("PIN_REQUIRED")
        assertFalse(serverRepository.isConnected.value)
    }

    @Test
    fun wrongPinIsRejectedAndSocketClosed() {
        val client = Client()
        val wrong = if (currentPin() == "0000") "1111" else "0000"
        client.send("PIN_SUBMIT", wrong)

        assertEquals("Invalid PIN", client.await("AUTH_FAILED").payload)
        assertNotNull(client.closed.poll(5, TimeUnit.SECONDS))
        assertFalse(serverRepository.isConnected.value)
    }

    @Test
    fun correctPinPairsAndIssuesToken() {
        val (_, token) = pairNewClient()

        assertTrue(authManager.isValidToken(token))
        waitFor { serverRepository.isConnected.value }
        assertEquals("Mobile Controller", serverRepository.connectedDeviceName.value)
        assertTrue(serverRepository.connectionEvent.value is ConnectionEvent.None)
    }

    @Test
    fun controllerDisconnectShowsPinAgain() {
        val (client, _) = pairNewClient()
        waitFor { serverRepository.isConnected.value }

        client.socket.close(1000, null)
        waitFor { serverRepository.connectionEvent.value is ConnectionEvent.PinRequested }
        assertFalse(serverRepository.isConnected.value)
        assertNull(serverRepository.connectedDeviceName.value)
    }

    @Test
    fun savedTokenReconnectsWithoutPin() {
        val (first, token) = pairNewClient()
        first.socket.close(1000, null)
        waitFor { !serverRepository.isConnected.value }

        val second = Client()
        second.send("AUTH_REQUEST", token)
        assertEquals(token, second.await("AUTH_SUCCESS").payload)
        waitFor { serverRepository.isConnected.value }
    }

    @Test
    fun secondDeviceTriggersConflictPromptAndCanBeRejected() {
        val (_, token) = pairNewClient()

        val second = Client()
        second.send("AUTH_REQUEST", token)
        waitFor { serverRepository.connectionEvent.value is ConnectionEvent.ConnectionConflict }

        (serverRepository.connectionEvent.value as ConnectionEvent.ConnectionConflict).onResolve(false)
        assertEquals("Connection rejected by TV", second.await("AUTH_FAILED").payload)
        assertTrue(serverRepository.isConnected.value)
    }

    @Test
    fun remoteTextIsForwardedToTheUi() = runBlocking {
        val (client, _) = pairNewClient()
        // Collector must be running before the text arrives (the flow has no replay)
        val received = async(Dispatchers.IO) {
            withTimeout(5_000) { serverRepository.remoteTextInput.first() }
        }
        Thread.sleep(200)
        client.send("TEXT_SUBMIT", "lofi hip hop")

        assertEquals(RemoteTextInput("lofi hip hop", submit = true), received.await())
        // The server posts the SEARCH action just after emitting the text, so it can lag the collector.
        waitFor { serverRepository.remoteActionEvent.value?.action == "SEARCH" }
    }

    @Test
    fun closeActiveSessionDisconnectsTheController() {
        val (client, _) = pairNewClient()
        server.closeActiveSession()
        assertNotNull(client.closed.poll(5, TimeUnit.SECONDS))
    }

    private fun waitFor(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Condition not met within ${timeoutMs}ms")
            Thread.sleep(25)
        }
    }
}
