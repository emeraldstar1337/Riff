package dev.lad.data

import android.content.Context
import dev.lad.domain.albumKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TrackAppearance(context: Context) {
    private val preferences = context.getSharedPreferences("track_appearance", Context.MODE_PRIVATE)
    private val revision = MutableStateFlow(0)
    val changes = revision.asStateFlow()

    fun albumAccent(track: Track): Int? {
        val key = "accent:${track.albumKey()}"
        return if (preferences.contains(key)) preferences.getInt(key, 0) else null
    }

    fun setAlbumAccent(track: Track, color: Int?) {
        preferences
            .edit()
            .apply {
                val key = "accent:${track.albumKey()}"
                if (color == null) remove(key) else putInt(key, color or 0xff000000.toInt())
            }
            .apply()
        revision.update { it + 1 }
    }

    fun lyricsOffset(id: Long): Long = preferences.getLong("offset:$id", 0).coerceIn(-30000, 30000)

    fun setLyricsOffset(id: Long, offset: Long) {
        preferences.edit().putLong("offset:$id", offset.coerceIn(-30000, 30000)).apply()
        revision.update { it + 1 }
    }
}
