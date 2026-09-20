package com.mobplayer.tv.models

import kotlinx.serialization.Serializable

@Serializable
data class WebSocketMessage(
    val type: String,
    val payload: String
)

@Serializable
data class PlayerStatePayload(
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long = 0L,
    val volume: Float
)

@Serializable
data class PlayerCommandPayload(
    val action: String, // "PLAY", "PAUSE", "SEEK", "SET_VOLUME", "DPAD_UP", etc.
    val seekToMs: Long? = null,
    val positionMs: Long? = null,
    val volume: Float? = null,
    val keyCode: Int? = null,
    val url: String? = null,
    val title: String? = null
)
