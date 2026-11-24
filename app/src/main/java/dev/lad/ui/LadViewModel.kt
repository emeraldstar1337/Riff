package dev.lad.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.lad.LadApplication
import dev.lad.data.Playlist
import dev.lad.data.Settings
import dev.lad.data.Track
import dev.lad.data.TrackEdit
import dev.lad.ui.state.LibraryStateHolder
import dev.lad.ui.state.PlayerStateHolder
import dev.lad.ui.state.PlaylistsStateHolder
import dev.lad.ui.state.SettingsStateHolder
import dev.lad.ui.state.StatisticsStateHolder

class LadViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LadApplication

    val libraryState =
        LibraryStateHolder(app, app.music, app.settings, app.mediaTools, viewModelScope)

    val playerState =
        PlayerStateHolder(app.playback, app.trackAppearance, app.lyrics, app.settings, viewModelScope)

    val playlistsState = PlaylistsStateHolder(app.music, viewModelScope)

    val statisticsState = StatisticsStateHolder(app.music.dao, app.playback, viewModelScope)

    val settingsState =
        SettingsStateHolder(
            app,
            app.settings,
            onLibraryRulesChanged = { libraryState.scheduleScan(forceArtwork = false, debounceMs = 100) },
            scope = viewModelScope,
        )

    val tracks
        get() = libraryState.tracks

    val playlists
        get() = playlistsState.playlists

    val history
        get() = statisticsState.history

    val runs
        get() = statisticsState.runs

    val settings
        get() = settingsState.settings

    val appearance
        get() = playerState.appearance

    val playback
        get() = playerState.playback

    val folders
        get() = libraryState.folders

    val lyrics
        get() = playerState.lyrics

    val mediaTools
        get() = libraryState.mediaTools

    val hidden
        get() = libraryState.hidden

    val selectedDetails
        get() = playerState.selectedDetails

    val audioOpen
        get() = playerState.audioOpen

    val scanning
        get() = libraryState.scanning

    val message
        get() = libraryState.message

    fun showDetails(track: Track) = playerState.showDetails(track)

    fun hide(track: Track) = libraryState.hide(track)

    fun unhide(id: Long) = libraryState.unhide(id)

    suspend fun saveEdit(track: Track, edit: TrackEdit) = libraryState.saveEdit(track, edit)

    suspend fun saveAlbumCover(albumId: Long, uri: String) =
        libraryState.saveAlbumCover(albumId, uri)

    fun hasPermission() = libraryState.hasPermission()

    fun refresh(forceArtwork: Boolean = true) = libraryState.refresh(forceArtwork)

    fun loadFolders() = libraryState.loadFolders()

    fun updateSettings(transform: (Settings) -> Settings) = settingsState.updateSettings(transform)

    fun favorite(track: Track) = libraryState.favorite(track)

    fun play(
        track: Track,
        context: List<Track>,
        name: String,
        forceNew: Boolean = false,
        shuffle: Boolean = playback.state.value.queue.shuffle,
    ) = playerState.play(track, context, name, forceNew, shuffle)

    fun savePlaylist(p: Playlist) = playlistsState.savePlaylist(p)

    fun deletePlaylist(id: Long) = playlistsState.deletePlaylist(id)

    fun addToPlaylist(id: Long, ids: List<Long>) = playlistsState.addToPlaylist(id, ids)

    fun setLanguage(language: String) = settingsState.setLanguage(language)

    fun clearStatistics() = statisticsState.clearStatistics()

    override fun onCleared() {
        libraryState.dispose()
        super.onCleared()
    }
}
