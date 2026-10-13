package dev.lad.data

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import dev.lad.BuildConfig
import dev.lad.R
import dev.lad.domain.LyricsMatch
import dev.lad.domain.OnlineLyrics
import dev.lad.i18n.AppLanguage
import dev.lad.i18n.TextResources
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LyricsRepository(
    private val context: Context,
    private val dao: MusicDao,
    private val fetch: (String) -> LyricsHttpReply = ::fetchLyricsUrl,
) {
    private val strings
        get() = TextResources(AppLanguage.context(context))

    private val requests = Mutex()
    private val cache = linkedMapOf<String, Pair<Long, String?>>()
    private var lastRequest = 0L
    private var retryAfter = 0L

    suspend fun load(track: Track): LyricsRecord? = dao.lyrics(track.id)

    suspend fun import(id: Long, uri: Uri) =
        withContext(Dispatchers.IO) {
            val bytes =
                context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(2_000_000) }
                    ?: error(strings[R.string.could_not_open_the_lyrics_file])
            dao.saveLyrics(
                LyricsRecord(
                    id,
                    bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF"),
                    strings[R.string.lrc_txt_file],
                )
            )
        }

    suspend fun search(
        title: String,
        artist: String,
        durationMs: Long = 0,
        album: String = "",
    ): List<OnlineLyrics> {
        if (title.isBlank()) return emptyList()
        val body =
            request("search?" + query("track_name" to title.trim(), "artist_name" to artist.trim()))
                ?: return emptyList()
        val array = JSONArray(body)
        val candidates = (0 until array.length()).map { parseOnline(array.getJSONObject(it)) }
        return LyricsMatch.rank(candidates, title, artist, durationMs, album)
    }

    suspend fun findSynced(track: Track): OnlineLyrics? {
        if (track.title.isBlank() || track.artist.isBlank()) return null
        val signature =
            query(
                "track_name" to track.title,
                "artist_name" to track.artist,
                "album_name" to track.album,
                "duration" to (track.durationMs / 1000.0).toString(),
            )
        if (track.durationMs in 1000..3_600_000) {
            request("get?$signature")?.let { body ->
                val candidate = parseOnline(JSONObject(body))
                if (LyricsMatch.exact(candidate, track.title, track.artist, track.durationMs))
                    return candidate
            }
        }
        return search(track.title, track.artist, track.durationMs, track.album).firstOrNull {
            LyricsMatch.exact(it, track.title, track.artist, track.durationMs)
        }
    }

    suspend fun save(id: Long, text: String, source: String) =
        dao.saveLyrics(LyricsRecord(id, text, source))

    private suspend fun request(path: String): String? =
        withContext(Dispatchers.IO) {
            requests.withLock {
                val now = SystemClock.elapsedRealtime()
                cache[path]
                    ?.takeIf { now - it.first < 600_000 }
                    ?.let {
                        return@withLock it.second
                    }
                if (now < retryAfter)
                    error(
                        strings.format(
                            R.string.lrclib_asks_you_to_wait_s,
                            (retryAfter - now) / 1000 + 1,
                        )
                    )
                delay((300 - (now - lastRequest)).coerceAtLeast(0))
                currentCoroutineContext().ensureActive()
                val response =
                    try {
                        fetch("https://lrclib.net/api/$path")
                    } finally {
                        lastRequest = SystemClock.elapsedRealtime()
                    }
                currentCoroutineContext().ensureActive()
                if (response.code == 429) {
                    retryAfter =
                        SystemClock.elapsedRealtime() +
                            response.retrySeconds.coerceAtLeast(1) * 1000L
                    error(
                        strings.format(
                            R.string.lrclib_is_busy_try_again_in,
                            response.retrySeconds.coerceAtLeast(1),
                        )
                    )
                }
                if (response.code != 200 && response.code != 404)
                    error(strings.format(R.string.lyrics_service_response, response.code))
                val body = response.body.takeIf { response.code == 200 }
                if (cache.size >= 64) cache.remove(cache.keys.first())
                cache[path] = SystemClock.elapsedRealtime() to body
                body
            }
        }
}

internal fun parseOnline(item: JSONObject): OnlineLyrics {
    val synced = item.optString("syncedLyrics").takeUnless { it == "null" || it.isBlank() }
    return OnlineLyrics(
        item.optString("trackName"),
        item.optString("artistName"),
        item.optString("albumName"),
        item.optDouble("duration", 0.0).toInt(),
        synced.orEmpty(),
        synced != null,
    )
}

data class LyricsHttpReply(val code: Int, val body: String, val retrySeconds: Long = 60)

private fun query(vararg pairs: Pair<String, String>) =
    pairs.joinToString("&") { it.first + "=" + URLEncoder.encode(it.second, "UTF-8") }

private fun fetchLyricsUrl(url: String): LyricsHttpReply {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.setRequestProperty(
            "User-Agent",
            "Lad/${BuildConfig.VERSION_NAME} (personal Android music player)",
        )
        val code = connection.responseCode
        val body =
            if (code == 200)
                connection.inputStream.use {
                    it.readBytesLimited(2_000_000).toString(Charsets.UTF_8)
                }
            else ""
        return LyricsHttpReply(
            code,
            body,
            connection.getHeaderField("Retry-After")?.toLongOrNull() ?: 60,
        )
    } finally {
        connection.disconnect()
    }
}

private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        require(output.size() + n <= limit) { "The lyrics file is too large" }
        output.write(buffer, 0, n)
    }
    return output.toByteArray()
}
