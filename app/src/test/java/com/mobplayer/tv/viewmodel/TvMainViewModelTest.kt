package com.mobplayer.tv.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mobplayer.tv.models.VideoMetadata
import com.mobplayer.tv.repository.MediaRepository
import com.mobplayer.tv.repository.ServerRepository
import com.mobplayer.tv.storage.VideoUploadManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TvMainViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var serverRepository: ServerRepository
    private lateinit var mediaRepository: MediaRepository
    private lateinit var uploadManager: VideoUploadManager
    private lateinit var viewModel: TvMainViewModel

    @Before
    fun setup() {
        serverRepository = ServerRepository()
        mediaRepository = mockk(relaxed = true)
        uploadManager = VideoUploadManager(tempFolder.newFolder("uploads"))
        viewModel = TvMainViewModel(serverRepository, mediaRepository, uploadManager)
    }

    private fun storeVideo(title: String = "Holiday", positionMs: Long = 0L, durationMs: Long = 0L): VideoMetadata {
        val upload = uploadManager.createActiveUpload("$title.mkv", title, 100L, mimeType = "video/x-matroska")
        uploadManager.writeChunk(upload.uploadId, ByteArray(100), 100)
        val meta = uploadManager.markUploadCompleted(upload.uploadId)
        return if (durationMs > 0L) uploadManager.updatePlaybackProgress(meta.id, positionMs, durationMs)!! else meta
    }

    @Test
    fun `attaches itself to the media repository on creation`() {
        verify { mediaRepository.attach(viewModel) }
    }

    @Test
    fun `exposes repository flows`() {
        assertSame(serverRepository.connectionEvent, viewModel.connectionEvent)
        assertSame(serverRepository.isPlayerActive, viewModel.isPlayerActive)
        assertSame(uploadManager.uploadedVideosFlow, viewModel.uploadedVideos)
    }

    @Test
    fun `initializePlayer delegates to the media repository`() {
        viewModel.initializePlayer()
        verify { mediaRepository.initialize() }
    }

    @Test
    fun `playUploadedVideo resumes from the saved position`() {
        val meta = storeVideo(positionMs = 30_000L, durationMs = 120_000L)
        val file = uploadManager.getVideoFile(meta.fileName)!!

        viewModel.playUploadedVideo(meta)

        verify { mediaRepository.loadMedia("file://${file.absolutePath}", 30_000L, meta.id, "video/x-matroska") }
        assertTrue(serverRepository.isPlayerActive.value)
        assertEquals("Holiday", serverRepository.activeMediaTitle.value)
        assertEquals("🎬 Playing (Resumed)", serverRepository.remoteActionEvent.value?.displayName)
    }

    @Test
    fun `playUploadedVideo starts over when resume is off`() {
        val meta = storeVideo(positionMs = 30_000L, durationMs = 120_000L)
        viewModel.playUploadedVideo(meta, resume = false)
        verify { mediaRepository.loadMedia(any(), 0L, meta.id, any()) }
        assertEquals("🎬 Playing", serverRepository.remoteActionEvent.value?.displayName)
    }

    @Test
    fun `playUploadedVideo starts over when the video was finished`() {
        val meta = storeVideo(positionMs = 118_000L, durationMs = 120_000L)
        viewModel.playUploadedVideo(meta)
        verify { mediaRepository.loadMedia(any(), 0L, meta.id, any()) }
    }

    @Test
    fun `playUploadedVideo starts over when under five seconds in`() {
        val meta = storeVideo(positionMs = 4_000L, durationMs = 120_000L)
        viewModel.playUploadedVideo(meta)
        verify { mediaRepository.loadMedia(any(), 0L, meta.id, any()) }
    }

    @Test
    fun `playUploadedVideo does nothing when the file is missing`() {
        val meta = storeVideo()
        uploadManager.getVideoFile(meta.fileName)!!.delete()

        viewModel.playUploadedVideo(meta)

        verify(exactly = 0) { mediaRepository.loadMedia(any(), any(), any(), any()) }
        assertFalse(serverRepository.isPlayerActive.value)
    }

    @Test
    fun `closePlayer closes the player screen and stops playback`() {
        serverRepository.openPlayer("Something")
        viewModel.closePlayer()
        assertFalse(serverRepository.isPlayerActive.value)
        verify { mediaRepository.stop() }
    }

    @Test
    fun `enterDemoMode connects the demo controller`() {
        viewModel.enterDemoMode()
        assertEquals("Demo Controller", serverRepository.connectedDeviceName.value)
    }

    @Test
    fun `deleteUploadedVideo removes the video`() {
        val meta = storeVideo()
        assertTrue(viewModel.deleteUploadedVideo(meta.id))
        assertTrue(viewModel.uploadedVideos.value.isEmpty())
        assertFalse(viewModel.deleteUploadedVideo(meta.id))
    }

    @Test
    fun `clearing the view model releases the player it owns`() {
        val store = ViewModelStore()
        val factory = viewModelFactory { initializer { TvMainViewModel(serverRepository, mediaRepository, uploadManager) } }
        val owned = ViewModelProvider(store, factory)[TvMainViewModel::class.java]

        store.clear()

        verify { mediaRepository.release(owned) }
    }
}
