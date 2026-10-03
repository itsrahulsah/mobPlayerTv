package com.mobplayer.tv.viewmodel

import androidx.lifecycle.ViewModel
import com.mobplayer.tv.models.RemoteActionEvent
import com.mobplayer.tv.models.RemoteIconType
import com.mobplayer.tv.models.VideoMetadata
import com.mobplayer.tv.repository.MediaRepository
import com.mobplayer.tv.repository.ServerRepository
import com.mobplayer.tv.storage.VideoUploadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TvMainViewModel @Inject constructor(
    val serverRepository: ServerRepository,
    val mediaRepository: MediaRepository,
    val videoUploadManager: VideoUploadManager
) : ViewModel() {
    
    // We can expose the flows directly from the repositories to the UI.
    // This acts as a clean bridge for the Compose views.
    
    val connectionEvent = serverRepository.connectionEvent
    val connectedDeviceName = serverRepository.connectedDeviceName
    val activeMediaTitle = serverRepository.activeMediaTitle
    val isPlayerActive = serverRepository.isPlayerActive
    val playerStateFlow = mediaRepository.playerStateFlow
    val remoteActionEvent = serverRepository.remoteActionEvent
    val uploadedVideos = videoUploadManager.uploadedVideosFlow

    init {
        mediaRepository.attach(this)
    }

    fun initializePlayer() = mediaRepository.initialize()

    fun releasePlayer() = mediaRepository.release(this)
    
    fun closePlayer() {
        serverRepository.closePlayer()
        mediaRepository.stop()
    }
    
    fun enterDemoMode() {
        serverRepository.enterDemoMode()
    }

    fun playUploadedVideo(metadata: VideoMetadata, resume: Boolean = true) {
        val file = videoUploadManager.getVideoFile(metadata.fileName) ?: return
        val startPos = if (resume && !metadata.isCompleted && metadata.lastPlayedPositionMs > 5000L) {
            metadata.lastPlayedPositionMs
        } else {
            0L
        }
        serverRepository.openPlayer(metadata.title)
        mediaRepository.loadMedia("file://${file.absolutePath}", startPos, metadata.id, metadata.mimeType)
        val resumeText = if (startPos > 0) " (Resumed)" else ""
        serverRepository.postRemoteAction(
            RemoteActionEvent("PLAY", "🎬 Playing$resumeText", metadata.title, RemoteIconType.PLAY)
        )
    }

    fun deleteUploadedVideo(videoId: String): Boolean {
        return videoUploadManager.deleteVideo(videoId)
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}
