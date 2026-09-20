package com.mobplayer.tv.models

enum class RemoteIconType {
    PLAY,
    PAUSE,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    SEEK_FORWARD,
    SEEK_BACKWARD,
    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT,
    SELECT,
    BACK,
    HOME,
    MEDIA,
    INFO
}

data class RemoteActionEvent(
    val action: String,
    val displayName: String,
    val details: String? = null,
    val iconType: RemoteIconType,
    val timestamp: Long = System.currentTimeMillis()
)
