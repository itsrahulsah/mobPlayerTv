package com.mobplayer.tv.viewmodel

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
