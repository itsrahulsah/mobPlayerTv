package com.mobplayer.ytcrawler.model.youtube

import com.mobplayer.ytcrawler.model.ExtractorResult
import com.google.gson.*
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

class WindowSettings(
    @SerializedName("build_id") val buildId: String? = null,
    @SerializedName("build_label") val buildLabel: String? = null,
    @SerializedName("client_name") val clientName: String? = null,
    @SerializedName("client_version") val clientVersion: String? = null,
    @SerializedName("variants_checksum") val variantChecksum: String? = null
)

open class Content(val itemType: String)

open class MultipleItemContent(
    itemType: String,
    val items: List<Content> = emptyList()
) : Content(itemType)

open class NestedContent(
    itemType: String,
    val subContent: Content? = null
) : Content(itemType)

interface Continuation {
    val continuationToken: String?
    val clickTrackingParams: String?
}

abstract class AbstractResponse(
    val result: String,
    val timestamp: Long
) {
    companion object {
        const val RESULT_OK = "ok"
    }

    val isSuccess: Boolean
        get() = RESULT_OK.equals(result, ignoreCase = true)

    abstract val hasContinuation: Boolean
    abstract val continuation: Continuation?
}

open class CompactVideo(
    itemType: String = ITEM_TYPE,
    val title: String,
    val channelTitle: String,
    val lengthText: String?,
    val viewCountText: String,
    val thumbnailUrl: String,
    val liveBadge: String? = null,
    val videoId: String,
    val endpoint: String
) : Content(itemType) {
    companion object {
        const val ITEM_TYPE = "compact_video"
    }

    class TypeAdapter : JsonDeserializer<CompactVideo> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): CompactVideo {
            if (!json.isJsonObject) throw JsonParseException("Invalid CompactVideo")
            val obj = json.asJsonObject
            val title = TypeAdapterUtils.parseFormattedString(obj.getAsJsonObject("title")) ?: ""
            val viewCount = TypeAdapterUtils.parseFormattedString(obj.getAsJsonObject("view_count")) ?: ""
            val byline = TypeAdapterUtils.parseFormattedString(obj.getAsJsonObject("short_byline")) ?: ""
            val length = TypeAdapterUtils.parseFormattedString(obj.getAsJsonObject("length"))
            val thumbnail = obj.getAsJsonObject("thumbnail_info")?.get("url")?.asString ?: ""
            val videoId = obj.get("encrypted_id")?.asString ?: ""
            val endpoint = obj.getAsJsonObject("endpoint")?.get("url")?.asString ?: ""

            var liveBadge: String? = null
            val badges = obj.getAsJsonArray("badges")
            if (badges != null && badges.size() > 0) {
                for (elem in badges) {
                    val badgeObj = elem.asJsonObject
                    val itemType = TypeAdapterUtils.safeGet(badgeObj, TypeAdapterUtils.KEY_ITEM_TYPE, "")
                    if ("live_badge".equals(itemType, ignoreCase = true)) {
                        liveBadge = TypeAdapterUtils.parseFormattedString(badgeObj.getAsJsonObject("label"))
                    }
                }
            }
            return CompactVideo(
                ITEM_TYPE, title, byline, length, viewCount, thumbnail, liveBadge, videoId, endpoint
            )
        }
    }
}

