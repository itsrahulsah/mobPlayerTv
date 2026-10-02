package com.mobplayer.ytcrawler

import com.mobplayer.ytcrawler.internal.InnerTubeClient
import com.mobplayer.ytcrawler.model.ResponseData
import com.mobplayer.ytcrawler.model.youtube.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class YouTubeData private constructor(
    val okHttpClient: OkHttpClient,
    val gson: Gson
) {
    private val innerTubeClient = InnerTubeClient(okHttpClient, gson)

    suspend fun homeFeed(): ResponseData<FeedResponse> = withContext(Dispatchers.IO) {
        val contents = innerTubeClient.browseFeed("FEwhat_to_watch")
        val feed = Feed("Home", SectionList(contents, null, null))
        ResponseData(FeedResponse(AbstractResponse.RESULT_OK, System.currentTimeMillis(), feed))
    }

    suspend fun trendingFeed(): ResponseData<FeedResponse> = withContext(Dispatchers.IO) {
        val contents = innerTubeClient.browseFeed("FEtrending")
        val feed = Feed("Trending", SectionList(contents, null, null))
        ResponseData(FeedResponse(AbstractResponse.RESULT_OK, System.currentTimeMillis(), feed))
    }

    suspend fun recommendedFeed(): ResponseData<FeedResponse> = withContext(Dispatchers.IO) {
        val contents = innerTubeClient.browseFeed("FEwhat_to_watch")
        val feed = Feed("Recommended", SectionList(contents, null, null))
        ResponseData(FeedResponse(AbstractResponse.RESULT_OK, System.currentTimeMillis(), feed))
    }

    suspend fun search(query: String): ResponseData<SearchResponse> = withContext(Dispatchers.IO) {
        val contents = innerTubeClient.search(query)
        val searchResult = SearchResult(SectionList(contents, null, null), null)
        ResponseData(SearchResponse(AbstractResponse.RESULT_OK, System.currentTimeMillis(), searchResult))
    }

    suspend fun channel(channelId: String, channelTab: ChannelTab = ChannelTab.HOME): ResponseData<ChannelResponse> =
        withContext(Dispatchers.IO) {
            val contents = innerTubeClient.browseFeed(channelId)
            val channel = Channel(null, SectionList(contents, null, null))
            ResponseData(ChannelResponse(AbstractResponse.RESULT_OK, System.currentTimeMillis(), channel))
        }

    /**
     * Crawl full playlist details and tracklist
     */
    suspend fun playlist(playlistId: String): ResponseData<PlaylistResponse> = withContext(Dispatchers.IO) {
        val res = innerTubeClient.getPlaylist(playlistId)
        ResponseData(res)
    }

    /**
     * Crawl watch metadata, streaming playback URLs, and playlist queue.
     * Pass [includeStreams] = false to skip stream extraction when only metadata/up-next is needed.
     */
    suspend fun watch(videoId: String, playlistId: String? = null, includeStreams: Boolean = true): WatchResponse = withContext(Dispatchers.IO) {
        innerTubeClient.getWatchNext(videoId, playlistId, includeStreams)
    }

    fun getStreamExtractor(signatureDecipher: SignatureDecipher? = null): YouTubeStreamExtractor {
        return DefaultYouTubeStreamExtractor(okHttpClient, gson, signatureDecipher)
    }

    fun getStreamExtractor(): YouTubeStreamExtractor {
        return getStreamExtractor(MusicAppSignatureDecipher(okHttpClient, gson))
    }

    class Builder(
        private var okHttpClientBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
    ) {
        private val gsonBuilder: GsonBuilder = GsonBuilder()
            .registerTypeAdapter(ArtistWatchCard::class.java, ArtistWatchCard.TypeAdapter())
            .registerTypeAdapter(Channel::class.java, Channel.TypeAdapter())
            .registerTypeAdapter(ChannelResponse::class.java, ChannelResponse.TypeAdapter())
            .registerTypeAdapter(CompactChannel::class.java, CompactChannel.TypeAdapter())
            .registerTypeAdapter(CompactPlaylist::class.java, CompactPlaylist.TypeAdapter())
            .registerTypeAdapter(CompactRadio::class.java, CompactRadio.TypeAdapter())
            .registerTypeAdapter(CompactVideo::class.java, CompactVideo.TypeAdapter())
            .registerTypeAdapter(Feed::class.java, Feed.TypeAdapter())
            .registerTypeAdapter(FeedResponse::class.java, FeedResponse.TypeAdapter())
            .registerTypeAdapter(ItemSection::class.java, ItemSection.TypeAdapter())
            .registerTypeAdapter(SearchResponse::class.java, SearchResponse.TypeAdapter())
            .registerTypeAdapter(SearchResult::class.java, SearchResult.TypeAdapter())
            .registerTypeAdapter(SectionList::class.java, SectionList.TypeAdapter())
            .registerTypeAdapter(Shelf::class.java, Shelf.TypeAdapter())
            .registerTypeAdapter(VerticalList::class.java, VerticalList.TypeAdapter())
            .registerTypeAdapter(VideoWithContext::class.java, VideoWithContext.TypeAdapter())
            .registerTypeAdapter(WatchCardAlbumList::class.java, WatchCardAlbumList.TypeAdapter())
            .registerTypeAdapter(WatchCardVideoList::class.java, WatchCardVideoList.TypeAdapter())
            .setLenient()

        fun setCache(cacheDir: File, cacheSize: Long) = apply {
            okHttpClientBuilder.cache(Cache(cacheDir, cacheSize))
        }

        fun addInterceptor(interceptor: Interceptor) = apply {
            okHttpClientBuilder.addInterceptor(interceptor)
        }

        fun addNetworkInterceptor(interceptor: Interceptor) = apply {
            okHttpClientBuilder.addNetworkInterceptor(interceptor)
        }

        fun setCookieJar(cookieJar: CookieJar) = apply {
            okHttpClientBuilder.cookieJar(cookieJar)
        }

        fun build(): YouTubeData {
            val gson = gsonBuilder.create()
            val okHttpClient = okHttpClientBuilder.build()
            return YouTubeData(okHttpClient, gson)
        }
    }
}
