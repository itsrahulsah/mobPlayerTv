package com.mobplayer.ytcrawler

enum class ChannelTab(val lastPathSegment: String) {
    HOME("/featured"),
    VIDEOS("/videos"),
    PLAYLISTS("/playlists"),
    CHANNELS("/channels")
}