class VideoWithContext(
    title: String,
    bylineText: String,
    viewCountText: String,
    lengthText: String?,
    val publishedTimeText: String?,
    val channelThumbnailUrl: String?,
    thumbnailUrl: String,
    videoId: String,
    endpoint: String,
    val channelEndpoint: String?
) : CompactVideo(
    ITEM_TYPE, title, bylineText, lengthText, viewCountText, thumbnailUrl, null, videoId, endpoint
) {
    companion object {
        const val ITEM_TYPE = "video_with_context"
    }

    class TypeAdapter : JsonDeserializer<VideoWithContext> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): VideoWithContext {
            if (!json.isJsonObject) throw JsonParseException("Invalid VideoWithContext")
            val obj = json.asJsonObject
            val title = TypeAdapterUtils.parseFormattedString(obj.get("headline")) ?: ""
            val bylineText = TypeAdapterUtils.parseFormattedString(obj.get("short_byline_text")) ?: ""
            val viewCount = TypeAdapterUtils.parseFormattedString(obj.get("short_view_count_text")) ?: ""
            val length = TypeAdapterUtils.parseFormattedString(obj.get("length_text"))
            val publishedTime = TypeAdapterUtils.parseFormattedString(obj.get("published_time_text"))
            val videoId = obj.get("video_id")?.asString ?: ""
            val thumbnail = obj.getAsJsonObject("thumbnail_info")?.get("url")?.asString ?: ""
            val endpoint = obj.getAsJsonObject("navigation_endpoint")?.get("url")?.asString ?: ""
            val channel = obj.getAsJsonObject("channel_thumbnail")
            val channelThumbnail = channel?.getAsJsonObject("thumbnail_info")?.get("url")?.asString
            val channelEndpoint = channel?.getAsJsonObject("navigation_endpoint")?.get("url")?.asString
            return VideoWithContext(
                title, bylineText, viewCount, length, publishedTime,
                channelThumbnail, thumbnail, videoId, endpoint, channelEndpoint
            )
        }
    }
}

class CompactPlaylist(
    val title: String,
    val owner: String,
    val videoCountText: String,
    val thumbnailUrl: String,
    val playlistId: String,
    val endpoint: String
) : Content(ITEM_TYPE) {
    companion object {
        const val ITEM_TYPE = "compact_playlist"
    }

    class TypeAdapter : JsonDeserializer<CompactPlaylist> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): CompactPlaylist {
            if (!json.isJsonObject) throw JsonParseException("Invalid CompactPlaylist")
            val obj = json.asJsonObject
            val title = TypeAdapterUtils.parseFormattedString(obj.get("title")) ?: ""
            val owner = TypeAdapterUtils.parseFormattedString(obj.get("owner")) ?: ""
            val videoCount = TypeAdapterUtils.parseFormattedString(obj.get("video_count_short")) ?: ""
            val thumbnail = obj.getAsJsonObject("thumbnail_info")?.get("url")?.asString ?: ""
            val playlistId = obj.get("playlist_id")?.asString ?: ""
            val endpoint = obj.getAsJsonObject("endpoint")?.get("url")?.asString ?: ""
            return CompactPlaylist(title, owner, videoCount, thumbnail, playlistId, endpoint)
        }
    }
}

class CompactRadio(
    val title: String,
    val videoCountShortText: String,
    val thumbnailUrl: String,
    val playlistId: String,
    val endpoint: String
) : Content(ITEM_TYPE) {
    companion object {
        const val ITEM_TYPE = "compact_radio"
    }

    class TypeAdapter : JsonDeserializer<CompactRadio> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): CompactRadio {
            if (!json.isJsonObject) throw JsonParseException("Invalid CompactRadio")
            val obj = json.asJsonObject
            val title = TypeAdapterUtils.parseFormattedString(obj.getAsJsonObject("title")) ?: ""
            val videoCount = TypeAdapterUtils.parseFormattedString(obj.getAsJsonObject("video_count_short_text")) ?: ""
            val thumbnail = obj.getAsJsonObject("thumbnail_info")?.get("url")?.asString ?: ""
            val playlistId = obj.get("playlist_id")?.asString ?: ""
            val endpoint = obj.getAsJsonObject("navigation_endpoint")?.get("url")?.asString ?: ""
            return CompactRadio(title, videoCount, thumbnail, playlistId, endpoint)
        }
    }
}

