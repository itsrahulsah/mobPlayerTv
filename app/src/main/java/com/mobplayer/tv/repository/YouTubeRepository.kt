package com.mobplayer.tv.repository

import com.mobplayer.tv.data.models.MediaItemModel
import com.mobplayer.tv.youtube.PlaybackSource
import com.mobplayer.tv.youtube.SmartTubePlayerEngine
import com.mobplayer.ytcrawler.YouTubeData
import com.mobplayer.ytcrawler.model.youtube.CompactVideo
import com.mobplayer.ytcrawler.model.youtube.Content
import com.mobplayer.ytcrawler.model.youtube.MultipleItemContent
import com.mobplayer.ytcrawler.model.youtube.NestedContent
import com.mobplayer.ytcrawler.model.youtube.SectionList
import com.mobplayer.ytcrawler.model.youtube.VideoWithContext
import javax.inject.Inject
import javax.inject.Singleton

enum class YouTubeCategory(val title: String, val subtitle: String) {
    ALL("All", "Everything on YouTube"),
    TRENDING("Trending", "What's hot right now"),
    RECOMMENDED("Recommended", "Picked for you"),
    MUSIC("Music", "Top songs & music videos"),
    NEWS("News", "Latest headlines"),
    MOVIES("Movies", "Trailers & film clips"),
    SPORTS("Sports", "Highlights & live sports"),
    // Declaration order is the on-screen order (category bar and Home rails); Gaming goes last
    GAMING("Gaming", "Gameplay, esports & streams");

    companion object {
        /** Categories shown as individual rails under "All". */
        val feedCategories = entries.filter { it != ALL }
    }
}

@Singleton
class YouTubeRepository @Inject constructor() {

    // Lazy: the OkHttp client setup isn't needed until the feed loads, after the first frame
    private val youTubeData: YouTubeData by lazy { YouTubeData.Builder().build() }

    suspend fun getVideos(category: YouTubeCategory): List<MediaItemModel> {
        val contents = when (category) {
            YouTubeCategory.ALL -> youTubeData.homeFeed().data.feed?.content.sectionItems()
            YouTubeCategory.TRENDING -> youTubeData.trendingFeed().data.feed?.content.sectionItems()
            YouTubeCategory.RECOMMENDED -> youTubeData.recommendedFeed().data.feed?.content.sectionItems()
            YouTubeCategory.MUSIC -> search("music songs")
            YouTubeCategory.GAMING -> search("gaming")
            YouTubeCategory.NEWS -> search("news today")
            YouTubeCategory.MOVIES -> search("movie trailers")
            YouTubeCategory.SPORTS -> search("sports highlights")
        }
        return toMediaItems(contents, idPrefix = category.name, label = category.title)
    }

    suspend fun searchVideos(query: String): List<MediaItemModel> =
        toMediaItems(search(query), idPrefix = "SEARCH", label = "Search")

    /** "Up next" videos YouTube suggests alongside [videoId], excluding the video itself. */
    suspend fun getSuggestions(videoId: String): List<MediaItemModel> {
        val upNext = youTubeData.watch(videoId, includeStreams = false).upNextVideos.filter { it.videoId != videoId }
        return toMediaItems(upNext, idPrefix = "NEXT", label = "Up next")
    }

    private fun toMediaItems(contents: List<Content>, idPrefix: String, label: String): List<MediaItemModel> =
        flattenVideos(contents)
            .filter { it.videoId.isNotBlank() }
            .distinctBy { it.videoId }
            .map { it.toMediaItem(idPrefix, label) }

    /**
     * Resolves a playable source via SmartTube's engine, which deciphers signatures and attaches
     * PO tokens. The crawler's own extractor returns no usable streams for most videos.
     */
    suspend fun resolvePlaybackSource(videoId: String): PlaybackSource =
        SmartTubePlayerEngine.resolvePlaybackSource(videoId)

    private suspend fun search(query: String): List<Content> =
        youTubeData.search(query).data.searchResult?.content.sectionItems()

    private fun Content?.sectionItems(): List<Content> = (this as? SectionList)?.items.orEmpty()

    private fun flattenVideos(contents: List<Content>): List<CompactVideo> = contents.flatMap { content ->
        when (content) {
            is CompactVideo -> listOf(content)
            is MultipleItemContent -> flattenVideos(content.items)
            is NestedContent -> flattenVideos(listOfNotNull(content.subContent))
            else -> emptyList()
        }
    }

    private fun CompactVideo.toMediaItem(idPrefix: String, label: String): MediaItemModel {
        val isLive = !liveBadge.isNullOrEmpty()
        val published = (this as? VideoWithContext)?.publishedTimeText
        return MediaItemModel(
            id = "yt_${idPrefix}_$videoId",
            title = title,
            subtitle = listOf(channelTitle, viewCountText, published.orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(" • "),
            description = title,
            posterUrl = thumbnailUrl,
            backdropUrl = thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" },
            genres = listOf("YouTube", label),
            year = published.orEmpty(),
            duration = lengthText.orEmpty(),
            rating = "",
            contentRating = "",
            badge = if (isLive) liveBadge else lengthText,
            videoUrl = "",
            isLive = isLive,
            youtubeVideoId = videoId
        )
    }
}
