package com.mobplayer.tv.data.models

enum class CardType {
    POSTER,
    LANDSCAPE,
    CONTINUE_WATCHING,
    LIVE
}

data class MediaItemModel(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val description: String,
    val posterUrl: String,
    val backdropUrl: String,
    val genres: List<String>,
    val year: String,
    val duration: String,
    val rating: String,
    val contentRating: String = "16+",
    val badge: String? = null,
    val progress: Float? = null,
    val remainingTime: String? = null,
    val videoUrl: String,
    val gradientColors: List<Long> = listOf(0xFF1E293B, 0xFF0F172A),
    val isLive: Boolean = false,
    val channelNumber: String? = null,
    val rankNumber: Int? = null
)

data class MediaRailModel(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val cardType: CardType,
    val items: List<MediaItemModel>
)