class CompactChannel(
    val title: String,
    val thumbnailUrl: String,
    val videoCountText: String,
    val subscriberCount: String?,
    val endpoint: String
) : Content(ITEM_TYPE) {
    companion object {
        const val ITEM_TYPE = "compact_channel"
    }

    class TypeAdapter : JsonDeserializer<CompactChannel> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): CompactChannel {
            if (!json.isJsonObject) throw JsonParseException("Invalid CompactChannel")
            val obj = json.asJsonObject
            val title = TypeAdapterUtils.parseFormattedString(obj.get("title")) ?: ""
            val thumbnail = obj.getAsJsonObject("thumbnail_info")?.get("url")?.asString ?: ""
            val videoCount = TypeAdapterUtils.parseFormattedString(obj.get("video_count")) ?: ""
            val subscriberCount = TypeAdapterUtils.parseFormattedString(obj.get("subscriber_count"))
            val endpoint = obj.getAsJsonObject("endpoint")?.get("url")?.asString ?: ""
            return CompactChannel(title, thumbnail, videoCount, subscriberCount, endpoint)
        }
    }
}

class ItemSection(
    contents: List<Content>,
    override val continuationToken: String?,
    override val clickTrackingParams: String?
) : MultipleItemContent(ITEM_TYPE, contents), Continuation {
    companion object {
        const val ITEM_TYPE = "item_section"
    }

    class TypeAdapter : JsonDeserializer<ItemSection> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ItemSection {
            if (!json.isJsonObject) throw JsonParseException("Invalid ItemSection")
            val obj = json.asJsonObject
            val contents = TypeAdapterUtils.parse(obj.getAsJsonArray("contents"), context)
            var token: String? = null
            var clickParams: String? = null
            val continuations = obj.getAsJsonArray("continuations")
            if (continuations != null) {
                for (elem in continuations) {
                    val cObj = elem.asJsonObject
                    if (TypeAdapterUtils.NEXT_CONTINUATION_DATA_TYPE.equals(
                            TypeAdapterUtils.safeGet(cObj, TypeAdapterUtils.KEY_ITEM_TYPE, ""), ignoreCase = true
                        )
                    ) {
                        token = cObj.get("continuation")?.asString
                        clickParams = cObj.get("click_tracking_params")?.asString
                        break
                    }
                }
            }
            return ItemSection(contents, token, clickParams)
        }
    }
}

class SectionList(
    items: List<Content>,
    override val continuationToken: String?,
    override val clickTrackingParams: String?
) : MultipleItemContent(ITEM_TYPE, items), Continuation {
    companion object {
        const val ITEM_TYPE = "section_list"
    }

    class TypeAdapter : JsonDeserializer<SectionList> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): SectionList {
            if (!json.isJsonObject) throw JsonParseException("Invalid SectionList")
            val obj = json.asJsonObject
            val contents = TypeAdapterUtils.parse(obj.getAsJsonArray("contents"), context)
            var token: String? = null
            var clickParams: String? = null
            val continuations = obj.getAsJsonArray("continuations")
            if (continuations != null) {
                for (elem in continuations) {
                    val cObj = elem.asJsonObject
                    if (TypeAdapterUtils.NEXT_CONTINUATION_DATA_TYPE.equals(
                            TypeAdapterUtils.safeGet(cObj, TypeAdapterUtils.KEY_ITEM_TYPE, ""), ignoreCase = true
                        )
                    ) {
                        token = cObj.get("continuation")?.asString
                        clickParams = cObj.get("click_tracking_params")?.asString
                        break
                    }
                }
            }
            return SectionList(contents, token, clickParams)
        }
    }
}

class Shelf(
    subContent: Content?,
    val thumbnailUrl: String?,
    val title: String?,
    val subtitle: String?,
    val titleAnnotation: String?,
    val endpoint: String?
) : NestedContent(ITEM_TYPE, subContent) {
    companion object {
        const val ITEM_TYPE = "shelf"
    }

    class TypeAdapter : JsonDeserializer<Shelf> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Shelf {
            if (!json.isJsonObject) throw JsonParseException("Invalid Shelf")
            val obj = json.asJsonObject
            val title = TypeAdapterUtils.parseFormattedString(obj.getAsJsonObject("title"))
            val subtitle = TypeAdapterUtils.parseFormattedString(obj.getAsJsonObject("subtitle"))
            val titleAnnotation = TypeAdapterUtils.parseFormattedString(obj.getAsJsonObject("title_annotation"))
            val endpoint = TypeAdapterUtils.safeGetNullable(obj.getAsJsonObject("endpoint"), "url", null)
            val thumbnail = TypeAdapterUtils.safeGetNullable(obj.getAsJsonObject("thumbnail"), "url", null)
            val subContent = TypeAdapterUtils.parse(obj.getAsJsonObject("content"), context)
            return Shelf(subContent, thumbnail, title, subtitle, titleAnnotation, endpoint)
        }
    }
}

