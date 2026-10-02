package com.mobplayer.ytcrawler

import com.mobplayer.ytcrawler.internal.InnerTubeClient
import com.mobplayer.ytcrawler.model.ExtractorResult
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

interface YouTubeStreamExtractor {
    data class Options(val isMarkWatched: Boolean = false)

    suspend fun extract(vid: String, options: Options = Options()): ExtractorResult
}

class DefaultYouTubeStreamExtractor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val signatureDecipher: SignatureDecipher? = null
) : YouTubeStreamExtractor {

    private val innerTubeClient = InnerTubeClient(okHttpClient, gson)

    override suspend fun extract(vid: String, options: YouTubeStreamExtractor.Options): ExtractorResult =
        withContext(Dispatchers.IO) {
            innerTubeClient.extractStreams(vid)
        }
}
