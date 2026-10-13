package dev.lad.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dev.lad.domain.*
import dev.lad.util.attempt
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.ladSettings by
    preferencesDataStore("lad", produceMigrations = { listOf(PreferencesMigration()) })
private val Context.ladPosition by preferencesDataStore("lad_position")

data class Settings(
    val theme: String = "system",
    val dynamicColor: Boolean = true,
    val artworkColor: Boolean = true,
    val animations: Boolean = true,
    val minimumSeconds: Int = 10,
    val excludedFolders: String = "",
    val statistics: Boolean = true,
    val pauseOnDisconnect: Boolean = true,
    val speed: Float = 1f,
    val replayGain: Boolean = false,
    val crossfadeSeconds: Int = 0,
    val includedFolders: String = "",
    val accent: String = "violet",
    val pureBlack: Boolean = false,
    val coverShape: String = "rounded",
    val recapArtwork: Boolean = true,
    val ambientBackground: Boolean = true,
    val playerStyle: String = "immersive",
    val miniStyle: String = "card",
    val vinyl: Boolean = false,
    val visualizer: Boolean = false,
    val navigationTabs: String = "home,library,playlists,stats",
    val audioProfiles: String = "{}",
    val seekStyle: String = "wave_bar",
    val glowPreset: String = "soft",
    val glowStrength: Float = 1f,
    val glowRadius: Float = 1f,
    val glowSaturation: Float = 0.8f,
    val glowMotion: Boolean = false,
    val glowShapes: Boolean = false,
    val shapeOpacity: Float = 0.07f,
    val motionSpeed: Float = 1f,
    val lyricsPreset: String = "balanced",
    val lyricsSize: Float = 24f,
    val lyricsSpacing: Float = 1.3f,
    val lyricsAlignment: String = "start",
    val lyricsDim: Float = 0.46f,
    val mergeArtists: Boolean = true,
)

class SettingsStore(private val context: Context) {
    private val queueKey = stringPreferencesKey("queue_v1")
    private val theme = stringPreferencesKey("theme")
    private val dynamic = booleanPreferencesKey("dynamic")
    private val cover = booleanPreferencesKey("cover")
    private val animations = booleanPreferencesKey("animations")
    private val minimum = intPreferencesKey("minimum")
    private val exclusions = stringPreferencesKey("exclusions")
    private val stats = booleanPreferencesKey("stats")
    private val headphones = booleanPreferencesKey("headphones")
    private val speed = floatPreferencesKey("speed")
    private val gain = booleanPreferencesKey("gain")
    private val fade = intPreferencesKey("fade")
    private val inclusions = stringPreferencesKey("inclusions")
    private val accent = stringPreferencesKey("accent")
    private val black = booleanPreferencesKey("pureBlack")
    private val shape = stringPreferencesKey("coverShape")
    private val recapCover = booleanPreferencesKey("recapArtwork")
    private val ambient = booleanPreferencesKey("ambientBackground")
    private val playerStyle = stringPreferencesKey("playerStyle")
    private val miniStyle = stringPreferencesKey("miniStyle")
    private val vinyl = booleanPreferencesKey("vinyl")
    private val visualizer = booleanPreferencesKey("visualizer")
    private val navigation = stringPreferencesKey("navigation")
    private val audioProfiles = stringPreferencesKey("audioProfiles")
    private val seekStyle = stringPreferencesKey("seekStyle")
    private val glowPreset = stringPreferencesKey("glowPreset")
    private val glowStrength = floatPreferencesKey("glowStrength")
    private val glowRadius = floatPreferencesKey("glowRadius")
    private val glowSaturation = floatPreferencesKey("glowSaturation")
    private val glowMotion = booleanPreferencesKey("glowMotion")
    private val glowShapes = booleanPreferencesKey("glowShapes")
    private val shapeOpacity = floatPreferencesKey("shapeOpacity")
    private val motionSpeed = floatPreferencesKey("motionSpeed")
    private val lyricsPreset = stringPreferencesKey("lyricsPreset")
    private val lyricsSize = floatPreferencesKey("lyricsSize")
    private val lyricsSpacing = floatPreferencesKey("lyricsSpacing")
    private val lyricsAlignment = stringPreferencesKey("lyricsAlignment")
    private val lyricsDim = floatPreferencesKey("lyricsDim")
    private val mergeArtists = booleanPreferencesKey("mergeArtists")
    private val historyKey = stringPreferencesKey("queue_history_v1")
    private var cachedHistory: List<QueueRevision>? = null

