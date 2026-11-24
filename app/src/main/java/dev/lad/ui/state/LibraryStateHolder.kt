package dev.lad.ui.state

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dev.lad.data.MediaTools
import dev.lad.data.MusicRepository
import dev.lad.data.SettingsStore
import dev.lad.data.Track
import dev.lad.data.TrackEdit
import dev.lad.domain.FolderRules
import dev.lad.util.attempt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryStateHolder(
    private val context: Context,
    private val music: MusicRepository,
    private val settingsStore: SettingsStore,
    val mediaTools: MediaTools,
    private val scope: CoroutineScope,
) {
    val tracks: StateFlow<List<Track>> =
        combine(
                music.dao.tracks(),
                settingsStore.settings.distinctUntilChanged { old, next ->
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
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hidden: StateFlow<List<TrackEdit>> =
        music.dao.hiddenEdits().stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders = MutableStateFlow<List<String>>(emptyList())
    val scanning = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    private var scanJob: Job? = null
    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scheduleScan(forceArtwork = false, debounceMs = 800)
            }
        }

    init {
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED

    fun refresh(forceArtwork: Boolean = true) = scheduleScan(forceArtwork)

    fun scheduleScan(forceArtwork: Boolean, debounceMs: Long = 0) {
        scanJob?.cancel()
        scanning.value = false
        scanJob =
            scope.launch {
                if (debounceMs > 0) delay(debounceMs)
                if (!hasPermission()) return@launch
                scanning.value = true
                try {
                    music.scan(settingsStore.settings.first(), forceArtwork) {
                        settingsStore.settings.first()
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
        if (hasPermission()) {
            scope.launch { attempt { music.folders() }.onSuccess { folders.value = it } }
        }
    }

    fun favorite(track: Track) {
        scope.launch { music.toggleFavorite(track) }
    }

    fun hide(track: Track) {
        scope.launch {
            music.edit(
                track,
                TrackEdit(
                    track.id,
                    track.title,
                    track.artist,
                    track.album,
                    track.year,
                    track.genre,
                    track.customArtwork,
                    hidden = true,
                ),
            )
        }
    }

    fun unhide(id: Long) {
        scope.launch {
            val edit = music.dao.edit(id) ?: return@launch
            music.dao.saveEdit(edit.copy(hidden = false))
            refresh(false)
        }
    }

    suspend fun saveEdit(track: Track, edit: TrackEdit) {
        music.edit(track, edit)
    }

    suspend fun saveAlbumCover(albumId: Long, uri: String) {
        music.albumCover(albumId, uri)
    }

    fun dispose() {
        context.contentResolver.unregisterContentObserver(observer)
    }
}
