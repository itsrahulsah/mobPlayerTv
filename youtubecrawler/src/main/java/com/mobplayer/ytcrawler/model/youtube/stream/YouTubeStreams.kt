package com.mobplayer.ytcrawler.model.youtube.stream

import com.mobplayer.ytcrawler.Const
import com.mobplayer.ytcrawler.Lazy
import com.mobplayer.ytcrawler.internal.Utils
import java.util.Comparator

data class Range(val start: Long, val end: Long)

data class SegmentBaseData(val index: Range?, val init: Range?)

data class DashManifestInfo(val url: String, val content: String) {
    class Builder {
        var url: String = ""
        var content: String = ""
        fun setUrl(url: String) = apply { this.url = url }
        fun setContent(content: String) = apply { this.content = content }
        fun build() = DashManifestInfo(url, content)
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }
}

data class Subtitle(val lang: String, val url: String, val isAutoCaption: Boolean = false)

open class UrlLazy(valueGetter: () -> String) : Lazy<String>(valueGetter)

open class YouTubeStream(
    val urlLazy: UrlLazy,
    val expireAt: Long,
    val itag: String,
    val container: String,
    val mimeType: String
) {
    val url: String
        get() = urlLazy.get()

    companion object {
        @JvmStatic
        fun compareStream(): Comparator<YouTubeStream> {
            return Comparator { s1, s2 ->
                val compare = getType(s1).compareTo(getType(s2))
                if (compare == 0 && s1 is YouTubeDashStream && s2 is YouTubeDashStream) {
                    YouTubeDashStream.comparator().compare(s1, s2)
                } else {
                    compare
                }
            }
        }

        private fun getType(stream: YouTubeStream): Int {
            return when (stream) {
                is YouTubeDashAudioStream -> 0
                is YouTubeDashVideoStream -> 1
                is YouTubeNonDashStream -> 2
                else -> 3
            }
        }
    }
}

open class YouTubeDashStream(
    urlLazy: UrlLazy,
    expireAt: Long,
    itag: String,
    container: String,
    mimeType: String,
    val bandwidth: Int,
    val contentLength: Int,
    val segmentBase: SegmentBaseData?
) : YouTubeStream(urlLazy, expireAt, itag, container, mimeType) {
    companion object {
        @JvmStatic
        fun comparator(): Comparator<YouTubeDashStream> {
            return Comparator { s1, s2 ->
                s1.bandwidth.compareTo(s2.bandwidth)
            }
        }
    }
}

class YouTubeDashVideoStream(
    urlLazy: UrlLazy,
    expireAt: Long,
    itag: String,
    container: String,
    mimeType: String,
    bandwidth: Int,
    contentLength: Int,
    segmentBase: SegmentBaseData?,
    val width: Int,
    val height: Int,
    val codec: String?,
    val fps: Int
) : YouTubeDashStream(urlLazy, expireAt, itag, container, mimeType, bandwidth, contentLength, segmentBase)

class YouTubeDashAudioStream(
    urlLazy: UrlLazy,
    expireAt: Long,
    itag: String,
    container: String,
    mimeType: String,
    bandwidth: Int,
    contentLength: Int,
    segmentBase: SegmentBaseData?,
    val codec: String?,
    val audioChannelCount: Int
) : YouTubeDashStream(urlLazy, expireAt, itag, container, mimeType, bandwidth, contentLength, segmentBase)

class YouTubeNonDashStream(
    urlLazy: UrlLazy,
    expireAt: Long,
    itag: String,
    container: String,
    mimeType: String,
    val width: Int,
    val height: Int,
    val audioBitrate: Int,
    val audioCodec: String?,
    val videoCodec: String?
) : YouTubeStream(urlLazy, expireAt, itag, container, mimeType)

class YouTubeLiveStream(
    val manifestUrl: String,
    val type: Type
) : YouTubeStream(
    UrlLazy { manifestUrl },
    Const.UNKNOWN_VALUE.toLong(),
    if (type == Type.HLS) "live_hls" else "live_dash",
    if (type == Type.HLS) "m3u8" else "mpd",
    if (type == Type.HLS) "application/x-mpegURL" else "application/dash+xml"
) {
    enum class Type {
        HLS, DASH
    }
}