class VerticalList(items: List<Content>) : MultipleItemContent(ITEM_TYPE, items) {
    companion object {
        const val ITEM_TYPE = "vertical_list"
    }

    class TypeAdapter : JsonDeserializer<VerticalList> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): VerticalList {
            if (!json.isJsonObject) throw JsonParseException("Invalid VerticalList")
            val obj = json.asJsonObject
            val contents = TypeAdapterUtils.parse(obj.getAsJsonArray("items"), context)
            return VerticalList(contents)
        }
    }
}

class Feed(
    val feedName: String,
    val content: Content?
) {
    class TypeAdapter : JsonDeserializer<Feed> {
        companion object {
            private const val SINGLE_COLUMN_BROWSE_RESULTS = "single_column_browse_results"
            private const val CONTINUATION_CONTENTS = "continuation_contents"
        }

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Feed {
            if (!json.isJsonObject) throw JsonParseException("Invalid Feed structure")
            val obj = json.asJsonObject
            val feedName = TypeAdapterUtils.safeGet(obj, "feed_name", TypeAdapterUtils.UNKNOWN_NAME)
            val content: Content? = when {
                obj.has(SINGLE_COLUMN_BROWSE_RESULTS) -> {
                    val singleCol = obj.getAsJsonObject(SINGLE_COLUMN_BROWSE_RESULTS)
                    val tabs = singleCol.getAsJsonArray("tabs")
                    var selectedContent: Content? = null
                    if (tabs != null) {
                        for (tab in tabs) {
                            val tabObj = tab.asJsonObject
                            if (TypeAdapterUtils.safeGet(tabObj, "selected", false)) {
                                selectedContent = TypeAdapterUtils.parse(tabObj.getAsJsonObject("content"), context)
                                break
                            }
                        }
                    }
                    selectedContent ?: throw JsonParseException("No tab is selected")
                }
                obj.has(CONTINUATION_CONTENTS) -> {
                    TypeAdapterUtils.parse(obj.getAsJsonObject(CONTINUATION_CONTENTS), context)
                }
                else -> throw JsonParseException("Unknown feed structure: $feedName")
            }
            return Feed(feedName, content)
        }
    }
}

class FeedResponse(
    result: String,
    timestamp: Long,
    val feed: Feed?
) : AbstractResponse(result, timestamp) {
    override val hasContinuation: Boolean
        get() = feed?.content is Continuation && (feed.content as Continuation).continuationToken != null

    override val continuation: Continuation?
        get() = if (hasContinuation) feed?.content as Continuation else null

    class TypeAdapter : JsonDeserializer<FeedResponse> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): FeedResponse {
            if (!json.isJsonObject) throw JsonParseException("Invalid feed response")
            val obj = json.asJsonObject
            val result = TypeAdapterUtils.safeGet(obj, "result", "failed")
            val timestamp = TypeAdapterUtils.safeGet(obj, "timestamp", 0).toLong()
            var feed: Feed? = null
            if (RESULT_OK.equals(result, ignoreCase = true)) {
                val content = obj.get("content")
                if (content is JsonObject && content.size() > 0) {
                    feed = context.deserialize(content, Feed::class.java)
                }
            }
            return FeedResponse(result, timestamp, feed)
        }
    }
}

