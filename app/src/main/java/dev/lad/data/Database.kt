package dev.lad.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val genre: String = "",
    val path: String = "",
    val addedAt: Long = 0,
    val favorite: Boolean = false,
    val available: Boolean = true,
    val fileSize: Long = 0,
    val mimeType: String = "",
    val customArtwork: String? = null,
    @ColumnInfo(defaultValue = "0") val modifiedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val artworkRevision: Long = 0,
) {
    val artwork: String
        get() = customArtwork ?: trackArtworkUri(id, uri, modifiedAt, fileSize, artworkRevision)
}

@Entity(tableName = "track_edits")
data class TrackEdit(
    @PrimaryKey val trackId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val year: Int,
    val genre: String,
    val cover: String? = null,
    val hidden: Boolean = false,
)

@Entity(tableName = "album_covers")
data class AlbumCover(@PrimaryKey val albumId: Long, val uri: String)

@Entity(tableName = "playback_runs")
data class PlaybackRun(@PrimaryKey val id: String, val startedAt: Long, val listenedMs: Long)

@Entity(tableName = "lyrics")
data class LyricsRecord(
    @PrimaryKey val trackId: Long,
    val text: String,
    val source: String = "Manual",
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val tracks: List<Long> = emptyList(),
    val cover: String? = null,
)

@Entity(tableName = "listens", indices = [Index("startedAt"), Index("trackId")])
data class Listen(
    @PrimaryKey val sessionId: String,
    val trackId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val artwork: String,
    val startedAt: Long,
    val listenedMs: Long,
    val counted: Boolean,
)

class Converters {
    @TypeConverter fun encode(ids: List<Long>): String = ids.joinToString(",")

    @TypeConverter
    fun decode(ids: String): List<Long> =
        if (ids.isBlank()) emptyList() else ids.split(',').mapNotNull { it.toLongOrNull() }
}

@Dao
interface MusicDao {
    @Query("SELECT * FROM tracks WHERE available = 1 ORDER BY title COLLATE NOCASE")
    fun tracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks") suspend fun allTracks(): List<Track>

    @Query("SELECT * FROM tracks WHERE id = :id") suspend fun track(id: Long): Track?

    @Query("SELECT * FROM tracks WHERE available = 1") suspend fun availableTracks(): List<Track>

    @Upsert suspend fun upsertTracks(tracks: List<Track>)

    @Query("UPDATE tracks SET available = 0") suspend fun markUnavailable()

    @Query("UPDATE tracks SET favorite = :favorite WHERE id = :id")
    suspend fun favorite(id: Long, favorite: Boolean)

    @Query("UPDATE tracks SET favorite = NOT favorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("SELECT * FROM track_edits") suspend fun edits(): List<TrackEdit>

    @Query("SELECT * FROM track_edits WHERE hidden = 1") fun hiddenEdits(): Flow<List<TrackEdit>>

    @Upsert suspend fun saveEdit(edit: TrackEdit)

    @Query("SELECT * FROM album_covers") suspend fun albumCovers(): List<AlbumCover>

    @Upsert suspend fun saveAlbumCover(cover: AlbumCover)

    @Query("SELECT * FROM lyrics WHERE trackId = :id") suspend fun lyrics(id: Long): LyricsRecord?

    @Upsert suspend fun saveLyrics(lyrics: LyricsRecord)

    @Query("SELECT * FROM playlists ORDER BY id DESC") fun playlists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id") suspend fun playlist(id: Long): Playlist?

    @Upsert suspend fun savePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playback_runs ORDER BY listenedMs DESC")
    fun runs(): Flow<List<PlaybackRun>>

    @Upsert suspend fun saveRun(run: PlaybackRun)

    @Query("DELETE FROM playback_runs") suspend fun clearRuns()

    @Query("SELECT * FROM listens ORDER BY startedAt DESC") fun listens(): Flow<List<Listen>>

    @Upsert suspend fun saveListen(listen: Listen)

    @Query("DELETE FROM listens") suspend fun clearStatistics()
}

@Database(
    entities =
        [
            Track::class,
            Playlist::class,
            Listen::class,
            TrackEdit::class,
            AlbumCover::class,
            LyricsRecord::class,
            PlaybackRun::class,
        ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LadDatabase : RoomDatabase() {
    abstract fun music(): MusicDao
}

val MIGRATION_1_2 =
    object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tracks ADD COLUMN fileSize INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE tracks ADD COLUMN mimeType TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE tracks ADD COLUMN customArtwork TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS track_edits (trackId INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL, year INTEGER NOT NULL, genre TEXT NOT NULL, cover TEXT, hidden INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS album_covers (albumId INTEGER NOT NULL PRIMARY KEY, uri TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS playback_runs (id TEXT NOT NULL PRIMARY KEY, startedAt INTEGER NOT NULL, listenedMs INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS lyrics (trackId INTEGER NOT NULL PRIMARY KEY, text TEXT NOT NULL, source TEXT NOT NULL)"
            )
        }
    }

val MIGRATION_2_3 =
    object : androidx.room.migration.Migration(2, 3) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tracks ADD COLUMN modifiedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE tracks ADD COLUMN artworkRevision INTEGER NOT NULL DEFAULT 0")
        }
    }
