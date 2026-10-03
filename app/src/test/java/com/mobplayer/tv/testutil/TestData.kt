package com.mobplayer.tv.testutil

import com.mobplayer.tv.data.models.MediaItemModel

fun youTubeItem(videoId: String, title: String = "Video $videoId") = MediaItemModel(
    id = "yt_TEST_$videoId",
    title = title,
    description = title,
    posterUrl = "",
    backdropUrl = "",
    genres = listOf("YouTube"),
    year = "",
    duration = "",
    rating = "",
    videoUrl = "",
    youtubeVideoId = videoId
)

fun youTubeItems(count: Int, prefix: String = "v") = (1..count).map { youTubeItem("$prefix$it") }
