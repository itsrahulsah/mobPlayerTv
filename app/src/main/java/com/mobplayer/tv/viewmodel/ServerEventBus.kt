package com.mobplayer.tv.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ServerEventBus {
    private val _connectionEvent = MutableStateFlow<ConnectionEvent>(ConnectionEvent.None)
    val connectionEvent: StateFlow<ConnectionEvent> = _connectionEvent

    fun showPin(pin: String) {
        _connectionEvent.value = ConnectionEvent.PinRequested(pin)
    }

    fun promptConnectionConflict(deviceName: String, onResolve: (Boolean) -> Unit) {
        _connectionEvent.value = ConnectionEvent.ConnectionConflict(deviceName) { allowed ->
            onResolve(allowed)
            _connectionEvent.value = ConnectionEvent.None
        }
    }

    fun hideDialogs() {
        _connectionEvent.value = ConnectionEvent.None
    }
}
