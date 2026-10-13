package dev.lad.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.lad.LadApplication
import dev.lad.data.*
import dev.lad.domain.FolderRules
import dev.lad.util.attempt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class LadViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LadApplication
    val tracks =
        combine(
                app.music.dao.tracks(),
                app.settings.settings.distinctUntilChanged { old, next ->
                    old.minimumSeconds == next.minimumSeconds &&
                        old.includedFolders == next.includedFolders &&
                        old.excludedFolders == next.excludedFolders
                },
            ) { rows, rules ->
                rows.filter {
                    it.durationMs >= rules.minimumSeconds * 1000L &&
                        FolderRules.accepts(
                            FolderRules.folder(it.path),
                            rules.includedFolders,
                            rules.excludedFolders,
                        )
                }
            }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlists =
        app.music.dao
            .playlists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history =
        app.music.dao
            .listens()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val runs =
        app.music.dao
            .runs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val playback = app.playback
    val folders = MutableStateFlow<List<String>>(emptyList())
    val lyrics = LyricsRepository(app, app.music.dao)
    val mediaTools = MediaTools(app)
    val hidden =
        app.music.dao
            .hiddenEdits()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val selectedDetails = MutableStateFlow<Track?>(null)
    val audioOpen = MutableStateFlow(false)

    fun showDetails(track: Track) {
        selectedDetails.value = track
    }

    fun hide(track: Track) {
        viewModelScope.launch {
            app.music.edit(
                track,
                TrackEdit(
                    track.id,
                    track.title,
                    track.artist,
                    track.album,
                    track.year,
                    track.genre,
                    track.customArtwork,
                    true,
                ),
            )
        }
    }

    fun unhide(id: Long) {
        viewModelScope.launch {
            val edit = app.music.dao.edits().firstOrNull { it.trackId == id } ?: return@launch
            app.music.dao.saveEdit(edit.copy(hidden = false))
            refresh(false)
        }
    }

    suspend fun saveEdit(track: Track, edit: TrackEdit) {
        app.music.edit(track, edit)
    }

    suspend fun saveAlbumCover(albumId: Long, uri: String) {
        app.music.albumCover(albumId, uri)
    }

    val scanning = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    private var scanJob: Job? = null
    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scheduleScan(false, 800)
            }
        }

    init {

        viewModelScope.launch {
            if (
                playback.state.value.current != null ||
                    app.settings.queue.first()?.entries?.isNotEmpty() == true
            )
                playback.connect()
        }
        app.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
    }

    fun hasPermission() =
        ContextCompat.checkSelfPermission(
            app,
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED

    fun refresh(forceArtwork: Boolean = true) = scheduleScan(forceArtwork)

    private fun scheduleScan(forceArtwork: Boolean, debounceMs: Long = 0) {
        scanJob?.cancel()
        scanning.value = false
        scanJob = viewModelScope.launch {
            delay(debounceMs)
            if (!hasPermission()) return@launch
            scanning.value = true
            try {
                app.music.scan(app.settings.settings.first(), forceArtwork) {
                    app.settings.settings.first()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message.value = error.message
            } finally {
                if (scanJob === coroutineContext[Job]) scanning.value = false
            }
        }
    }

    fun loadFolders() {
        if (hasPermission())
            viewModelScope.launch {
                attempt { app.music.folders() }.onSuccess { folders.value = it }
            }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            val previous = app.settings.settings.first()
            val next = app.settings.update(transform)
            if (
                next.includedFolders != previous.includedFolders ||
                    next.excludedFolders != previous.excludedFolders ||
                    next.minimumSeconds != previous.minimumSeconds
            )
                scheduleScan(false, 100)
        }
    }

    fun favorite(track: Track) {
        viewModelScope.launch { app.music.toggleFavorite(track) }
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
        } else playback.start(context.map { it.id }, track.id, shuffle, name)
    }

    fun savePlaylist(p: Playlist) {
        viewModelScope.launch { app.music.dao.savePlaylist(p) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { app.music.dao.deletePlaylist(id) }
    }

    fun addToPlaylist(id: Long, ids: List<Long>) {
        viewModelScope.launch { app.music.addToPlaylist(id, ids) }
    }

    fun setLanguage(language: String) = dev.lad.i18n.AppLanguage.set(app, language)

    fun clearStatistics() {
        playback.command("clearStats")
    }

    override fun onCleared() {
        app.contentResolver.unregisterContentObserver(observer)
        super.onCleared()
    }
}
