package com.mobplayer.tv.viewmodel

import androidx.lifecycle.ViewModel
import com.mobplayer.tv.repository.MediaRepository
import com.mobplayer.tv.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TvMainViewModel @Inject constructor(
    val serverRepository: ServerRepository,
    val mediaRepository: MediaRepository
) : ViewModel() {
    
    // We can expose the flows directly from the repositories to the UI.
    // This acts as a clean bridge for the Compose views.
    
    val connectionEvent = serverRepository.connectionEvent
    val connectedDeviceName = serverRepository.connectedDeviceName
    val activeMediaTitle = serverRepository.activeMediaTitle
    val isPlayerActive = serverRepository.isPlayerActive
    val playerStateFlow = mediaRepository.playerStateFlow
    val remoteActionEvent = serverRepository.remoteActionEvent

    fun initializePlayer() = mediaRepository.initialize()
    
    fun closePlayer() {
        serverRepository.closePlayer()
        mediaRepository.stop()
    }
    
    fun enterDemoMode() {
        serverRepository.enterDemoMode()
    }

    override fun onCleared() {
        super.onCleared()
        mediaRepository.release()
    }
}
