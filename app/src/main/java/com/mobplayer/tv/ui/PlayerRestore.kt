package com.mobplayer.tv.ui

/** The player facts MainActivity compares to notice new media while the player is minimised. */
data class PlayerSnapshot(val isActive: Boolean, val title: String, val loadCount: Long)

/**
 * Whether a minimised player should come back full screen: the player was opened or closed, or
 * other media started (a new title, or a load that isn't the YouTube screen's own stream).
 *
 * The YouTube screen moving on to its next video by autoplay changes the title too, but it isn't
 * new media: bringing it up would cover a search the user is typing. [isYouTubeScreenTitle] says
 * whether the current title belongs to the video the YouTube screen is showing.
 */
fun shouldRestoreMinimizedPlayer(
    previous: PlayerSnapshot,
    current: PlayerSnapshot,
    isYouTubeScreenLoad: Boolean,
    isYouTubeScreenTitle: Boolean
): Boolean {
    if (previous.isActive != current.isActive) return true
    val isNewLoad = current.loadCount != previous.loadCount && !isYouTubeScreenLoad
    val isNewTitle = current.title != previous.title && !(current.isActive && isYouTubeScreenTitle)
    return isNewLoad || isNewTitle
}
