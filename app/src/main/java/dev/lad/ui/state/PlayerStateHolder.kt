package dev.lad.ui.state

import dev.lad.data.LyricsRepository
import dev.lad.data.SettingsStore
import dev.lad.data.Track
import dev.lad.data.TrackAppearance
import dev.lad.playback.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlayerStateHolder(
    val playback: PlaybackRepository,
    val appearance: TrackAppearance,
    val lyrics: LyricsRepository,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
) {
    val selectedDetails = MutableStateFlow<Track?>(null)
    val audioOpen = MutableStateFlow(false)

    init {
        scope.launch {
            if (
                playback.state.value.current != null ||
                    settingsStore.queue.first()?.entries?.isNotEmpty() == true
            ) {
                playback.connect()
            }
        }
    }

    fun showDetails(track: Track) {
        selectedDetails.value = track
    }

    fun play(
        track: Track,
        context: List<Track>,
        name: String,
        forceNew: Boolean = false,
        shuffle: Boolean = playback.state.value.queue.shuffle,
    ) {
        val state = playback.state.value
        if (!forceNew && state.current != null) {
            if (state.current.id == track.id) playback.toggle() else playback.select(track.id)
        } else {
            playback.start(context.map { it.id }, track.id, shuffle, name)
        }
    }
}
