package com.mobplayer.tv.youtube

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.DefaultDashChunkSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.liskovsoft.googlecommon.common.helpers.DefaultHeaders
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.formatbuilders.mpdbuilder.YouTubeMPDBuilder
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import com.liskovsoft.youtubeapi.service.data.YouTubeMediaItemFormatInfo
import com.liskovsoft.youtubeapi.videoinfo.V2.VideoInfoService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

sealed interface PlaybackSource {
    data class Dash(val mpdXml: String) : PlaybackSource
    data class Hls(val url: String) : PlaybackSource
    data class DashUrl(val url: String) : PlaybackSource
    data class Progressive(val url: String) : PlaybackSource
    data class Error(val message: String) : PlaybackSource
}

object SmartTubePlayerEngine {
    private const val TAG = "SmartTubePlayerEngine"
    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            GlobalPreferences.instance(context.applicationContext)
            isInitialized = true
            Log.d(TAG, "SmartTubePlayerEngine initialized successfully")
        }
    }

    suspend fun resolvePlaybackSource(videoId: String): PlaybackSource = withContext(Dispatchers.IO) {
        Log.d(TAG, "Resolving playback source for videoId: $videoId")

        // Strategy 1: Use VideoInfoService directly
        // VideoInfoService iterates through VISIONOS, TV_DOWNGRADED, WEB, etc.
        // and deciphers signatures + n-params using V8 and injects PoToken
        try {
            Log.d(TAG, "Strategy 1: Querying VideoInfoService...")
            val videoInfoService = VideoInfoService.instance()
            val videoInfo = videoInfoService.getVideoInfo(videoId, null)
            if (videoInfo != null) {
                Log.d(TAG, "VideoInfoService returned client=${videoInfo.client}, isUnplayable=${videoInfo.isUnplayable}")
                if (!videoInfo.isUnplayable) {
                    val formatInfo = YouTubeMediaItemFormatInfo.from(videoInfo)
                    val source = extractPlaybackSource(formatInfo)
                    if (source != null) {
                        Log.d(TAG, "Successfully resolved source via VideoInfoService: ${source.javaClass.simpleName}")
                        return@withContext source
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Strategy 1 (VideoInfoService) error: ${e.message}", e)
        }

        ensureActive()

        // Strategy 2: Use YouTubeServiceManager with client rotation
        val service = YouTubeServiceManager.instance()
        val mediaItemService = service.mediaItemService

        for (attempt in 0..5) {
            // The calls below block and ignore cancellation; stop between them so an abandoned lookup
            // doesn't keep rotating the shared client under a newer video's lookup.
            ensureActive()
            try {
                Log.d(TAG, "Strategy 2: MediaItemService attempt $attempt...")
                val formatInfo = mediaItemService.getFormatInfo(videoId)
                if (formatInfo != null) {
                    Log.d(TAG, "MediaItemService attempt $attempt: containsDash=${formatInfo.containsDashFormats()}, containsSabr=${formatInfo.containsSabrFormats()}, containsUrl=${formatInfo.containsUrlFormats()}, hls=${!formatInfo.hlsManifestUrl.isNullOrEmpty()}")
                    val source = extractPlaybackSource(formatInfo)
                    if (source != null) {
                        Log.d(TAG, "Successfully resolved source via MediaItemService (attempt $attempt): ${source.javaClass.simpleName}")
                        return@withContext source
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Strategy 2 (MediaItemService) attempt $attempt failed: ${e.message}")
            }
            Log.d(TAG, "Attempt $attempt produced no playable formats, rotating client...")
            ensureActive()
            service.switchNextClientNow()
        }

        Log.e(TAG, "All strategies exhausted for videoId: $videoId")
        PlaybackSource.Error("No playable stream found")
    }

    private fun extractPlaybackSource(formatInfo: MediaItemFormatInfo): PlaybackSource? {
        // 0. Live streams: a sideloaded DASH manifest would be dynamic, which ExoPlayer rejects
        //    (DashMediaSource requires a static manifest when sideloaded), so use the HLS URL.
        val liveHlsUrl = formatInfo.hlsManifestUrl
        if (formatInfo.isLive && !liveHlsUrl.isNullOrEmpty()) {
            Log.d(TAG, "Live stream, using HLS manifest URL: $liveHlsUrl")
            return PlaybackSource.Hls(liveHlsUrl)
        }

        // 1. DASH Manifest stream generated from deciphered adaptive formats
        if (formatInfo.containsDashFormats()) {
            try {
                val stream = formatInfo.createMpdStream() ?: YouTubeMPDBuilder.from(formatInfo).build()
                if (stream != null) {
                    val xml = stream.bufferedReader().use { it.readText() }
                    if (xml.isNotEmpty() && xml.contains("<MPD") && xml.contains("<Representation")) {
                        Log.d(TAG, "Built DASH MPD manifest (${xml.length} bytes)")
                        return PlaybackSource.Dash(xml)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed building MPD from formatInfo: ${e.message}")
            }
        }

        // 2. Direct HLS Manifest URL (iOS / VisionOS streams with unthrottled adaptive video + audio)
        val hlsUrl = formatInfo.hlsManifestUrl
        if (!hlsUrl.isNullOrEmpty()) {
            Log.d(TAG, "Found HLS manifest URL: $hlsUrl")
            return PlaybackSource.Hls(hlsUrl)
        }

        // 3. Direct DASH Manifest URL
        val dashUrl = formatInfo.dashManifestUrl
        if (!dashUrl.isNullOrEmpty()) {
            Log.d(TAG, "Found DASH manifest URL: $dashUrl")
            return PlaybackSource.DashUrl(dashUrl)
        }

        // 4. Progressive URL formats
        if (formatInfo.containsUrlFormats()) {
            val urlList = formatInfo.createUrlList()
            if (!urlList.isNullOrEmpty()) {
                Log.d(TAG, "Found progressive URL: ${urlList[0]}")
                return PlaybackSource.Progressive(urlList[0])
            }
        }

        // 5. Fallback: Check if adaptive formats have non-null URLs even if containsDashFormats returned false
        val adaptive = formatInfo.adaptiveFormats
        if (!adaptive.isNullOrEmpty()) {
            val hasVideoUrl = adaptive.any { it.mimeType?.startsWith("video/") == true && !it.url.isNullOrEmpty() }
            val hasAudioUrl = adaptive.any { it.mimeType?.startsWith("audio/") == true && !it.url.isNullOrEmpty() }
            if (hasVideoUrl && hasAudioUrl) {
                try {
                    val mpdBuilder = YouTubeMPDBuilder.from(formatInfo)
                    for (fmt in adaptive) {
                        mpdBuilder.append(fmt)
                    }
                    val stream = mpdBuilder.build()
                    if (stream != null) {
                        val xml = stream.bufferedReader().use { it.readText() }
                        if (xml.isNotEmpty() && xml.contains("<MPD") && xml.contains("<Representation")) {
                            Log.d(TAG, "Built custom adaptive DASH MPD manifest (${xml.length} bytes)")
                            return PlaybackSource.Dash(xml)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Custom adaptive DASH builder failed: ${e.message}")
                }
            }
        }

        return null
    }

    @OptIn(UnstableApi::class)
    fun createHttpDataSourceFactory(): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent(DefaultHeaders.APP_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)
    }

    @OptIn(UnstableApi::class)
    fun buildMediaSource(playbackSource: PlaybackSource, httpDataSourceFactory: DefaultHttpDataSource.Factory): MediaSource? {
        return when (playbackSource) {
            is PlaybackSource.Dash -> {
                val parser = DashManifestParser()
                val manifestUri = Uri.parse("https://www.youtube.com/api/manifest/dash/test.mpd")
                val manifest = parser.parse(manifestUri, ByteArrayInputStream(playbackSource.mpdXml.toByteArray()))
                val chunkSourceFactory = DefaultDashChunkSource.Factory(httpDataSourceFactory)
                DashMediaSource.Factory(chunkSourceFactory, null).createMediaSource(manifest)
            }
            is PlaybackSource.Hls -> {
                HlsMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(playbackSource.url))
            }
            is PlaybackSource.DashUrl -> {
                DashMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(playbackSource.url))
            }
            is PlaybackSource.Progressive -> {
                ProgressiveMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(playbackSource.url))
            }
            is PlaybackSource.Error -> null
        }
    }
}
