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
    val volume: Float
)

@Serializable
data class PlayerCommandPayload(
    val action: String, // "PLAY", "PAUSE", "SEEK"
    val seekToMs: Long? = null
)