class SearchResult(
    val content: Content?,
    val searchType: String?
) {
    class TypeAdapter : JsonDeserializer<SearchResult> {
        companion object {
            private const val CONTINUATION_CONTENTS = "continuation_contents"
            private const val SEARCH_RESULTS = "search_results"
        }

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): SearchResult {
            if (!json.isJsonObject) throw JsonParseException("Invalid SearchResult structure")
            val obj = json.asJsonObject
            val searchType = TypeAdapterUtils.safeGetNullable(obj, "search_type", null)
            val content = when {
                obj.has(SEARCH_RESULTS) -> TypeAdapterUtils.parse(obj.getAsJsonObject(SEARCH_RESULTS), context)
                obj.has(CONTINUATION_CONTENTS) -> TypeAdapterUtils.parse(obj.getAsJsonObject(CONTINUATION_CONTENTS), context)
                else -> null
            }
            return SearchResult(content, searchType)
        }
    }
}

class SearchResponse(
    result: String,
    timestamp: Long,
    val searchResult: SearchResult?
) : AbstractResponse(result, timestamp) {
    override val hasContinuation: Boolean
        get() = searchResult?.content is Continuation && (searchResult.content as Continuation).continuationToken != null

    override val continuation: Continuation?
        get() = if (hasContinuation) searchResult?.content as Continuation else null

    class TypeAdapter : JsonDeserializer<SearchResponse> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): SearchResponse {
            if (!json.isJsonObject) throw JsonParseException("Invalid SearchResponse")
            val obj = json.asJsonObject
            val result = TypeAdapterUtils.safeGet(obj, "result", "failed")
            val timestamp = TypeAdapterUtils.safeGet(obj, "timestamp", 0).toLong()
            var sr: SearchResult? = null
            if (RESULT_OK.equals(result, ignoreCase = true)) {
                val content = obj.get("content")
                if (content is JsonObject && content.size() > 0) {
                    sr = context.deserialize(content, SearchResult::class.java)
                }
            }
            return SearchResponse(result, timestamp, sr)
        }
    }
}

class Channel(
    val header: Header?,
    val content: Content
) {
    class Header(
        val title: String,
        val avatarUrl: String,
        val channelEndpoint: String,
        val bannerUrl: String,
        val hdBannerUrl: String
    ) {
        constructor(jsonElement: JsonElement) : this(
            title = jsonElement.asJsonObject.get("title")?.asString ?: "",
            avatarUrl = jsonElement.asJsonObject.getAsJsonObject("avatar")?.get("url")?.asString ?: "",
            channelEndpoint = jsonElement.asJsonObject.get("channel_url")?.asString ?: "",
            bannerUrl = jsonElement.asJsonObject.getAsJsonObject("banner_image")?.get("url")?.asString ?: "",
            hdBannerUrl = jsonElement.asJsonObject.getAsJsonObject("banner_image_hd")?.get("url")?.asString ?: ""
        )
    }

    class TypeAdapter : JsonDeserializer<Channel> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Channel {
            if (!json.isJsonObject) throw JsonParseException("Invalid channel")
            val obj = json.asJsonObject
            val header = if (obj.has("header")) Header(obj.get("header")) else null
            var content: Content? = null
            val tabSettings = obj.getAsJsonObject("tab_settings")
            if (tabSettings != null) {
                val tabs = tabSettings.getAsJsonArray("available_tabs")
                if (tabs != null) {
                    for (tab in tabs) {
                        val tabObj = tab.asJsonObject
                        if (TypeAdapterUtils.safeGet(tabObj, "selected", false)) {
                            content = TypeAdapterUtils.parse(tabObj.getAsJsonObject("content"), context)
                            break
                        }
                    }
                }
            } else {
                val cont = obj.getAsJsonObject("continuation_contents")
                content = TypeAdapterUtils.parse(cont, context)
            }
            return Channel(header, content ?: throw JsonParseException("Channel content == null"))
        }
    }
}

