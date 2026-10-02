package com.mobplayer.ytcrawler

import com.mobplayer.ytcrawler.model.youtube.*
import com.google.gson.GsonBuilder
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class YouTubeModelsTest {

    private lateinit var gson: com.google.gson.Gson

    @Before
    fun setUp() {
        gson = GsonBuilder()
            .registerTypeAdapter(CompactVideo::class.java, CompactVideo.TypeAdapter())
            .registerTypeAdapter(CompactPlaylist::class.java, CompactPlaylist.TypeAdapter())
            .registerTypeAdapter(Feed::class.java, Feed.TypeAdapter())
            .registerTypeAdapter(FeedResponse::class.java, FeedResponse.TypeAdapter())
            .registerTypeAdapter(SearchResponse::class.java, SearchResponse.TypeAdapter())
            .registerTypeAdapter(SearchResult::class.java, SearchResult.TypeAdapter())
            .setLenient()
            .create()
    }

    @Test
    fun testCompactVideoDeserialization() {
        val json = """
            {
                "encrypted_id": "dQw4w9WgXcQ",
                "title": { "runs": [{ "text": "Rick Astley - Never Gonna Give You Up" }] },
                "short_byline": { "runs": [{ "text": "Rick Astley" }] },
                "length": { "runs": [{ "text": "3:32" }] },
                "view_count": { "runs": [{ "text": "1.5B views" }] },
                "thumbnail_info": { "url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg" },
                "endpoint": { "url": "/watch?v=dQw4w9WgXcQ" }
            }
        """.trimIndent()

        val video = gson.fromJson(json, CompactVideo::class.java)
        assertNotNull(video)
        assertEquals("dQw4w9WgXcQ", video.videoId)
        assertEquals("Rick Astley - Never Gonna Give You Up", video.title)
        assertEquals("Rick Astley", video.channelTitle)
        assertEquals("3:32", video.lengthText)
        assertEquals("1.5B views", video.viewCountText)
    }

    @Test
    fun testCompactPlaylistDeserialization() {
        val json = """
            {
                "playlist_id": "PL12345",
                "title": { "runs": [{ "text": "Top Hits 2026" }] },
                "owner": { "runs": [{ "text": "Music Channel" }] },
                "video_count_short": { "runs": [{ "text": "50 videos" }] },
                "thumbnail_info": { "url": "https://i.ytimg.com/pl/12345.jpg" },
                "endpoint": { "url": "/playlist?list=PL12345" }
            }
        """.trimIndent()

        val playlist = gson.fromJson(json, CompactPlaylist::class.java)
        assertNotNull(playlist)
        assertEquals("PL12345", playlist.playlistId)
        assertEquals("Top Hits 2026", playlist.title)
        assertEquals("Music Channel", playlist.owner)
        assertEquals("50 videos", playlist.videoCountText)
    }

    @Test
    fun testPlaylistResponseModel() {
        val video = CompactVideo(
            title = "Sample Video",
            channelTitle = "Sample Channel",
            lengthText = "4:12",
            viewCountText = "100K",
            thumbnailUrl = "https://thumb.url",
            videoId = "sampleId",
            endpoint = "/watch?v=sampleId"
        )
        val playlistResponse = PlaylistResponse(
            playlistId = "PLtest",
            title = "Test Playlist",
            owner = "Tester",
            description = "Playlist Description",
            thumbnailUrl = "https://thumb.url",
            videoCountText = "1 video",
            videos = listOf(video)
        )

        assertEquals("PLtest", playlistResponse.playlistId)
        assertEquals(1, playlistResponse.videos.size)
        assertEquals("sampleId", playlistResponse.videos[0].videoId)
    }

    @Test
    fun testWatchResponseModel() {
        val video = CompactVideo(
            title = "Queued Video",
            channelTitle = "Artist",
            lengthText = "3:00",
            viewCountText = "200K",
            thumbnailUrl = "https://thumb.url",
            videoId = "queuedId",
            endpoint = "/watch?v=queuedId"
        )
        val watchResponse = WatchResponse(
            videoId = "currentId",
            title = "Current Video",
            channelTitle = "Artist",
            playlistId = "PLtest",
            playlistTitle = "Test Queue",
            playlistQueue = listOf(video)
        )

        assertEquals("currentId", watchResponse.videoId)
        assertEquals("PLtest", watchResponse.playlistId)
        assertEquals(1, watchResponse.playlistQueue.size)
        assertEquals("queuedId", watchResponse.playlistQueue[0].videoId)
    }
}
