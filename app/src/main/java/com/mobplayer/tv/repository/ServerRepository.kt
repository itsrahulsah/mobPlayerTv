package com.mobplayer.tv.repository

import android.util.Log
import com.mobplayer.tv.models.RemoteActionEvent
import com.mobplayer.tv.viewmodel.ConnectionEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepository internal constructor(
    /** Runs the remote-action HUD dismiss timer; tests pass a virtual-time scope. */
    private val scope: CoroutineScope
) {
    @Inject
    constructor() : this(CoroutineScope(Dispatchers.Default + SupervisorJob()))

    private var actionDismissJob: Job? = null

    private val _connectionEvent = MutableStateFlow<ConnectionEvent>(ConnectionEvent.None)
    val connectionEvent: StateFlow<ConnectionEvent> = _connectionEvent

    private val _isConnected = MutableStateFlow<Boolean>(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _connectedDeviceName = MutableStateFlow<String?>("Mobile Controller")
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName

    private val _activeMediaTitle = MutableStateFlow<String>("")
    val activeMediaTitle: StateFlow<String> = _activeMediaTitle

    private val _isPlayerActive = MutableStateFlow<Boolean>(false)
    val isPlayerActive: StateFlow<Boolean> = _isPlayerActive

    /**
     * True while the user navigates UI on top of playing media (suggestions row, search over a
     * minimized video): remote D-pad/OK/Back then go to the UI instead of seek/play-pause/close.
     */
    private val _isUiNavigating = MutableStateFlow(false)
    val isUiNavigating: StateFlow<Boolean> = _isUiNavigating

    fun setUiNavigating(navigating: Boolean) {
        _isUiNavigating.value = navigating
    }

    private val _remoteActionEvent = MutableStateFlow<RemoteActionEvent?>(null)
    val remoteActionEvent: StateFlow<RemoteActionEvent?> = _remoteActionEvent

    var onInjectKeyEvent: ((Int) -> Unit)? = null

    /** Text typed on a remote (TEXT_INPUT / TEXT_SUBMIT); the UI routes it to the search field. */
    private val _remoteTextInput = MutableSharedFlow<RemoteTextInput>(extraBufferCapacity = 64)
    val remoteTextInput: SharedFlow<RemoteTextInput> = _remoteTextInput

    fun postRemoteText(text: String, submit: Boolean) {
        _remoteTextInput.tryEmit(RemoteTextInput(text, submit))
    }
    var onRequestNewPin: (() -> String)? = null
    var onCloseSession: (() -> Unit)? = null

    fun closeActiveSession() {
        onCloseSession?.invoke()
    }

    fun showPin(pin: String) {
        val isEmu = com.mobplayer.tv.network.NetworkUtils.isEmulator()
        val ip = if (isEmu) "10.0.2.2" else com.mobplayer.tv.network.NetworkUtils.getLocalIpAddress()
        _connectionEvent.value = ConnectionEvent.PinRequested(
            pin = pin,
            serverIp = ip,
            port = 8080,
            isEmulator = isEmu
        )
        _isConnected.value = false
    }

    fun requestPin(): String {
        val pin = onRequestNewPin?.invoke() ?: (1000..9999).random().toString()
        showPin(pin)
        return pin
    }

    fun promptConnectionConflict(deviceName: String, onResolve: (Boolean) -> Unit) {
        _connectionEvent.value = ConnectionEvent.ConnectionConflict(deviceName) { allowed ->
            onResolve(allowed)
            _connectionEvent.value = ConnectionEvent.None
        }
    }

    fun hideDialogs() {
        _connectionEvent.value = ConnectionEvent.None
        _isConnected.value = true
    }

    fun onClientConnected(deviceName: String = "Mobile Controller") {
        _isConnected.value = true
        _connectedDeviceName.value = deviceName
        _connectionEvent.value = ConnectionEvent.None
        logRemoteEvent("Server", "Client authenticated successfully: $deviceName")
    }

    fun onClientDisconnected() {
        _isConnected.value = false
        _connectedDeviceName.value = null
        logRemoteEvent("Server", "Client disconnected")
    }

    fun openPlayer(title: String = "Streaming Media") {
        _activeMediaTitle.value = title
        _isPlayerActive.value = true
        logRemoteEvent("Player", "Player opened with title: $title")
    }

    fun closePlayer() {
        _activeMediaTitle.value = ""
        _isPlayerActive.value = false
        logRemoteEvent("Player", "Player closed, returned to TV Home")
    }

    fun setActiveMediaTitle(title: String) {
        _activeMediaTitle.value = title
    }

    fun enterDemoMode() {
        _isConnected.value = true
        _connectedDeviceName.value = "Demo Controller"
        _connectionEvent.value = ConnectionEvent.None
        logRemoteEvent("Server", "Entered Demo Mode")
    }

    fun postRemoteAction(event: RemoteActionEvent) {
        logRemoteEvent("RemoteAction", "⚡ TV Remote Action: ${event.displayName} (${event.action}) - ${event.details ?: "No details"}")
        _remoteActionEvent.value = event
        actionDismissJob?.cancel()
        actionDismissJob = scope.launch {
            delay(REMOTE_ACTION_DISMISS_MS)
            if (_remoteActionEvent.value == event) {
                _remoteActionEvent.value = null
            }
        }
    }

    fun injectKey(keyCode: Int) {
        val hasAndroidLooper = try {
            android.os.Build.VERSION.SDK_INT > 0 && android.os.Looper.getMainLooper() != null
        } catch (_: Throwable) {
            false
        }

        if (hasAndroidLooper) {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                onInjectKeyEvent?.invoke(keyCode)
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onInjectKeyEvent?.invoke(keyCode)
                }
            }
        } else {
            // Unit test / JVM environment
            onInjectKeyEvent?.invoke(keyCode)
        }
    }

    fun logRemoteEvent(tag: String, message: String) {
        try {
            Log.i("RemoteEvent", "[$tag] $message")
        } catch (_: Throwable) {
            println("[RemoteEvent] [$tag] $message")
        }
    }
}

/** How long a remote action stays on the HUD before it is cleared. */
internal const val REMOTE_ACTION_DISMISS_MS = 2_800L

/** @param submit true when the remote pressed Enter/Search rather than just editing the text. */
data class RemoteTextInput(val text: String, val submit: Boolean)
