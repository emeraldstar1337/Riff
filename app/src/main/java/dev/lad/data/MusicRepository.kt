package dev.lad.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.room.withTransaction
import dev.lad.R
import dev.lad.domain.FolderRules
import dev.lad.i18n.AppLanguage
import dev.lad.i18n.TextResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context, private val db: LadDatabase) {
    private val strings
        get() = TextResources(AppLanguage.context(context))

    private val scanMutex = Mutex()
    val dao = db.music()

    suspend fun scan(
        requested: Settings,
        refreshArtwork: Boolean = false,
        currentSettings: suspend () -> Settings = { requested },
    ): Int = scanMutex.withLock {
        withContext(Dispatchers.IO) {
            val settings = currentSettings()
            val old = dao.allTracks().associateBy { it.id }
            val revision = System.currentTimeMillis()
            val favorites = dao.allTracks().filter { it.favorite }.map { it.id }.toHashSet()
            val columns =
                mutableListOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.TRACK,
                    MediaStore.Audio.Media.YEAR,
                    MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.DATE_MODIFIED,
                )
            if (Build.VERSION.SDK_INT >= 29) {
                columns.add(MediaStore.Audio.Media.RELATIVE_PATH)
                columns.add(MediaStore.MediaColumns.VOLUME_NAME)
            } else columns.add(MediaStore.Audio.Media.DATA)
            if (Build.VERSION.SDK_INT >= 30) columns.add(MediaStore.Audio.Media.GENRE)
            val tracks = mutableListOf<Track>()
            context.contentResolver
                .query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    columns.toTypedArray(),
                    "${MediaStore.Audio.Media.DURATION} >= ?",
                    arrayOf((settings.minimumSeconds * 1000L).toString()),
                    null,
                )
                ?.use { c ->
                    fun string(name: String) =
                        c.getColumnIndex(name).let {
                            if (it < 0 || c.isNull(it)) "" else c.getString(it) ?: ""
                        }
                    fun number(name: String) =
                        c.getColumnIndex(name).let {
                            if (it < 0 || c.isNull(it)) 0L else c.getLong(it)
                        }
                    while (c.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val id = number(MediaStore.Audio.Media._ID)
                        val path =
                            string(
                                if (Build.VERSION.SDK_INT >= 29)
                                    MediaStore.Audio.Media.RELATIVE_PATH
                                else MediaStore.Audio.Media.DATA
                            )
                        val folder =
                            if (Build.VERSION.SDK_INT >= 29)
                                FolderRules.location(
                                    path,
                                    string(MediaStore.MediaColumns.VOLUME_NAME),
                                )
                            else FolderRules.normalize(path.substringBeforeLast('/'))
                        if (
                            !FolderRules.accepts(
                                folder,
                                settings.includedFolders,
                                settings.excludedFolders,
                            )
                        )
                            continue
                        val title =
                            string(MediaStore.Audio.Media.TITLE).ifBlank {
                                strings[R.string.untitled]
                            }
                        val artist =
                            string(MediaStore.Audio.Media.ARTIST).takeUnless {
                                it.isBlank() || it == "<unknown>"
                            } ?: strings[R.string.unknown_artist]
                        tracks +=
                            Track(
                                id,
                                ContentUris.withAppendedId(
                                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                        id,
                                    )
                                    .toString(),
                                title,
                                artist,
                                string(MediaStore.Audio.Media.ALBUM).ifBlank {
                                    strings[R.string.unknown_album]
                                },
                                number(MediaStore.Audio.Media.ALBUM_ID),
                                number(MediaStore.Audio.Media.DURATION),
                                number(MediaStore.Audio.Media.TRACK).toInt() % 1000,
                                number(MediaStore.Audio.Media.YEAR).toInt(),
                                if (Build.VERSION.SDK_INT >= 30)
                                    string(MediaStore.Audio.Media.GENRE)
                                else "",
                                folder,
                                number(MediaStore.Audio.Media.DATE_ADDED) * 1000,
                                id in favorites,
                                fileSize = number(MediaStore.Audio.Media.SIZE),
                                mimeType = string(MediaStore.Audio.Media.MIME_TYPE),
                                modifiedAt = number(MediaStore.Audio.Media.DATE_MODIFIED) * 1000,
                                artworkRevision =
                                    if (refreshArtwork) revision else old[id]?.artworkRevision ?: 0,
                            )
                    }
                } ?: error(strings[R.string.mediastore_is_unavailable_check_permission_to])
            val latestRules = currentSettings()
            if (
                latestRules.includedFolders != settings.includedFolders ||
                    latestRules.excludedFolders != settings.excludedFolders ||
                    latestRules.minimumSeconds != settings.minimumSeconds
            )
                return@withContext 0
            db.withTransaction {
                val latestFavorites = dao.allTracks().filter { it.favorite }.map { it.id }.toSet()
                val edits = dao.edits().associateBy { it.trackId }
                val covers = dao.albumCovers().associateBy { it.albumId }
                val present = tracks.map { it.id }.toSet()
                val missing = dao.allTracks().filter { it.available && it.id !in present }
                if (missing.isNotEmpty())
                    dao.upsertTracks(missing.map { it.copy(available = false) })
                tracks.chunked(500).forEach { batch ->
                    currentCoroutineContext().ensureActive()
                    dao.upsertTracks(
                        batch
                            .map { original ->
                                val edit = edits[original.id]
                                original.copy(
                                    available = edit?.hidden != true,
                                    favorite = original.id in latestFavorites,
                                    title = edit?.title ?: original.title,
                                    artist = edit?.artist ?: original.artist,
                                    album = edit?.album ?: original.album,
                                    year = edit?.year ?: original.year,
                                    genre = edit?.genre ?: original.genre,
                                    customArtwork = edit?.cover ?: covers[original.albumId]?.uri,
                                )
                            }
                            .filter { it != old[it.id] }
                    )
                }
            }
            tracks.size
        }
    }

    suspend fun folders(): List<String> =
        withContext(Dispatchers.IO) {
            val column =
                if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH
                else MediaStore.Audio.Media.DATA
            val result = mutableSetOf<String>()
            context.contentResolver
                .query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    if (Build.VERSION.SDK_INT >= 29)
                        arrayOf(column, MediaStore.MediaColumns.VOLUME_NAME)
                    else arrayOf(column),
                    null,
                    null,
                    null,
                )
                ?.use { c ->
                    while (c.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val path = c.getString(0) ?: continue
                        result +=
                            FolderRules.normalize(
                                if (Build.VERSION.SDK_INT >= 29)
                                    FolderRules.location(path, c.getString(1) ?: "external_primary")
                                else FolderRules.normalize(path.substringBeforeLast('/'))
                            )
                    }
                }
            result.filter { it.isNotBlank() }.sorted()
        }

    suspend fun edit(track: Track, edit: TrackEdit) = db.withTransaction {
        dao.saveEdit(edit)
        val latest = dao.track(track.id) ?: track
        dao.upsertTracks(
            listOf(
                latest.copy(
                    title = edit.title,
                    artist = edit.artist,
                    album = edit.album,
                    year = edit.year,
                    genre = edit.genre,
                    customArtwork = edit.cover ?: latest.customArtwork,
                    available = !edit.hidden,
                )
            )
        )
    }

    suspend fun albumCover(albumId: Long, uri: String) = db.withTransaction {
        dao.saveAlbumCover(AlbumCover(albumId, uri))
        val editedCovers = dao.edits().filter { it.cover != null }.map { it.trackId }.toSet()
        dao.upsertTracks(
            dao.allTracks()
                .filter { it.albumId == albumId && it.id !in editedCovers }
                .map { it.copy(customArtwork = uri) }
        )
    }

    suspend fun toggleFavorite(track: Track) = dao.toggleFavorite(track.id)

    suspend fun addToPlaylist(id: Long, trackIds: List<Long>) {
        db.withTransaction {
            dao.playlist(id)?.let {
                dao.savePlaylist(it.copy(tracks = (it.tracks + trackIds).distinct()))
            }
        }
    }
}
