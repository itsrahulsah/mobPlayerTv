package com.mobplayer.tv.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class ConnectionEvent {
    object None : ConnectionEvent()
    data class PinRequested(val pin: String) : ConnectionEvent()
    data class ConnectionConflict(val deviceName: String, val onResolve: (Boolean) -> Unit) : ConnectionEvent()
}

class ServerStateViewModel : ViewModel() {
    
    private val _connectionEvent = MutableStateFlow<ConnectionEvent>(ConnectionEvent.None)
    val connectionEvent: StateFlow<ConnectionEvent> = _connectionEvent

    fun showPin(pin: String) {
        _connectionEvent.value = ConnectionEvent.PinRequested(pin)
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