    private fun readSettings(p: Preferences): Settings =
        Settings(
            p[theme] ?: "system",
            p[dynamic] ?: true,
            p[cover] ?: true,
            p[animations] ?: true,
            p[minimum] ?: 10,
            p[exclusions] ?: "",
            p[stats] ?: true,
            p[headphones] ?: true,
            p[speed] ?: 1f,
            p[gain] ?: false,
            p[fade] ?: 0,
            p[inclusions] ?: "",
            p[accent] ?: "violet",
            p[black] ?: false,
            p[shape] ?: "rounded",
            p[recapCover] ?: true,
            p[ambient] ?: true,
            p[playerStyle] ?: "immersive",
            p[miniStyle] ?: "card",
            p[vinyl] ?: false,
            p[visualizer] ?: false,
            p[navigation] ?: "home,library,playlists,stats",
            p[audioProfiles] ?: "{}",
            p[seekStyle] ?: "wave_bar",
            p[glowPreset] ?: "soft",
            p[glowStrength] ?: 1f,
            p[glowRadius] ?: 1f,
            p[glowSaturation] ?: 0.8f,
            p[glowMotion] ?: false,
            p[glowShapes] ?: false,
            p[shapeOpacity] ?: 0.07f,
            p[motionSpeed] ?: 1f,
            p[lyricsPreset] ?: "balanced",
            p[lyricsSize] ?: 24f,
            p[lyricsSpacing] ?: 1.3f,
            p[lyricsAlignment] ?: "start",
            p[lyricsDim] ?: 0.46f,
            p[mergeArtists] ?: true,
        )

    val settings = context.ladSettings.data.map(::readSettings).distinctUntilChanged()
    private val positionKey = longPreferencesKey("position")
    private val positionItemKey = stringPreferencesKey("item")
    private var cachedQueue: QueueSnapshot? = null

    private fun positionItem(s: QueueSnapshot) =
        "${s.current?.trackId}:${s.current?.cycle}:${s.index}"

    val queue =
        combine(context.ladSettings.data, context.ladPosition.data) { p, position ->
            p[queueKey]
                ?.let { attempt { decodeQueue(it) }.getOrNull() }
                ?.let { saved ->
                    if (position[positionItemKey] == positionItem(saved))
                        saved.copy(playbackMs = position[positionKey] ?: 0)
                    else saved
                }
        }

    suspend fun update(value: Settings) = update { value }

    suspend fun update(transform: (Settings) -> Settings): Settings {
        var updated = Settings()
        context.ladSettings.edit { p ->
            val value = transform(readSettings(p))
            updated = value
            p[theme] = value.theme
            p[dynamic] = value.dynamicColor
            p[cover] = value.artworkColor
            p[animations] = value.animations
            p[minimum] = value.minimumSeconds
            p[exclusions] = value.excludedFolders
            p[stats] = value.statistics
            p[headphones] = value.pauseOnDisconnect
            p[speed] = value.speed
            p[gain] = value.replayGain
            p[fade] = value.crossfadeSeconds
            p[inclusions] = value.includedFolders
            p[accent] = value.accent
            p[black] = value.pureBlack
            p[shape] = value.coverShape
            p[recapCover] = value.recapArtwork
            p[ambient] = value.ambientBackground
            p[playerStyle] = value.playerStyle
            p[miniStyle] = value.miniStyle
            p[vinyl] = value.vinyl
            p[visualizer] = value.visualizer
            p[navigation] = value.navigationTabs
            p[seekStyle] = value.seekStyle
            p[glowPreset] = value.glowPreset
            p[glowStrength] = value.glowStrength
            p[glowRadius] = value.glowRadius
            p[glowSaturation] = value.glowSaturation
            p[glowMotion] = value.glowMotion
            p[glowShapes] = value.glowShapes
            p[shapeOpacity] = value.shapeOpacity
            p[motionSpeed] = value.motionSpeed
            p[lyricsPreset] = value.lyricsPreset
            p[lyricsSize] = value.lyricsSize
            p[lyricsSpacing] = value.lyricsSpacing
            p[lyricsAlignment] = value.lyricsAlignment
            p[lyricsDim] = value.lyricsDim
            p[mergeArtists] = value.mergeArtists
        }
        return updated
    }