class ChannelResponse(
    result: String,
    timestamp: Long,
    val channel: Channel?
) : AbstractResponse(result, timestamp) {
    override val hasContinuation: Boolean
        get() = channel?.content is Continuation && (channel.content as Continuation).continuationToken != null

    override val continuation: Continuation?
        get() = if (hasContinuation) channel?.content as Continuation else null

    class TypeAdapter : JsonDeserializer<ChannelResponse> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ChannelResponse {
            if (!json.isJsonObject) throw JsonParseException("Invalid ChannelResponse")
            val obj = json.asJsonObject
            val result = TypeAdapterUtils.safeGet(obj, "result", "failed")
            val timestamp = TypeAdapterUtils.safeGet(obj, "timestamp", 0).toLong()
            var channel: Channel? = null
            if (RESULT_OK.equals(result, ignoreCase = true)) {
                val content = obj.get("content")
                if (content is JsonObject && content.size() > 0) {
                    channel = context.deserialize(content, Channel::class.java)
                }
            }
            return ChannelResponse(result, timestamp, channel)
        }
    }
}

class ArtistWatchCard(
    title: String,
    val subtitle: String?,
    val thumbnailUrl: String?,
    val callToAction: String?,
    val callToActionEndpoint: String?,
    val relatedDataTitle: String?,
    val relatedData: List<Artist>,
    val channelEndpoint: String?,
    lists: List<Content>
) : MultipleItemContent(ITEM_TYPE, lists) {
    companion object {
        const val ITEM_TYPE = "artist_watch_card"
    }

    class Artist(
        val title: String,
        val thumbnailUrl: String,
        val endpoint: String
    ) {
        constructor(jsonElement: JsonElement) : this(
            title = TypeAdapterUtils.parseFormattedString(jsonElement.asJsonObject.get("title")) ?: "",
            thumbnailUrl = jsonElement.asJsonObject.getAsJsonObject("thumbnail")?.get("url")?.asString ?: "",
            endpoint = jsonElement.asJsonObject.getAsJsonObject("navigation_endpoint")?.get("url")?.asString ?: ""
        )
    }

    class TypeAdapter : JsonDeserializer<ArtistWatchCard> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ArtistWatchCard {
            if (!json.isJsonObject) throw JsonParseException("Invalid ArtistWatchCard")
            val obj = json.asJsonObject
            val title = TypeAdapterUtils.parseFormattedString(obj.get("title")) ?: ""
            val subtitle = TypeAdapterUtils.parseFormattedString(obj.get("collapsed_label"))
            val ctaObj = obj.getAsJsonObject("call_to_action")
            val ctaLabel = TypeAdapterUtils.parseFormattedString(ctaObj?.get("label"))
            val ctaEndpoint = ctaObj?.getAsJsonObject("navigation_endpoint")?.get("url")?.asString
            val thumbnail = TypeAdapterUtils.safeGetNullable(ctaObj?.getAsJsonObject("left_thumbnail"), "url", null)
            val relatedDataObj = obj.getAsJsonObject("related_data")
            val relatedDataTitle = TypeAdapterUtils.parseFormattedString(relatedDataObj?.get("title"))
            val relatedData = TypeAdapterUtils.parse(relatedDataObj?.getAsJsonArray("entities")) { Artist(it) }
            val lists = TypeAdapterUtils.parse(obj.getAsJsonArray("lists"), context)
            val channelEndpoint = obj.getAsJsonObject("navigation_endpoint")?.get("url")?.asString
            return ArtistWatchCard(
                title, subtitle, thumbnail, ctaLabel, ctaEndpoint, relatedDataTitle, relatedData, channelEndpoint, lists
            )
        }
    }
}

class WatchCardAlbumList(
    val title: String?,
    val albumList: List<Album>
) : Content(ITEM_TYPE) {
    companion object {
        const val ITEM_TYPE = "watch_card_album_list"
    }

    class Album(
        val title: String,
        val year: String?,
        val thumbnailUrl: String,
        val endpoint: String
    ) {
        constructor(jsonElement: JsonElement) : this(
            title = TypeAdapterUtils.parseFormattedString(jsonElement.asJsonObject.get("title")) ?: "",
            year = TypeAdapterUtils.parseFormattedString(jsonElement.asJsonObject.get("year")),
            thumbnailUrl = jsonElement.asJsonObject.getAsJsonObject("thumbnail")?.get("url")?.asString ?: "",
            endpoint = jsonElement.asJsonObject.getAsJsonObject("navigation_endpoint")?.get("url")?.asString ?: ""
        )
    }

    class TypeAdapter : JsonDeserializer<WatchCardAlbumList> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): WatchCardAlbumList {
            if (!json.isJsonObject) throw JsonParseException("Invalid WatchCardAlbumList")
            val obj = json.asJsonObject
            val title = TypeAdapterUtils.parseFormattedString(obj.get("title"))
            val albums = TypeAdapterUtils.parse(obj.getAsJsonArray("albums")) { Album(it) }
            return WatchCardAlbumList(title, albums)
        }
    }
}

