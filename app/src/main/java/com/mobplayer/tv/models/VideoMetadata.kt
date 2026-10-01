package com.mobplayer.tv.models

import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class VideoMetadata(
    val id: String,
    val fileName: String,
    val originalFileName: String,
    val title: String,
    val fileSize: Long,
    val fileSizeFormatted: String,
    val mimeType: String = "video/mp4",
    val uploadedAt: Long,
    val durationMs: Long = 0L,
    val lastPlayedPositionMs: Long = 0L,
    val lastPlayedAt: Long = 0L
) {
    val progress: Float
        get() = if (durationMs > 0L) {
            (lastPlayedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val isCompleted: Boolean
        get() = durationMs > 0L && lastPlayedPositionMs >= (durationMs - 5_000L).coerceAtLeast(0L)

    val remainingMs: Long
        get() = if (durationMs > 0L) (durationMs - lastPlayedPositionMs).coerceAtLeast(0L) else 0L

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return if (digitGroups == 0) "$bytes B" else String.format("%.1f %s", value, units[digitGroups])
        }

        fun sanitizeFileName(name: String): String {
            val simpleName = File(name).name.replace('\\', '/').substringAfterLast('/')
            val base = simpleName.substringBeforeLast('.', simpleName)
                .replace(Regex("[^a-zA-Z0-9_-]+"), "_")
                .trim('_', '.')
                .ifEmpty { "uploaded_video" }
            val ext = simpleName.substringAfterLast('.', "").replace(Regex("[^a-zA-Z0-9]"), "")
            return if (ext.isNotEmpty()) "$base.$ext" else base
        }

        fun resolveMimeType(fileName: String, providedMime: String? = null): String {
            if (!providedMime.isNullOrBlank() && 
                providedMime != "application/octet-stream" && 
                providedMime != "multipart/form-data") {
                return providedMime
            }
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "mp4", "m4v" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "ts", "m2ts", "mts" -> "video/mp2t"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "flv" -> "video/x-flv"
                "wmv" -> "video/x-ms-wmv"
                "asf" -> "video/x-ms-asf"
                "3gp", "3gpp", "3g2" -> "video/3gpp"
                "ogv", "ogg" -> "video/ogg"
                "mpg", "mpeg", "vob" -> "video/mpeg"
                "mp3" -> "audio/mpeg"
                "aac" -> "audio/aac"
                "wav" -> "audio/wav"
                "flac" -> "audio/flac"
                "m4a" -> "audio/mp4"
                "opus" -> "audio/opus"
                "m3u8" -> "application/x-mpegURL"
                "mpd" -> "application/dash+xml"
                else -> "video/mp4"
            }
        }
    }
}

@Serializable
data class UploadResponse(
    val status: String,
    val uploadId: String,
    val fileName: String,
    val title: String,
    val size: Long,
    val videoUrl: String,
    val streamUrl: String,
    val playedImmediately: Boolean,
    val message: String? = null
)

@Serializable
data class VideoListResponse(
    val videos: List<VideoMetadata>,
    val totalCount: Int,
    val totalSizeBytes: Long,
    val totalSizeFormatted: String
)