    val queueHistory =
        context.ladSettings.data.map { p ->
            attempt {
                    val rows = JSONArray(p[historyKey] ?: "[]")
                    (0 until rows.length())
                        .map { i ->
                            rows.getJSONObject(i).let {
                                QueueRevision(
                                    it.getLong("id"),
                                    it.getString("label"),
                                    it.getLong("time"),
                                    decodeQueue(it.getString("snapshot")),
                                )
                            }
                        }
                        .takeLast(12)
                }
                .getOrDefault(emptyList())
        }

    suspend fun saveQueue(snapshot: QueueSnapshot, history: List<QueueRevision>? = null) {
        context.ladPosition.edit { p ->
            p[positionKey] = snapshot.playbackMs
            p[positionItemKey] = positionItem(snapshot)
        }
        val timeline = snapshot.copy(playbackMs = 0)
        if (timeline == cachedQueue && (history == null || history == cachedHistory)) return
        context.ladSettings.edit { p ->
            if (timeline != cachedQueue) p[queueKey] = encodeQueue(timeline)
            if (history != null && history != cachedHistory) {
                val json =
                    JSONArray()
                        .apply {
                            history.forEach { row ->
                                put(
                                    JSONObject()
                                        .put("id", row.id)
                                        .put("label", row.label)
                                        .put("time", row.createdAt)
                                        .put("snapshot", encodeQueue(row.snapshot))
                                )
                            }
                        }
                        .toString()
                p[historyKey] = json
            }
        }
        cachedQueue = timeline
        if (history != null) cachedHistory = history
    }

    suspend fun updateAudioProfile(device: String, profile: SoundProfile) {
        context.ladSettings.edit { p ->
            p[audioProfiles] = SoundProfiles.write(p[audioProfiles] ?: "{}", device, profile)
        }
    }

    companion object {
        fun encodeQueue(s: QueueSnapshot): String =
            JSONObject()
                .apply {
                    put("source", JSONArray(s.source))
                    put(
                        "entries",
                        JSONArray().apply {
                            s.entries.forEach {
                                put(
                                    JSONObject()
                                        .put("id", it.trackId)
                                        .put("cycle", it.cycle)
                                        .put("manual", it.manual)
                                )
                            }
                        },
                    )
                    put("index", s.index)
                    put("shuffle", s.shuffle)
                    put("repeat", s.repeat.name)
                    put("context", s.context)
                    put("position", s.playbackMs)
                }
                .toString()

        fun decodeQueue(text: String): QueueSnapshot {
            val j = JSONObject(text)
            val source = j.getJSONArray("source")
            val entries = j.getJSONArray("entries")
            return QueueSnapshot(
                (0 until source.length()).map { source.getLong(it) },
                (0 until entries.length()).map {
                    entries.getJSONObject(it).let { o ->
                        QueueEntry(o.getLong("id"), o.getInt("cycle"), o.optBoolean("manual"))
                    }
                },
                j.getInt("index"),
                j.getBoolean("shuffle"),
                RepeatMode.valueOf(j.getString("repeat")),
                j.getString("context"),
                j.getLong("position"),
            )
        }
    }
}