class WatchCardVideoList(
    val title: String?,
    val endpoint: String?,
    val videoList: List<Video>
) : Content(ITEM_TYPE) {
    companion object {
        const val ITEM_TYPE = "watch_card_video_list"
    }

    class Video(
        val title: String,
        val durationText: String?,
        val thumbnailUrl: String,
        val endpoint: String
    ) {
        constructor(jsonElement: JsonElement) : this(
            title = TypeAdapterUtils.parseFormattedString(jsonElement.asJsonObject.get("title")) ?: "",
            durationText = TypeAdapterUtils.parseFormattedString(jsonElement.asJsonObject.get("duration")),
            thumbnailUrl = jsonElement.asJsonObject.getAsJsonObject("thumbnail")?.get("url")?.asString ?: "",
            endpoint = jsonElement.asJsonObject.getAsJsonObject("navigation_endpoint")?.get("url")?.asString ?: ""
        )
    }

    class TypeAdapter : JsonDeserializer<WatchCardVideoList> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): WatchCardVideoList {
            if (!json.isJsonObject) throw JsonParseException("Invalid WatchCardVideoList")
            val obj = json.asJsonObject
            val title = TypeAdapterUtils.parseFormattedString(obj.get("title"))
            var endpoint = TypeAdapterUtils.safeGetNullable(obj.getAsJsonObject("view_all_endpoint"), "url", null)
            val videos = TypeAdapterUtils.parse(obj.getAsJsonArray("videos")) { Video(it) }
            if (endpoint == null && videos.isNotEmpty()) {
                endpoint = videos[0].endpoint
            }
            return WatchCardVideoList(title, endpoint, videos)
        }
    }
}

/**
 * Extended Model for YouTube Playlists (/playlist?list=... / browseId VL...)
 */
data class PlaylistResponse(
    val playlistId: String,
    val title: String,
    val owner: String,
    val description: String = "",
    val thumbnailUrl: String = "",
    val videoCountText: String = "",
    val videos: List<CompactVideo> = emptyList(),
    val continuationToken: String? = null
)

/**
 * Extended Model for Unified Watch Screen (video + playlist queue on the same page!)
 */
data class WatchResponse(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val channelAvatarUrl: String = "",
    val viewCountText: String = "",
    val publishDateText: String = "",
    val likesText: String = "",
    val description: String = "",
    val playlistId: String? = null,
    val playlistTitle: String? = null,
    val playlistQueue: List<CompactVideo> = emptyList(),
    val upNextVideos: List<CompactVideo> = emptyList(),
    val extractorResult: ExtractorResult? = null
)

object TypeAdapterUtils {
    const val UNKNOWN_NAME: String = "Unknown"
    const val KEY_ITEM_TYPE: String = "item_type"
    const val NEXT_CONTINUATION_DATA_TYPE: String = "next_continuation_data"
    private const val TYPE_FORMATTED_STRING: String = "formatted_string"

    private val ITEM_TYPE_MAP: Map<String, Class<out Content>> = mapOf(
        ArtistWatchCard.ITEM_TYPE to ArtistWatchCard::class.java,
        CompactChannel.ITEM_TYPE to CompactChannel::class.java,
        CompactPlaylist.ITEM_TYPE to CompactPlaylist::class.java,
        CompactRadio.ITEM_TYPE to CompactRadio::class.java,
        CompactVideo.ITEM_TYPE to CompactVideo::class.java,
        ItemSection.ITEM_TYPE to ItemSection::class.java,
        SectionList.ITEM_TYPE to SectionList::class.java,
        Shelf.ITEM_TYPE to Shelf::class.java,
        VerticalList.ITEM_TYPE to VerticalList::class.java,
        VideoWithContext.ITEM_TYPE to VideoWithContext::class.java,
        WatchCardAlbumList.ITEM_TYPE to WatchCardAlbumList::class.java,
        WatchCardVideoList.ITEM_TYPE to WatchCardVideoList::class.java
    )

