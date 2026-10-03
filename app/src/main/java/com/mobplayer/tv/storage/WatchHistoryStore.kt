package com.mobplayer.tv.storage

import android.content.Context
import android.content.SharedPreferences
import com.mobplayer.tv.data.models.MediaItemModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube videos the user has played on this TV, most recent first. Persisted in
 * SharedPreferences so the "Watch history" row in the player survives restarts.
 */
@Singleton
class WatchHistoryStore(private val prefs: SharedPreferences) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    @Serializable
    private data class Entry(
        val videoId: String,
        val title: String,
        val subtitle: String = "",
        val posterUrl: String = "",
        val backdropUrl: String = "",
        val duration: String = "",
        val isLive: Boolean = false
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val _history = MutableStateFlow(load())
    val history: StateFlow<List<MediaItemModel>> = _history.asStateFlow()

    /** Moves [item] to the front of the history, dropping the oldest entries past [MAX_ENTRIES]. */
    @Synchronized
    fun record(item: MediaItemModel) {
        val videoId = item.youtubeVideoId ?: return
        val entry = Entry(
            videoId = videoId,
            title = item.title,
            subtitle = item.subtitle,
            posterUrl = item.posterUrl,
            backdropUrl = item.backdropUrl,
            duration = item.duration,
            isLive = item.isLive
        )
        val updated = (listOf(entry) + loadEntries().filter { it.videoId != videoId }).take(MAX_ENTRIES)
        prefs.edit().putString(KEY_HISTORY, json.encodeToString(updated)).apply()
        _history.value = updated.map(::toItem)
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
        _history.value = emptyList()
    }

    private fun load(): List<MediaItemModel> = loadEntries().map(::toItem)

    private fun loadEntries(): List<Entry> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<Entry>>(raw)
        } catch (_: Exception) {
            emptyList() // Corrupt data: start over rather than crash the player.
        }
    }

    private fun toItem(entry: Entry) = MediaItemModel(
        id = "yt_history_${entry.videoId}",
        title = entry.title,
        subtitle = entry.subtitle,
        description = entry.title,
        posterUrl = entry.posterUrl,
        backdropUrl = entry.backdropUrl,
        genres = listOf("YouTube"),
        year = "",
        duration = entry.duration,
        rating = "",
        videoUrl = "",
        isLive = entry.isLive,
        youtubeVideoId = entry.videoId
    )

    companion object {
        const val PREFS_NAME = "youtube_watch_history"
        const val KEY_HISTORY = "history"
        const val MAX_ENTRIES = 30
    }
}
