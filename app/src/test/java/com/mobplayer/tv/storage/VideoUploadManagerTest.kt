package com.mobplayer.tv.storage

import com.mobplayer.tv.models.VideoMetadata
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VideoUploadManagerTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var uploadDir: File
    private lateinit var uploadManager: VideoUploadManager

    @Before
    fun setup() {
        uploadDir = tempFolder.newFolder("test_uploads")
        uploadManager = VideoUploadManager(uploadDir)
    }

    @Test
    fun `test VideoMetadata formatting and calculation helpers`() {
        assertEquals("0 B", VideoMetadata.formatFileSize(0))
        assertEquals("500 B", VideoMetadata.formatFileSize(500))
        assertEquals("1.0 KB", VideoMetadata.formatFileSize(1024))
        assertEquals("10.0 MB", VideoMetadata.formatFileSize(10 * 1024 * 1024))
        assertEquals("1.5 GB", VideoMetadata.formatFileSize((1.5 * 1024 * 1024 * 1024).toLong()))

        assertEquals("test_video.mp4", VideoMetadata.sanitizeFileName("test video.mp4"))
        assertEquals("vacation.mkv", VideoMetadata.sanitizeFileName("../../../vacation.mkv"))
        assertEquals("special_chars.mp4", VideoMetadata.sanitizeFileName("special!@#chars.mp4"))

        val meta = VideoMetadata(
            id = "vid_1",
            fileName = "1_test.mp4",
            originalFileName = "test.mp4",
            title = "Test Video",
            fileSize = 1000,
            fileSizeFormatted = "1.0 KB",
            uploadedAt = 1000L,
            durationMs = 100_000L,
            lastPlayedPositionMs = 50_000L
        )

        assertEquals(0.5f, meta.progress, 0.001f)
        assertEquals(50_000L, meta.remainingMs)
        assertFalse(meta.isCompleted)

        val completedMeta = meta.copy(lastPlayedPositionMs = 98_000L)
        assertTrue(completedMeta.isCompleted)
    }

    @Test
    fun `test createActiveUpload and writeChunk progressive streaming`() {
        val originalName = "sample_movie.mp4"
        val customTitle = "My Favorite Movie"
        val totalSize = 3000L

        val active = uploadManager.createActiveUpload(
            originalFileName = originalName,
            customTitle = customTitle,
            totalSize = totalSize
        )

        assertNotNull(active)
        assertEquals(customTitle, active.metadata.title)
        assertEquals(0L, active.bytesWritten)
        assertFalse(active.isCompleted)
        assertTrue(active.file.exists())

        // Stream chunk 1: 1000 bytes
        val chunk1 = ByteArray(1000) { 1 }
        uploadManager.writeChunk(active.uploadId, chunk1, 1000)
        assertEquals(1000L, active.bytesWritten)

        // Stream chunk 2: 1000 bytes
        val chunk2 = ByteArray(1000) { 2 }
        uploadManager.writeChunk(active.uploadId, chunk2, 1000)
        assertEquals(2000L, active.bytesWritten)

        // Stream chunk 3: 1000 bytes
        val chunk3 = ByteArray(1000) { 3 }
        uploadManager.writeChunk(active.uploadId, chunk3, 1000)
        assertEquals(3000L, active.bytesWritten)

        // Verify active upload lookup
        val retrieved = uploadManager.getActiveUpload(active.uploadId)
        assertNotNull(retrieved)
        assertEquals(3000L, retrieved?.bytesWritten)

        // Mark upload completed
        val completedMeta = uploadManager.markUploadCompleted(active.uploadId)
        assertTrue(active.isCompleted)
        assertEquals(3000L, completedMeta.fileSize)

        // Verify active upload is removed from in-memory map (prevent state/memory leak)
        assertNull(uploadManager.getActiveUpload(active.uploadId))
        assertEquals(0, uploadManager.getActiveUploadCount())

        // Verify fallback resolution finds completed video file on disk
        val completedFile = uploadManager.findCompletedVideoFileByUploadId(active.uploadId)
        assertNotNull(completedFile)
        assertTrue(completedFile!!.exists())
        assertEquals(3000L, completedFile.length())

        // Verify file content size on disk
        assertEquals(3000L, active.file.length())

        // Verify .meta.json file exists on disk
        val metaFile = File(uploadDir, "${completedMeta.fileName}.meta.json")
        assertTrue(metaFile.exists())

        // Verify video list flow updated
        val videos = uploadManager.uploadedVideosFlow.value
        assertEquals(1, videos.size)
        assertEquals(customTitle, videos[0].title)
    }

    @Test
    fun `test cleanupStaleUploads cleans timed out uploads`() {
        val active = uploadManager.createActiveUpload("stale.mp4", "Stale", 1000L)
        assertEquals(1, uploadManager.getActiveUploadCount())
        // Trigger stale cleanup with 0 idle threshold
        uploadManager.cleanupStaleUploads(maxIdleMs = -1L)
        assertEquals(0, uploadManager.getActiveUploadCount())
        assertFalse(active.file.exists())
    }

    @Test
    fun `test updatePlaybackProgress persists position and calculates progress`() {
        val active = uploadManager.createActiveUpload("vacation.mp4", "Hawaii Vacation", 5000L)
        val chunk = ByteArray(5000) { 0 }
        uploadManager.writeChunk(active.uploadId, chunk, 5000)
        val meta = uploadManager.markUploadCompleted(active.uploadId)

        // Update progress: watched 30s of 120s
        val updated = uploadManager.updatePlaybackProgress(
            videoId = meta.id,
            positionMs = 30_000L,
            durationMs = 120_000L
        )

        assertNotNull(updated)
        assertEquals(30_000L, updated?.lastPlayedPositionMs)
        assertEquals(120_000L, updated?.durationMs)
        assertEquals(0.25f, updated?.progress ?: 0f, 0.001f)
        assertFalse(updated?.isCompleted ?: true)

        // Verify flow has updated values
        val flowMeta = uploadManager.getVideoMetadata(meta.id)
        assertNotNull(flowMeta)
        assertEquals(30_000L, flowMeta?.lastPlayedPositionMs)
    }

    @Test
    fun `test loadPersistedMetadata reloads videos on restart`() {
        val active = uploadManager.createActiveUpload("clip.mp4", "Startup Test", 2000L)
        uploadManager.writeChunk(active.uploadId, ByteArray(2000), 2000)
        val meta = uploadManager.markUploadCompleted(active.uploadId)
        uploadManager.updatePlaybackProgress(meta.id, 1000L, 5000L)

        // Simulate app restart by creating a new VideoUploadManager instance with same directory
        val newManager = VideoUploadManager(uploadDir)
        val reloadedVideos = newManager.uploadedVideosFlow.value

        assertEquals(1, reloadedVideos.size)
        assertEquals(meta.id, reloadedVideos[0].id)
        assertEquals("Startup Test", reloadedVideos[0].title)
        assertEquals(1000L, reloadedVideos[0].lastPlayedPositionMs)
        assertEquals(5000L, reloadedVideos[0].durationMs)
    }

    @Test
    fun `test deleteVideo removes media and metadata files`() {
        val active = uploadManager.createActiveUpload("to_delete.mp4", "Delete Me", 1000L)
        uploadManager.writeChunk(active.uploadId, ByteArray(1000), 1000)
        val meta = uploadManager.markUploadCompleted(active.uploadId)

        val mediaFile = File(uploadDir, meta.fileName)
        val metaFile = File(uploadDir, "${meta.fileName}.meta.json")
        assertTrue(mediaFile.exists())
        assertTrue(metaFile.exists())

        val deleted = uploadManager.deleteVideo(meta.id)
        assertTrue(deleted)
        assertFalse(mediaFile.exists())
        assertFalse(metaFile.exists())
        assertEquals(0, uploadManager.uploadedVideosFlow.value.size)
    }

    @Test
    fun `test deleteAllVideos cleans up all stored files`() {
        val active1 = uploadManager.createActiveUpload("v1.mp4", "Video 1", 1000L)
        uploadManager.writeChunk(active1.uploadId, ByteArray(1000), 1000)
        uploadManager.markUploadCompleted(active1.uploadId)

        val active2 = uploadManager.createActiveUpload("v2.mp4", "Video 2", 2000L)
        uploadManager.writeChunk(active2.uploadId, ByteArray(2000), 2000)
        uploadManager.markUploadCompleted(active2.uploadId)

        assertEquals(2, uploadManager.uploadedVideosFlow.value.size)

        val count = uploadManager.deleteAllVideos()
        assertEquals(2, count)
        assertEquals(0, uploadManager.uploadedVideosFlow.value.size)
    }

    @Test
    fun `test path traversal protection in getVideoFile`() {
        val active = uploadManager.createActiveUpload("valid.mp4", "Valid", 500L)
        uploadManager.writeChunk(active.uploadId, ByteArray(500), 500)
        val meta = uploadManager.markUploadCompleted(active.uploadId)

        val validFile = uploadManager.getVideoFile(meta.fileName)
        assertNotNull(validFile)
        assertTrue(validFile!!.exists())

        // Attempt path traversal
        val malicious = uploadManager.getVideoFile("../../../etc/passwd")
        assertNull(malicious)
    }
}
