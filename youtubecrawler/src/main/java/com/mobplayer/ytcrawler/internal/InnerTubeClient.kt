package com.mobplayer.ytcrawler.internal

import com.mobplayer.ytcrawler.Const
import com.mobplayer.ytcrawler.exception.VideoNotAvailableException
import com.mobplayer.ytcrawler.model.ExtractorResult
import com.mobplayer.ytcrawler.model.youtube.*
import com.mobplayer.ytcrawler.model.youtube.stream.*
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class InnerTubeClient(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        const val API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        private const val BASE_URL = "https://www.youtube.com/youtubei/v1"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private fun createWebContext(): JsonObject {
        val client = JsonObject().apply {
            addProperty("clientName", "WEB")
            addProperty("clientVersion", "2.20260918.00.00")
            addProperty("hl", "en")
            addProperty("gl", "US")
        }
        return JsonObject().apply { add("client", client) }
    }

    private fun createAndroidVrContext(): JsonObject {
        val client = JsonObject().apply {
            addProperty("clientName", "ANDROID_VR")
            addProperty("clientVersion", "1.61.48")
            addProperty("hl", "en")
            addProperty("gl", "US")
        }
        return JsonObject().apply { add("client", client) }
    }

    private fun createIosContext(): JsonObject {
        val client = JsonObject().apply {
            addProperty("clientName", "IOS")
            addProperty("clientVersion", "21.26.4")
            addProperty("deviceMake", "Apple")
            addProperty("deviceModel", "iPhone16,2")
            addProperty("osName", "iPhone")
            addProperty("osVersion", "18.3.2.22D82")
            addProperty("hl", "en")
            addProperty("gl", "US")
        }
        return JsonObject().apply { add("client", client) }
    }

    private suspend fun postJson(
        endpoint: String,
        body: JsonObject,
        userAgent: String = "Mozilla/5.0",
        extraHeaders: Map<String, String> = emptyMap()
    ): JsonObject =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/$endpoint?key=$API_KEY"
            val builder = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", userAgent)
            for ((k, v) in extraHeaders) {
                builder.addHeader(k, v)
            }
            val request = builder.post(body.toString().toRequestBody(JSON_MEDIA_TYPE)).build()

            val response = okHttpClient.newCall(request).execute()
            val raw = response.body?.string() ?: "{}"
            gson.fromJson(raw, JsonObject::class.java)
        }

    suspend fun browseFeed(browseId: String = "FEwhat_to_watch"): List<Content> {
        val contents = try {
            val body = JsonObject().apply {
                add("context", createWebContext())
                addProperty("browseId", browseId)
            }
            val root = postJson("browse", body)
            parseBrowseContents(root)
        } catch (e: Exception) {
            emptyList()
        }

        if (contents.isNotEmpty()) return contents

        // When YouTube returns feedNudgeRenderer for unauthenticated web requests or 400 for FEtrending,
        // automatically fallback to rich search feeds so the UI is never blank
        val fallbackQuery = when (browseId) {
            "FEtrending" -> "trending"
            else -> "trending videos"
        }
        return search(fallbackQuery)
    }

    suspend fun search(query: String): List<Content> {
        val body = JsonObject().apply {
            add("context", createWebContext())
            addProperty("query", query)
        }
        val root = postJson("search", body)
        return parseSearchResults(root)
    }

    suspend fun getPlaylist(playlistId: String): PlaylistResponse {
        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val body = JsonObject().apply {
            add("context", createWebContext())
            addProperty("browseId", browseId)
        }
        val root = postJson("browse", body)
        return parsePlaylistResponse(playlistId, root)
    }

    suspend fun getWatchNext(videoId: String, playlistId: String? = null, includeStreams: Boolean = true): WatchResponse {
        val body = JsonObject().apply {
            add("context", createWebContext())
            addProperty("videoId", videoId)
            if (!playlistId.isNullOrEmpty()) {
                addProperty("playlistId", playlistId)
            }
        }
        val root = postJson("next", body)
        val streamResult = if (includeStreams) extractStreams(videoId) else null
        return parseWatchResponse(videoId, playlistId, root, streamResult)
    }

    suspend fun extractStreams(videoId: String): ExtractorResult {
        // Use iOS client first for direct, pre-verified unthrottled HLS & adaptive streams
        val iosBody = JsonObject().apply {
            add("context", createIosContext())
            addProperty("videoId", videoId)
            addProperty("contentCheckOk", true)
            addProperty("racyCheckOk", true)
        }
        val iosHeaders = mapOf(
            "X-YouTube-Client-Name" to "5",
            "X-YouTube-Client-Version" to "21.26.4"
        )
        val root = try {
            val res = postJson(
                "player",
                iosBody,
                "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                iosHeaders
            )
            val st = res.getAsJsonObject("playabilityStatus")?.get("status")?.asString
            if (st == "OK") res else throw IllegalStateException(st)
        } catch (e: Exception) {
            val body = JsonObject().apply {
                add("context", createAndroidVrContext())
                addProperty("videoId", videoId)
            }
            postJson("player", body, "com.google.android.apps.youtube.vr/1.61.48")
        }

        val playability = root.getAsJsonObject("playabilityStatus")
        val status = playability?.get("status")?.asString
        if (status != null && status != "OK") {
            val reason = playability.get("reason")?.asString ?: "Video unavailable"
            throw VideoNotAvailableException(reason, videoId)
        }

        val videoDetails = root.getAsJsonObject("videoDetails")
        val title = videoDetails?.get("title")?.asString ?: "YouTube Video"

        val streamingData = root.getAsJsonObject("streamingData") ?: JsonObject()
        val streams = mutableListOf<YouTubeStream>()

        // Progressive formats (MP4 360p / 720p with audio included)
        val progressiveFormats = streamingData.getAsJsonArray("formats")
        if (progressiveFormats != null) {
            for (elem in progressiveFormats) {
                val f = elem.asJsonObject
                val url = f.get("url")?.asString ?: continue
                val itag = f.get("itag")?.asString ?: "18"
                val mimeType = f.get("mimeType")?.asString ?: "video/mp4"
                val width = f.get("width")?.asInt ?: 640
                val height = f.get("height")?.asInt ?: 360
                val bitrate = f.get("bitrate")?.asInt ?: 0
                val stream = YouTubeNonDashStream(
                    urlLazy = UrlLazy { url },
                    expireAt = Const.UNKNOWN_VALUE.toLong(),
                    itag = itag,
                    container = "mp4",
                    mimeType = mimeType,
                    width = width,
                    height = height,
                    audioBitrate = bitrate / 1000,
                    audioCodec = "aac",
                    videoCodec = "h264"
                )
                streams.add(stream)
            }
        }

        // Adaptive formats (1080p, 720p, 480p video-only & high quality audio-only)
        val adaptiveFormats = streamingData.getAsJsonArray("adaptiveFormats")
        if (adaptiveFormats != null) {
            for (elem in adaptiveFormats) {
                val f = elem.asJsonObject
                val url = f.get("url")?.asString ?: continue
                val itag = f.get("itag")?.asString ?: ""
                val mimeType = f.get("mimeType")?.asString ?: ""
                val bitrate = f.get("bitrate")?.asInt ?: 0
                val contentLength = f.get("contentLength")?.asString?.toIntOrNull() ?: 0

                if (mimeType.startsWith("audio")) {
                    val audioStream = YouTubeDashAudioStream(
                        urlLazy = UrlLazy { url },
                        expireAt = Const.UNKNOWN_VALUE.toLong(),
                        itag = itag,
                        container = if (mimeType.contains("webm")) "webm" else "m4a",
                        mimeType = mimeType,
                        bandwidth = bitrate,
                        contentLength = contentLength,
                        segmentBase = null,
                        codec = null,
                        audioChannelCount = 2
                    )
                    streams.add(audioStream)
                } else if (mimeType.startsWith("video")) {
                    val width = f.get("width")?.asInt ?: 0
                    val height = f.get("height")?.asInt ?: 0
                    val fps = f.get("fps")?.asInt ?: 30
                    val videoStream = YouTubeDashVideoStream(
                        urlLazy = UrlLazy { url },
                        expireAt = Const.UNKNOWN_VALUE.toLong(),
                        itag = itag,
                        container = if (mimeType.contains("webm")) "webm" else "mp4",
                        mimeType = mimeType,
                        bandwidth = bitrate,
                        contentLength = contentLength,
                        segmentBase = null,
                        width = width,
                        height = height,
                        codec = null,
                        fps = fps
                    )
                    streams.add(videoStream)
                }
            }
        }

        // HLS if live or iOS master playlist
        val hlsManifestUrl = streamingData.get("hlsManifestUrl")?.asString
        if (!hlsManifestUrl.isNullOrEmpty()) {
            streams.add(0, YouTubeLiveStream(hlsManifestUrl, YouTubeLiveStream.Type.HLS))
        }

        return ExtractorResult(
            vid = videoId,
            title = title,
            dashManifestInfo = null,
            streams = streams,
            subtitlesLazy = null
        )
    }

    private fun parseBrowseContents(root: JsonObject): List<Content> {
        val contents = mutableListOf<Content>()
        val twoCol = root.getAsJsonObject("contents")?.getAsJsonObject("twoColumnBrowseResultsRenderer")
        val tabs = twoCol?.getAsJsonArray("tabs") ?: root.getAsJsonObject("contents")
            ?.getAsJsonObject("singleColumnBrowseResultsRenderer")?.getAsJsonArray("tabs")
        if (tabs == null || tabs.size() == 0) return contents

        val firstTab = tabs[0].asJsonObject.getAsJsonObject("tabRenderer")
        val tabContent = firstTab?.getAsJsonObject("content") ?: return contents

        val richGrid = tabContent.getAsJsonObject("richGridRenderer")
        if (richGrid != null) {
            val items = richGrid.getAsJsonArray("contents")
            if (items != null) {
                for (item in items) {
                    val itemObj = item.asJsonObject
                    val richItem = itemObj.getAsJsonObject("richItemRenderer")?.getAsJsonObject("content")
                    if (richItem != null) {
                        parseVideoOrPlaylist(richItem)?.let { contents.add(it) }
                    }
                    val richSection = itemObj.getAsJsonObject("richSectionRenderer")?.getAsJsonObject("content")
                    if (richSection != null) {
                        val shelf = richSection.getAsJsonObject("richShelfRenderer")
                        if (shelf != null) {
                            val shelfTitle = TypeAdapterUtils.parseFormattedString(shelf.getAsJsonObject("title"))
                            val shelfItems = mutableListOf<Content>()
                            val shelfContents = shelf.getAsJsonArray("contents")
                            if (shelfContents != null) {
                                for (sItem in shelfContents) {
                                    val subContent = sItem.asJsonObject.getAsJsonObject("richItemRenderer")
                                        ?.getAsJsonObject("content")
                                    if (subContent != null) {
                                        parseVideoOrPlaylist(subContent)?.let { shelfItems.add(it) }
                                    }
                                }
                            }
                            contents.add(Shelf(MultipleItemContent("shelf_items", shelfItems), null, shelfTitle, null, null, null))
                        }
                    }
                }
            }
        }

        val sectionList = tabContent.getAsJsonObject("sectionListRenderer")
        if (sectionList != null) {
            val sections = sectionList.getAsJsonArray("contents")
            if (sections != null) {
                for (sec in sections) {
                    val itemSec = sec.asJsonObject.getAsJsonObject("itemSectionRenderer")
                    val items = itemSec?.getAsJsonArray("contents")
                    if (items != null) {
                        for (it in items) {
                            parseVideoOrPlaylist(it.asJsonObject)?.let { contents.add(it) }
                        }
                    }
                }
            }
        }

        return contents
    }

    private fun parseSearchResults(root: JsonObject): List<Content> {
        val contents = mutableListOf<Content>()
        val searchResults = root.getAsJsonObject("contents")
            ?.getAsJsonObject("twoColumnSearchResultsRenderer")
            ?.getAsJsonObject("primaryContents")
            ?.getAsJsonObject("sectionListRenderer")
            ?.getAsJsonArray("contents") ?: return contents

        for (sec in searchResults) {
            val itemSec = sec.asJsonObject.getAsJsonObject("itemSectionRenderer")
            val items = itemSec?.getAsJsonArray("contents") ?: continue
            for (elem in items) {
                val itemObj = elem.asJsonObject
                parseVideoOrPlaylist(itemObj)?.let { contents.add(it) }
                if (itemObj.has("shelfRenderer")) {
                    val sr = itemObj.getAsJsonObject("shelfRenderer")
                    val vList = sr.getAsJsonObject("content")?.getAsJsonObject("verticalListRenderer")
                    val vItems = vList?.getAsJsonArray("items")
                    if (vItems != null) {
                        for (vElem in vItems) {
                            parseVideoOrPlaylist(vElem.asJsonObject)?.let { contents.add(it) }
                        }
                    }
                }
            }
        }
        return contents
    }

    private fun parseVideoOrPlaylist(obj: JsonObject): Content? {
        if (obj.has("videoRenderer")) {
            val vr = obj.getAsJsonObject("videoRenderer")
            val videoId = vr.get("videoId")?.asString ?: ""
            val title = TypeAdapterUtils.parseFormattedString(vr.getAsJsonObject("title")) ?: ""
            val length = TypeAdapterUtils.parseFormattedString(vr.getAsJsonObject("lengthText"))
            val viewCount = TypeAdapterUtils.parseFormattedString(vr.getAsJsonObject("viewCountText")) ?: ""
            val channelTitle = TypeAdapterUtils.parseFormattedString(vr.getAsJsonObject("ownerText")) ?: ""
            val thumb = getBestThumbnail(vr.getAsJsonObject("thumbnail"))
            return CompactVideo(
                title = title,
                channelTitle = channelTitle,
                lengthText = length,
                viewCountText = viewCount,
                thumbnailUrl = thumb,
                videoId = videoId,
                endpoint = "/watch?v=$videoId"
            )
        }

        if (obj.has("lockupViewModel")) {
            val lvm = obj.getAsJsonObject("lockupViewModel")
            val contentId = lvm.get("contentId")?.asString ?: ""
            val contentType = lvm.get("contentType")?.asString ?: ""
            val meta = lvm.getAsJsonObject("metadata")?.getAsJsonObject("lockupMetadataViewModel")
            val title = meta?.getAsJsonObject("title")?.get("content")?.asString ?: ""
            val rows = meta?.getAsJsonObject("metadata")?.getAsJsonObject("contentMetadataViewModel")?.getAsJsonArray("metadataRows")
            var channelTitle = ""
            var viewCount = ""
            if (rows != null && rows.size() > 0) {
                val firstParts = rows[0].asJsonObject.getAsJsonArray("metadataParts")
                if (firstParts != null && firstParts.size() > 0) {
                    channelTitle = firstParts[0].asJsonObject.getAsJsonObject("text")?.get("content")?.asString ?: ""
                }
                if (rows.size() > 1) {
                    val secondParts = rows[1].asJsonObject.getAsJsonArray("metadataParts")
                    if (secondParts != null && secondParts.size() > 0) {
                        viewCount = secondParts[0].asJsonObject.getAsJsonObject("text")?.get("content")?.asString ?: ""
                    }
                }
            }
            val thumb = getBestThumbnailFromViewModel(lvm.getAsJsonObject("contentImage"))

            if (contentType == "LOCKUP_CONTENT_TYPE_PLAYLIST") {
                return CompactPlaylist(
                    title = title,
                    owner = channelTitle,
                    videoCountText = viewCount,
                    thumbnailUrl = thumb,
                    playlistId = contentId,
                    endpoint = "/playlist?list=$contentId"
                )
            } else if (contentType == "LOCKUP_CONTENT_TYPE_VIDEO") {
                return CompactVideo(
                    title = title,
                    channelTitle = channelTitle,
                    lengthText = null,
                    viewCountText = viewCount,
                    thumbnailUrl = thumb,
                    videoId = contentId,
                    endpoint = "/watch?v=$contentId"
                )
            }
            // Albums, podcasts, etc.: contentId isn't a video id, so a card would never play.
            return null
        }

        if (obj.has("compactVideoRenderer")) {
            val cv = obj.getAsJsonObject("compactVideoRenderer")
            val videoId = cv.get("videoId")?.asString ?: ""
            val title = TypeAdapterUtils.parseFormattedString(cv.getAsJsonObject("title")) ?: ""
            val channelTitle = TypeAdapterUtils.parseFormattedString(cv.getAsJsonObject("shortBylineText")) ?: ""
            val length = TypeAdapterUtils.parseFormattedString(cv.getAsJsonObject("lengthText"))
            val viewCount = TypeAdapterUtils.parseFormattedString(cv.getAsJsonObject("viewCountText")) ?: ""
            val thumb = getBestThumbnail(cv.getAsJsonObject("thumbnail"))
            return CompactVideo(
                title = title,
                channelTitle = channelTitle,
                lengthText = length,
                viewCountText = viewCount,
                thumbnailUrl = thumb,
                videoId = videoId,
                endpoint = "/watch?v=$videoId"
            )
        }

        if (obj.has("playlistRenderer")) {
            val pr = obj.getAsJsonObject("playlistRenderer")
            val playlistId = pr.get("playlistId")?.asString ?: ""
            val title = TypeAdapterUtils.parseFormattedString(pr.getAsJsonObject("title")) ?: ""
            val owner = TypeAdapterUtils.parseFormattedString(pr.getAsJsonObject("shortBylineText")) ?: ""
            val videoCount = pr.get("videoCount")?.asString ?: ""
            val thumb = getBestThumbnail(pr.getAsJsonObject("thumbnail"))
            return CompactPlaylist(
                title = title,
                owner = owner,
                videoCountText = "$videoCount videos",
                thumbnailUrl = thumb,
                playlistId = playlistId,
                endpoint = "/playlist?list=$playlistId"
            )
        }

        if (obj.has("channelRenderer")) {
            val cr = obj.getAsJsonObject("channelRenderer")
            val channelId = cr.get("channelId")?.asString ?: ""
            val title = TypeAdapterUtils.parseFormattedString(cr.getAsJsonObject("title")) ?: ""
            val videoCount = TypeAdapterUtils.parseFormattedString(cr.getAsJsonObject("videoCountText")) ?: ""
            val subCount = TypeAdapterUtils.parseFormattedString(cr.getAsJsonObject("subscriberCountText"))
            val thumb = getBestThumbnail(cr.getAsJsonObject("thumbnail"))
            return CompactChannel(
                title = title,
                thumbnailUrl = thumb,
                videoCountText = videoCount,
                subscriberCount = subCount,
                endpoint = "/channel/$channelId"
            )
        }

        return null
    }

    private fun parsePlaylistResponse(playlistId: String, root: JsonObject): PlaylistResponse {
        var title = "Playlist"
        var owner = ""
        var thumb = ""
        var countText = ""
        val videos = mutableListOf<CompactVideo>()

        val header = root.getAsJsonObject("header")?.getAsJsonObject("playlistHeaderRenderer")
        if (header != null) {
            title = TypeAdapterUtils.parseFormattedString(header.getAsJsonObject("title")) ?: "Playlist"
            owner = TypeAdapterUtils.parseFormattedString(header.getAsJsonObject("ownerText")) ?: ""
            thumb = getBestThumbnail(header.getAsJsonObject("playlistHeaderBanner"))
            countText = TypeAdapterUtils.parseFormattedString(header.getAsJsonObject("numVideosText")) ?: ""
        }

        val twoCol = root.getAsJsonObject("contents")?.getAsJsonObject("twoColumnBrowseResultsRenderer")
        val tabs = twoCol?.getAsJsonArray("tabs")
        if (tabs != null && tabs.size() > 0) {
            val tab = tabs[0].asJsonObject.getAsJsonObject("tabRenderer")
            val secList = tab?.getAsJsonObject("content")?.getAsJsonObject("sectionListRenderer")?.getAsJsonArray("contents")
            if (secList != null) {
                for (s in secList) {
                    val contents = s.asJsonObject.getAsJsonObject("itemSectionRenderer")?.getAsJsonArray("contents") ?: continue
                    for (item in contents) {
                        val itemObj = item.asJsonObject
                        if (itemObj.has("playlistVideoListRenderer")) {
                            val vids = itemObj.getAsJsonObject("playlistVideoListRenderer").getAsJsonArray("contents")
                            if (vids != null) {
                                for (v in vids) {
                                    val pvr = v.asJsonObject.getAsJsonObject("playlistVideoRenderer") ?: continue
                                    val vid = pvr.get("videoId")?.asString ?: continue
                                    val vTitle = TypeAdapterUtils.parseFormattedString(pvr.getAsJsonObject("title")) ?: ""
                                    val vChannel = TypeAdapterUtils.parseFormattedString(pvr.getAsJsonObject("shortBylineText")) ?: owner
                                    val vLength = TypeAdapterUtils.parseFormattedString(pvr.getAsJsonObject("lengthText"))
                                    val vThumb = getBestThumbnail(pvr.getAsJsonObject("thumbnail"))
                                    videos.add(
                                        CompactVideo(
                                            title = vTitle,
                                            channelTitle = vChannel,
                                            lengthText = vLength,
                                            viewCountText = "",
                                            thumbnailUrl = vThumb,
                                            videoId = vid,
                                            endpoint = "/watch?v=$vid&list=$playlistId"
                                        )
                                    )
                                }
                            }
                        } else if (itemObj.has("lockupViewModel")) {
                            val lvm = itemObj.getAsJsonObject("lockupViewModel")
                            val vid = lvm.get("contentId")?.asString ?: continue
                            val meta = lvm.getAsJsonObject("metadata")?.getAsJsonObject("lockupMetadataViewModel")
                            val vTitle = meta?.getAsJsonObject("title")?.get("content")?.asString ?: ""
                            val vThumb = getBestThumbnailFromViewModel(lvm.getAsJsonObject("contentImage"))
                            videos.add(
                                CompactVideo(
                                    title = vTitle,
                                    channelTitle = owner,
                                    lengthText = null,
                                    viewCountText = "",
                                    thumbnailUrl = vThumb,
                                    videoId = vid,
                                    endpoint = "/watch?v=$vid&list=$playlistId"
                                )
                            )
                        }
                    }
                }
            }
        }

        if (thumb.isEmpty() && videos.isNotEmpty()) {
            thumb = videos[0].thumbnailUrl
        }

        return PlaylistResponse(
            playlistId = playlistId,
            title = title,
            owner = owner,
            thumbnailUrl = thumb,
            videoCountText = if (countText.isNotEmpty()) countText else "${videos.size} videos",
            videos = videos
        )
    }

    private fun parseWatchResponse(
        videoId: String,
        playlistId: String?,
        root: JsonObject,
        extractorResult: ExtractorResult?
    ): WatchResponse {
        var title = extractorResult?.title ?: "Playing Video"
        var channelTitle = ""
        var channelAvatar = ""
        var viewCount = ""
        var publishDate = ""
        var likes = ""
        var description = ""
        var playlistTitle: String? = null
        val playlistQueue = mutableListOf<CompactVideo>()
        val upNextVideos = mutableListOf<CompactVideo>()

        val twoCol = root.getAsJsonObject("contents")?.getAsJsonObject("twoColumnWatchNextResults")
        if (twoCol != null) {
            val results = twoCol.getAsJsonObject("results")?.getAsJsonObject("results")?.getAsJsonArray("contents")
            if (results != null) {
                for (elem in results) {
                    val videoPrimary = elem.asJsonObject.getAsJsonObject("videoPrimaryInfoRenderer")
                    if (videoPrimary != null) {
                        title = TypeAdapterUtils.parseFormattedString(videoPrimary.getAsJsonObject("title")) ?: title
                        viewCount = TypeAdapterUtils.parseFormattedString(videoPrimary.getAsJsonObject("viewCount")) ?: ""
                        publishDate = TypeAdapterUtils.parseFormattedString(videoPrimary.getAsJsonObject("dateText")) ?: ""
                    }
                    val videoSecondary = elem.asJsonObject.getAsJsonObject("videoSecondaryInfoRenderer")
                    if (videoSecondary != null) {
                        val owner = videoSecondary.getAsJsonObject("owner")?.getAsJsonObject("videoOwnerRenderer")
                        channelTitle = TypeAdapterUtils.parseFormattedString(owner?.getAsJsonObject("title")) ?: ""
                        channelAvatar = getBestThumbnail(owner?.getAsJsonObject("thumbnail"))
                        description = TypeAdapterUtils.parseFormattedString(videoSecondary.getAsJsonObject("description")) ?: ""
                    }
                }
            }

            // Playlist Queue (when in playlist mode)
            val pl = twoCol.getAsJsonObject("playlist")?.getAsJsonObject("playlist")
            if (pl != null) {
                playlistTitle = pl.get("title")?.asString
                val plContents = pl.getAsJsonArray("contents")
                if (plContents != null) {
                    for (item in plContents) {
                        val pvr = item.asJsonObject.getAsJsonObject("playlistPanelVideoRenderer") ?: continue
                        val vId = pvr.get("videoId")?.asString ?: continue
                        val vTitle = TypeAdapterUtils.parseFormattedString(pvr.getAsJsonObject("title")) ?: ""
                        val vChannel = TypeAdapterUtils.parseFormattedString(pvr.getAsJsonObject("shortBylineText")) ?: ""
                        val vLength = TypeAdapterUtils.parseFormattedString(pvr.getAsJsonObject("lengthText"))
                        val vThumb = getBestThumbnail(pvr.getAsJsonObject("thumbnail"))
                        playlistQueue.add(
                            CompactVideo(
                                title = vTitle,
                                channelTitle = vChannel,
                                lengthText = vLength,
                                viewCountText = "",
                                thumbnailUrl = vThumb,
                                videoId = vId,
                                endpoint = "/watch?v=$vId&list=${playlistId ?: ""}"
                            )
                        )
                    }
                }
            }

            // Up Next Recommendations
            val secondary = twoCol.getAsJsonObject("secondaryResults")?.getAsJsonObject("secondaryResults")?.getAsJsonArray("results")
            if (secondary != null) {
                for (item in secondary) {
                    val content = parseVideoOrPlaylist(item.asJsonObject)
                    if (content is CompactVideo) {
                        upNextVideos.add(content)
                    }
                }
            }
        }

        return WatchResponse(
            videoId = videoId,
            title = title,
            channelTitle = channelTitle,
            channelAvatarUrl = channelAvatar,
            viewCountText = viewCount,
            publishDateText = publishDate,
            likesText = likes,
            description = description,
            playlistId = playlistId,
            playlistTitle = playlistTitle,
            playlistQueue = playlistQueue,
            upNextVideos = upNextVideos,
            extractorResult = extractorResult
        )
    }

    private fun getBestThumbnail(thumbObj: JsonObject?): String {
        if (thumbObj == null) return ""
        val thumbnails = thumbObj.getAsJsonArray("thumbnails")
        if (thumbnails != null && thumbnails.size() > 0) {
            val best = thumbnails[thumbnails.size() - 1].asJsonObject
            return best.get("url")?.asString ?: ""
        }
        return thumbObj.get("url")?.asString ?: ""
    }

    private fun getBestThumbnailFromViewModel(contentImage: JsonObject?): String {
        if (contentImage == null) return ""
        val sources = contentImage.getAsJsonObject("thumbnailViewModel")
            ?.getAsJsonObject("image")
            ?.getAsJsonArray("sources")
        if (sources != null && sources.size() > 0) {
            val best = sources[sources.size() - 1].asJsonObject
            return best.get("url")?.asString ?: ""
        }
        return ""
    }
}