    private val BLACK_LIST_ITEM_TYPE: Set<String> = setOf("promoted_video")

    @JvmStatic
    fun parse(jsonObj: JsonObject?, context: JsonDeserializationContext): Content? {
        if (jsonObj == null) return null
        val itemType = safeGet(jsonObj, KEY_ITEM_TYPE, "")
        if (BLACK_LIST_ITEM_TYPE.contains(itemType)) return null
        val contentClass = ITEM_TYPE_MAP[itemType] ?: return Content(itemType)
        return context.deserialize(jsonObj, contentClass)
    }

    @JvmStatic
    fun parse(contentsJsonArr: JsonArray?, context: JsonDeserializationContext): List<Content> {
        if (contentsJsonArr == null) return emptyList()
        val contents = mutableListOf<Content>()
        for (element in contentsJsonArr) {
            if (element.isJsonObject) {
                val sectionItem = parse(element.asJsonObject, context)
                if (sectionItem != null) contents.add(sectionItem)
            }
        }
        return contents
    }

    @JvmStatic
    fun <T> parse(elements: JsonArray?, creator: (JsonElement) -> T): List<T> {
        if (elements == null) return emptyList()
        val result = mutableListOf<T>()
        for (element in elements) {
            result.add(creator(element))
        }
        return result
    }

    @JvmStatic
    fun parseFormattedString(content: JsonElement?): String? {
        if (content is JsonObject) {
            val itemType = safeGet(content, KEY_ITEM_TYPE, "")
            if (itemType.equals(TYPE_FORMATTED_STRING, ignoreCase = true)) {
                val runs = content.getAsJsonArray("runs")
                if (runs != null && runs.size() > 0) {
                    return safeGetNullable(runs.get(0).asJsonObject, "text", null)
                }
            }
            if (content.has("simpleText")) {
                return content.get("simpleText")?.asString
            }
            if (content.has("runs")) {
                val runs = content.getAsJsonArray("runs")
                val sb = StringBuilder()
                for (r in runs) {
                    if (r.isJsonObject && r.asJsonObject.has("text")) {
                        sb.append(r.asJsonObject.get("text").asString)
                    }
                }
                return sb.toString()
            }
        }
        return null
    }

    @JvmStatic
    fun safeGet(jsonObject: JsonObject?, key: String, defaultValue: String): String {
        if (jsonObject == null) return defaultValue
        val element = jsonObject.get(key)
        return if (element != null && element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            element.asString
        } else {
            defaultValue
        }
    }

    @JvmStatic
    fun safeGetNullable(jsonObject: JsonObject?, key: String, defaultValue: String? = null): String? {
        if (jsonObject == null) return defaultValue
        val element = jsonObject.get(key)
        return if (element != null && element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            element.asString
        } else {
            defaultValue
        }
    }

    @JvmStatic
    fun safeGet(jsonObject: JsonObject?, key: String, defaultValue: Number): Number {
        if (jsonObject == null) return defaultValue
        val element = jsonObject.get(key)
        return if (element != null && element.isJsonPrimitive && element.asJsonPrimitive.isNumber) {
            element.asNumber
        } else {
            defaultValue
        }
    }

    @JvmStatic
    fun safeGet(jsonObject: JsonObject?, key: String, defaultValue: Boolean): Boolean {
        if (jsonObject == null) return defaultValue
        val element = jsonObject.get(key)
        return if (element != null && element.isJsonPrimitive && element.asJsonPrimitive.isBoolean) {
            element.asBoolean
        } else {
            defaultValue
        }
    }
}
