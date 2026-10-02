package com.mobplayer.ytcrawler.model

import com.mobplayer.ytcrawler.Lazy
import com.mobplayer.ytcrawler.model.youtube.stream.DashManifestInfo
import com.mobplayer.ytcrawler.model.youtube.stream.Subtitle
import com.mobplayer.ytcrawler.model.youtube.stream.YouTubeStream

data class ExtractorResult(
    val vid: String,
    val title: String,
    val dashManifestInfo: DashManifestInfo?,
    val streams: List<YouTubeStream>,
    val subtitlesLazy: Lazy<List<Subtitle>>? = null
) {
    val subtitles: List<Subtitle>
        get() = subtitlesLazy?.get() ?: emptyList()
}

class ResponseData<T>(
    val data: T,
    val nextContinuation: (suspend () -> ResponseData<T>?)? = null
) {
    val hasContinuation: Boolean
        get() = nextContinuation != null
}
