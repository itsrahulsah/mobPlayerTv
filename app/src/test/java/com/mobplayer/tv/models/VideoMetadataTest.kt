package com.mobplayer.tv.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class VideoMetadataTest {

    private fun meta(durationMs: Long, positionMs: Long) = VideoMetadata(
        id = "vid_1",
        fileName = "1_a.mp4",
        originalFileName = "a.mp4",
        title = "A",
        fileSize = 1,
        fileSizeFormatted = "1 B",
        uploadedAt = 0L,
        durationMs = durationMs,
        lastPlayedPositionMs = positionMs
    )

    @Test
    fun `resolveMimeType maps common extensions`() {
        val expected = mapOf(
            "a.mp4" to "video/mp4",
            "a.M4V" to "video/mp4",
            "a.mkv" to "video/x-matroska",
            "a.webm" to "video/webm",
            "a.ts" to "video/mp2t",
            "a.avi" to "video/x-msvideo",
            "a.mov" to "video/quicktime",
            "a.flv" to "video/x-flv",
            "a.wmv" to "video/x-ms-wmv",
            "a.3gp" to "video/3gpp",
            "a.ogv" to "video/ogg",
            "a.mpeg" to "video/mpeg",
            "a.mp3" to "audio/mpeg",
            "a.flac" to "audio/flac",
            "a.opus" to "audio/opus",
            "stream.m3u8" to "application/x-mpegURL",
            "manifest.mpd" to "application/dash+xml",
            "noextension" to "video/mp4",
            "a.unknown" to "video/mp4"
        )
        expected.forEach { (name, mime) -> assertEquals(name, mime, VideoMetadata.resolveMimeType(name)) }
    }

    @Test
    fun `resolveMimeType prefers a specific provided mime`() {
        assertEquals("video/webm", VideoMetadata.resolveMimeType("a.mkv", "video/webm"))
    }

    @Test
    fun `resolveMimeType ignores generic provided mimes`() {
        assertEquals("video/x-matroska", VideoMetadata.resolveMimeType("a.mkv", "application/octet-stream"))
        assertEquals("video/x-matroska", VideoMetadata.resolveMimeType("a.mkv", "multipart/form-data"))
        assertEquals("video/x-matroska", VideoMetadata.resolveMimeType("a.mkv", "  "))
    }

    @Test
    fun `resolveMimeType uses the extension of a url`() {
        assertEquals("application/x-mpegURL", VideoMetadata.resolveMimeType("https://cdn.example.com/live/index.m3u8"))
    }

    @Test
    fun `sanitizeFileName handles windows paths and names without extension`() {
        assertEquals("movie.mp4", VideoMetadata.sanitizeFileName("C:\\Users\\me\\movie.mp4"))
        assertEquals("README", VideoMetadata.sanitizeFileName("README"))
        assertEquals("uploaded_video.mp4", VideoMetadata.sanitizeFileName("!!!.mp4"))
    }

    @Test
    fun `progress helpers are zero without a duration`() {
        val m = meta(durationMs = 0L, positionMs = 10_000L)
        assertEquals(0f, m.progress, 0f)
        assertEquals(0L, m.remainingMs)
        assertFalse(m.isCompleted)
    }

    @Test
    fun `progress and remaining are clamped past the end`() {
        val m = meta(durationMs = 10_000L, positionMs = 15_000L)
        assertEquals(1f, m.progress, 0f)
        assertEquals(0L, m.remainingMs)
        assertTrue(m.isCompleted)
    }

    @Test
    fun `isCompleted within the last five seconds`() {
        assertFalse(meta(durationMs = 60_000L, positionMs = 54_999L).isCompleted)
        assertTrue(meta(durationMs = 60_000L, positionMs = 55_000L).isCompleted)
        // Shorter than five seconds: any position counts as finished
        assertTrue(meta(durationMs = 3_000L, positionMs = 0L).isCompleted)
    }

    @Test
    fun `formatFileSize uses terabytes for huge sizes`() {
        assertEquals("2.0 TB", VideoMetadata.formatFileSize(2L * 1024 * 1024 * 1024 * 1024))
        assertEquals("0 B", VideoMetadata.formatFileSize(-5))
    }

    @Test
    fun `metadata survives a json round trip`() {
        val json = Json { ignoreUnknownKeys = true }
        val original = meta(durationMs = 1_000L, positionMs = 500L)
        assertEquals(original, json.decodeFromString<VideoMetadata>(json.encodeToString(original)))
    }

    @Test
    fun `player command payload decodes with optional fields missing`() {
        val json = Json { ignoreUnknownKeys = true }
        val command = json.decodeFromString<PlayerCommandPayload>("""{"action":"SEEK","seekToMs":42000,"extra":1}""")
        assertEquals("SEEK", command.action)
        assertEquals(42_000L, command.seekToMs)
        assertNull(command.volume)
        assertNull(command.url)
    }
}
