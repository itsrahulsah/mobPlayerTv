package com.mobplayer.tv.storage

import android.content.Context
import com.mobplayer.tv.models.VideoMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoUploadManager(
    val uploadDir: File
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        File(context.filesDir, "uploads")
    )

    data class ActiveUpload(
        val uploadId: String,
        val file: File,
        var metadata: VideoMetadata,
        val totalSize: Long,
        @Volatile var bytesWritten: Long = 0L,
        @Volatile var isCompleted: Boolean = false,
        @Volatile var error: Throwable? = null,
        @Volatile var lastActivityAt: Long = System.currentTimeMillis()
    ) {
        internal var outputStream: FileOutputStream? = null
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val activeUploads = ConcurrentHashMap<String, ActiveUpload>()
    private val _uploadedVideosFlow = MutableStateFlow<List<VideoMetadata>>(emptyList())
    val uploadedVideosFlow: StateFlow<List<VideoMetadata>> = _uploadedVideosFlow.asStateFlow()

    init {
        uploadDir.mkdirs()
        loadPersistedMetadata()
    }

    fun loadPersistedMetadata() {
        val metaFiles = uploadDir.listFiles { _, name -> name.endsWith(".meta.json") } ?: emptyArray()
        val list = mutableListOf<VideoMetadata>()
        for (metaFile in metaFiles) {
            try {
                val content = metaFile.readText()
                val meta = json.decodeFromString<VideoMetadata>(content)
                val mediaFile = File(uploadDir, meta.fileName)
                if (mediaFile.exists()) {
                    list.add(meta)
                } else {
                    metaFile.delete()
                }
            } catch (_: Exception) {}
        }
        _uploadedVideosFlow.value = list.sortedByDescending { it.uploadedAt }
    }

    fun createActiveUpload(
        originalFileName: String,
        customTitle: String? = null,
        totalSize: Long = 0L,
        mimeType: String = "video/mp4"
    ): ActiveUpload {
        val timestamp = System.currentTimeMillis()
        val uploadId = "upload_$timestamp"
        val videoId = "vid_$timestamp"
        val cleanName = VideoMetadata.sanitizeFileName(originalFileName)
        val storedFileName = "${timestamp}_$cleanName"
        val destFile = File(uploadDir, storedFileName)

        val cleanTitle = customTitle?.trim()?.ifEmpty { null }
            ?: originalFileName.substringBeforeLast('.').ifEmpty { "Uploaded Video" }

        val metadata = VideoMetadata(
            id = videoId,
            fileName = storedFileName,
            originalFileName = originalFileName,
            title = cleanTitle,
            fileSize = totalSize,
            fileSizeFormatted = VideoMetadata.formatFileSize(totalSize),
            mimeType = mimeType,
            uploadedAt = timestamp
        )

        val active = ActiveUpload(
            uploadId = uploadId,
            file = destFile,
            metadata = metadata,
            totalSize = totalSize,
            bytesWritten = 0L,
            isCompleted = false
        )
        active.outputStream = FileOutputStream(destFile)
        activeUploads[uploadId] = active
        return active
    }

    fun writeChunk(uploadId: String, data: ByteArray, length: Int) {
        val upload = activeUploads[uploadId] ?: throw IllegalArgumentException("Upload $uploadId not found")
        val stream = upload.outputStream ?: throw IllegalStateException("Upload $uploadId stream is closed")
        stream.write(data, 0, length)
        stream.flush()
        upload.bytesWritten += length
        upload.lastActivityAt = System.currentTimeMillis()
    }

    fun markUploadCompleted(uploadId: String): VideoMetadata {
        val upload = activeUploads[uploadId] ?: throw IllegalArgumentException("Upload $uploadId not found")
        upload.outputStream?.flush()
        upload.outputStream?.close()
        upload.outputStream = null
        upload.isCompleted = true

        val actualSize = upload.file.length()
        val finalMeta = upload.metadata.copy(
            fileSize = actualSize,
            fileSizeFormatted = VideoMetadata.formatFileSize(actualSize)
        )
        upload.metadata = finalMeta

        saveMetadataToFile(finalMeta)
        refreshVideosFlow()
        activeUploads.remove(uploadId)
        return finalMeta
    }

    fun markUploadFailed(uploadId: String, error: Throwable) {
        val upload = activeUploads[uploadId] ?: return
        upload.error = error
        try {
            upload.outputStream?.close()
        } catch (_: Exception) {}
        upload.outputStream = null
        upload.file.delete()
        activeUploads.remove(uploadId)
    }

    fun getActiveUpload(uploadId: String): ActiveUpload? = activeUploads[uploadId]

    fun getActiveUploadCount(): Int = activeUploads.size

    fun findCompletedVideoFileByUploadId(uploadId: String): File? {
        val timestamp = if (uploadId.startsWith("upload_")) {
            uploadId.removePrefix("upload_")
        } else {
            uploadId
        }
        // 1. Try finding in loaded metadata flow
        val meta = _uploadedVideosFlow.value.find {
            it.id == "vid_$timestamp" || it.fileName.startsWith("${timestamp}_")
        }
        if (meta != null) {
            val file = getVideoFile(meta.fileName)
            if (file != null && file.exists()) return file
        }
        // 2. Direct directory scan for files starting with timestamp_
        val matches = uploadDir.listFiles { _, name ->
            name.startsWith("${timestamp}_") && !name.endsWith(".meta.json")
        }
        val matched = matches?.firstOrNull { it.exists() && it.isFile }
        if (matched != null && matched.canonicalPath.startsWith(uploadDir.canonicalPath)) {
            return matched
        }
        // 3. Direct filename match fallback
        return getVideoFile(uploadId)
    }

    fun cleanupStaleUploads(maxIdleMs: Long = 15 * 60 * 1000L) {
        val now = System.currentTimeMillis()
        val stale = activeUploads.filter { (_, upload) -> now - upload.lastActivityAt > maxIdleMs }
        for ((id, _) in stale) {
            markUploadFailed(id, java.io.IOException("Upload timed out after inactivity"))
        }
    }

    fun updatePlaybackProgress(videoId: String, positionMs: Long, durationMs: Long): VideoMetadata? {
        val currentList = _uploadedVideosFlow.value
        val existing = currentList.find { it.id == videoId } ?: return null

        val updated = existing.copy(
            lastPlayedPositionMs = positionMs,
            durationMs = if (durationMs > 0L) durationMs else existing.durationMs,
            lastPlayedAt = System.currentTimeMillis()
        )

        saveMetadataToFile(updated)
        refreshVideosFlow()
        return updated
    }

    fun getVideoMetadata(videoId: String): VideoMetadata? {
        return _uploadedVideosFlow.value.find { it.id == videoId }
    }

    fun getVideoFile(fileName: String): File? {
        val cleanName = File(fileName).name
        val file = File(uploadDir, cleanName)
        if (!file.canonicalPath.startsWith(uploadDir.canonicalPath)) return null
        return if (file.exists() && file.isFile) file else null
    }

    fun deleteVideo(videoId: String): Boolean {
        val meta = getVideoMetadata(videoId) ?: return false
        val mediaFile = File(uploadDir, meta.fileName)
        val metaFile = File(uploadDir, "${meta.fileName}.meta.json")

        var deleted = false
        if (mediaFile.exists()) {
            deleted = mediaFile.delete()
        }
        if (metaFile.exists()) {
            metaFile.delete()
        }
        refreshVideosFlow()
        return deleted
    }

    fun deleteAllVideos(): Int {
        val currentList = _uploadedVideosFlow.value
        var count = 0
        for (item in currentList) {
            val mediaFile = File(uploadDir, item.fileName)
            val metaFile = File(uploadDir, "${item.fileName}.meta.json")
            if (mediaFile.exists()) mediaFile.delete()
            if (metaFile.exists()) metaFile.delete()
            count++
        }
        refreshVideosFlow()
        return count
    }

    private fun saveMetadataToFile(metadata: VideoMetadata) {
        val metaFile = File(uploadDir, "${metadata.fileName}.meta.json")
        metaFile.writeText(json.encodeToString(metadata))
    }

    private fun refreshVideosFlow() {
        loadPersistedMetadata()
    }
}
