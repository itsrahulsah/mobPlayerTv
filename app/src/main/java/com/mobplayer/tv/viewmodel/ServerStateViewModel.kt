package com.mobplayer.tv.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class ConnectionEvent {
    object None : ConnectionEvent()
    data class PinRequested(
        val pin: String,
        val serverIp: String? = null,
        val port: Int = 8080,
        val isEmulator: Boolean = false
    ) : ConnectionEvent()
    data class ConnectionConflict(val deviceName: String, val onResolve: (Boolean) -> Unit) : ConnectionEvent()
}

class ServerStateViewModel : ViewModel() {
    
    private val _connectionEvent = MutableStateFlow<ConnectionEvent>(ConnectionEvent.None)
    val connectionEvent: StateFlow<ConnectionEvent> = _connectionEvent

    fun showPin(pin: String) {
        _connectionEvent.value = ConnectionEvent.PinRequested(
            pin = pin,
            serverIp = com.mobplayer.tv.network.NetworkUtils.getLocalIpAddress(),
            port = 8080,
            isEmulator = com.mobplayer.tv.network.NetworkUtils.isEmulator()
        )
    }

    fun hideDialogs() {
        _connectionEvent.value = ConnectionEvent.None
    }

    fun promptConnectionConflict(deviceName: String, onResolve: (Boolean) -> Unit) {
        _connectionEvent.value = ConnectionEvent.ConnectionConflict(deviceName) { allowed ->
            onResolve(allowed)
            hideDialogs()
        }
    }
}
