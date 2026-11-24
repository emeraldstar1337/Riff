package dev.lad.data

import android.content.Context
import android.os.SystemClock
import dev.lad.BuildConfig
import dev.lad.domain.ArtistPhotoMetadata
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ArtistPhotos(context: Context) {
    data class Photo(val image: String, val page: String)

    private val cache = context.getSharedPreferences("artist_photos_v2", Context.MODE_PRIVATE)
    private val artists = ConcurrentHashMap<String, Mutex>()
    private val requests = Semaphore(3)
    private val searchRate = Mutex()
    private var lastSearch = 0L

    suspend fun find(artist: String, tracks: List<Track>): Photo? =
        withContext(Dispatchers.IO) {
            if (artist.isBlank() || artist.length > 160) return@withContext null
            val key = ArtistPhotoMetadata.name(artist)
            val mutex = artists.getOrPut(key) { Mutex() }
            try {
                mutex.withLock {
                    val saved =
                        cache.getString(key, null)?.let {
                            runCatching { JSONObject(it) }.getOrNull()
                        }
                    val previous =
                        saved
                            ?.optString("image")
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { Photo(it, saved.optString("page")) }
                    val evidence = tracks.map { it.title to it.album }.distinct()
                    val fingerprint =
                        evidence.sortedWith(compareBy({ it.first }, { it.second })).hashCode()
                    if (
                        saved != null &&
                            System.currentTimeMillis() < saved.optLong("expires") &&
                            (previous != null || saved.optInt("evidence") == fingerprint)
                    )
                        return@withLock previous
                    try {
                        val primary =
                            if (
                                Regex("[А-Яа-яЁё]")
                                    .containsMatchIn(
                                        artist +
                                            evidence.joinToString { "${it.first} ${it.second}" }
                                    )
                            )
                                "ru"
                            else "us"
                        var photo: Photo? = null
                        for (country in listOf(primary, if (primary == "ru") "us" else "ru")) {
                            val songs = search(artist, "song", country)
                            var id =
                                ArtistPhotoMetadata.resolve(
                                    artist,
                                    songs.map {
                                        ArtistPhotoMetadata.Recording(
                                            it.optLong("artistId"),
                                            it.optString("artistName"),
                                            it.optString("trackName"),
                                            it.optString("collectionName"),
                                        )
                                    },
                                    evidence,
                                )
                            if (id == null && evidence.isNotEmpty()) {
                                val album =
                                    evidence
                                        .map { it.second }
                                        .firstOrNull {
                                            it.isNotBlank() &&
                                                ArtistPhotoMetadata.name(it) !in
                                                    setOf("unknown album", "неизвестный альбом")
                                        }
                                if (album != null) {
                                    val albums = search("$artist $album", "album", country)
                                    id =
                                        ArtistPhotoMetadata.resolve(
                                            artist,
                                            albums.map {
                                                ArtistPhotoMetadata.Recording(
                                                    it.optLong("artistId"),
                                                    it.optString("artistName"),
                                                    "",
                                                    it.optString("collectionName"),
                                                )
                                            },
                                            evidence,
                                        )
                                }
                            }
                            if (id == null) {
                                val matches = search(artist, "musicArtist", country)
                                id =
                                    ArtistPhotoMetadata.matchingIds(
                                            artist,
                                            matches
                                                .map {
                                                    it.optLong("artistId") to
                                                        it.optString("artistName")
                                                }
                                                .filter { it.first > 0 },
                                        )
                                        .singleOrNull()
                            }
                            if (id != null) {
                                val page = "https://music.apple.com/$country/artist/$id"
                                photo =
                                    ArtistPhotoMetadata.image(request(page))?.let {
                                        Photo(it, page)
                                    }
                                if (photo != null) break
                            }
                        }
                        currentCoroutineContext().ensureActive()
                        save(
                            key,
                            photo,
                            fingerprint,
                            if (photo != null) 7 * 86400000L else 15 * 60000L,
                        )
                        photo
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        save(key, previous, fingerprint, 60000L)
                        previous
                    }
                }
            } finally {
                if (artists.size > 256) artists.remove(key, mutex)
            }
        }

    private suspend fun search(term: String, entity: String, country: String): List<JSONObject> {
        searchRate.withLock {
            val wait = 3100L - (SystemClock.elapsedRealtime() - lastSearch)
            if (wait > 0) delay(wait)
            lastSearch = SystemClock.elapsedRealtime()
        }
        val array =
            JSONObject(
                    request(
                        "https://itunes.apple.com/search?term=${URLEncoder.encode(term, "UTF-8")}&entity=$entity&limit=50&country=$country"
                    )
                )
                .getJSONArray("results")
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun save(key: String, photo: Photo?, fingerprint: Int, lifetime: Long) {
        val value =
            JSONObject()
                .put("image", photo?.image.orEmpty())
                .put("page", photo?.page.orEmpty())
                .put("evidence", fingerprint)
                .put("expires", System.currentTimeMillis() + lifetime)
        cache.edit().putString(key, value.toString()).apply()
    }

    private suspend fun request(url: String): String =
        requests.withPermit {
            currentCoroutineContext().ensureActive()
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 12000
                connection.readTimeout = 12000
                connection.setRequestProperty("User-Agent", "Riff/${BuildConfig.VERSION_NAME}")
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                check(connection.responseCode == 200)
                connection.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(4096)
                    val text = StringBuilder()
                    while (text.length < 768000) {
                        currentCoroutineContext().ensureActive()
                        val count = reader.read(buffer, 0, minOf(buffer.size, 768000 - text.length))
                        if (count < 0) break
                        text.append(buffer, 0, count)
                    }
                    text.toString()
                }
            } finally {
                connection.disconnect()
            }
        }
}
