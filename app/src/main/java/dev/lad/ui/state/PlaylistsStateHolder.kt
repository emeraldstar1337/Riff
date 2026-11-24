package dev.lad.ui.state

import dev.lad.data.MusicRepository
import dev.lad.data.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistsStateHolder(private val music: MusicRepository, private val scope: CoroutineScope) {
    val playlists: StateFlow<List<Playlist>> =
        music.dao.playlists().stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun savePlaylist(playlist: Playlist) {
        scope.launch { music.dao.savePlaylist(playlist) }
    }

    fun deletePlaylist(id: Long) {
        scope.launch { music.dao.deletePlaylist(id) }
    }

    fun addToPlaylist(id: Long, ids: List<Long>) {
        scope.launch { music.addToPlaylist(id, ids) }
    }
}
