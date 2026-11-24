package dev.lad.data

import android.content.Context
import android.os.SystemClock
import dev.lad.BuildConfig
import dev.lad.domain.ArtistPhotoMetadata
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class ArtistPhotos(context: Context) {
    data class Photo(val image: String, val page: String)

    private data class CacheEntry(
        val photo: Photo?,
        val evidence: Int,
        val expires: Long,
    )

    private val cache = context.getSharedPreferences("artist_photos_v3", Context.MODE_PRIVATE)
    private val memory = ConcurrentHashMap<String, CacheEntry>()
    private val artists = ConcurrentHashMap<String, Mutex>()
    // Shared HTTP/2 connection-pooled client for instant parallel lookups
    private val http =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    private val deezerPermits = Semaphore(10)
    private val applePermits = Semaphore(4)
    private val appleRate = Mutex()
    private var appleTokens = 6.0
    private var appleLastRefill = 0L
    @Volatile private var deezerBackoffUntil = 0L
    @Volatile private var appleBackoffUntil = 0L

    fun peek(artist: String): Photo? {
        if (artist.isBlank() || artist.length > 160) return null
        val key = ArtistPhotoMetadata.name(artist)
        memory[key]?.let { return it.photo }
        val saved =
            cache.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?: return null
        val photo =
            saved
                .optString("image")
                .takeIf { it.isNotEmpty() }
                ?.let { Photo(it, saved.optString("page")) }
        val entry = CacheEntry(photo, saved.optInt("evidence"), saved.optLong("expires"))
        memory[key] = entry
        return photo
    }

    suspend fun find(artist: String, tracks: List<Track>): Photo? =
        withContext(Dispatchers.IO) {
            if (artist.isBlank() || artist.length > 160) return@withContext null
            val key = ArtistPhotoMetadata.name(artist)
            val evidence = tracks.map { it.title to it.album }.distinct()
            val fingerprint =
                evidence.sortedWith(compareBy({ it.first }, { it.second })).hashCode()
            memory[key]?.let { cached ->
                if (
                    System.currentTimeMillis() < cached.expires &&
                        (cached.photo != null || cached.evidence == fingerprint)
                )
                    return@withContext cached.photo
            }
            val mutex = artists.getOrPut(key) { Mutex() }
            try {
                mutex.withLock {
                    val saved =
                        memory[key]
                            ?: cache
                                .getString(key, null)
                                ?.let { runCatching { JSONObject(it) }.getOrNull() }
                                ?.let { json ->
                                    val photo =
                                        json
                                            .optString("image")
                                            .takeIf { it.isNotEmpty() }
                                            ?.let { Photo(it, json.optString("page")) }
                                    CacheEntry(
                                        photo,
                                        json.optInt("evidence"),
                                        json.optLong("expires"),
                                    )
                                }
                                ?.also { memory[key] = it }
                    val previous = saved?.photo
                    if (
                        saved != null &&
                            System.currentTimeMillis() < saved.expires &&
                            (previous != null || saved.evidence == fingerprint)
                    )
                        return@withLock previous
                    try {
                        // Stage 1: Deezer public API — 1 tiny JSON call (~120ms), direct 500px portrait
                        var photo = findOnDeezer(artist, evidence)
                        // Stage 2: Apple Music fallback when Deezer has no portrait
                        if (photo == null) {
                            photo = findOnAppleMusic(artist, evidence)
                        }
                        currentCoroutineContext().ensureActive()
                        save(
                            key,
                            photo,
                            fingerprint,
                            if (photo != null) 30 * 86400000L else 10 * 60000L,
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

    private suspend fun findOnDeezer(
        artist: String,
        evidence: List<Pair<String, String>>,
    ): Photo? {
        val now = SystemClock.elapsedRealtime()
        if (deezerBackoffUntil > now) delay((deezerBackoffUntil - now).coerceAtMost(2000L))
        val encoded = URLEncoder.encode(artist, "UTF-8")
        val json =
            runCatching {
                    deezerGet("https://api.deezer.com/search/artist?q=$encoded&limit=10")
                }
                .getOrNull() ?: return null
        val data = json.optJSONArray("data") ?: return null
        val candidates =
            (0 until data.length()).mapNotNull { i ->
                val obj = data.optJSONObject(i) ?: return@mapNotNull null
                val image =
                    obj.optString("picture_big").ifBlank {
                        obj.optString("picture_xl").ifBlank { obj.optString("picture_medium") }
                    }
                ArtistPhotoMetadata.DeezerArtist(
                    id = obj.optLong("id"),
                    name = obj.optString("name"),
                    fans = obj.optInt("nb_fan"),
                    image = image,
                    page = obj.optString("link"),
                )
            }
        val exactKey = ArtistPhotoMetadata.name(artist)
        val exactMatches =
            candidates.filter {
                ArtistPhotoMetadata.name(it.name) == exactKey &&
                    ArtistPhotoMetadata.isValidDeezerImage(it.image)
            }
        var preferredId: Long? = null
        if (exactMatches.size > 1 && evidence.isNotEmpty()) {
            val sample =
                evidence.firstOrNull { it.first.isNotBlank() }?.first
                    ?: evidence.firstOrNull { it.second.isNotBlank() }?.second
            if (sample != null) {
                preferredId = disambiguateDeezerArtist(artist, sample, exactMatches.map { it.id }.toSet())
            }
        }
        val resolved = ArtistPhotoMetadata.resolveDeezer(artist, candidates, preferredId)
        if (resolved != null) {
            val page = resolved.page.ifBlank { "https://www.deezer.com/artist/${resolved.id}" }
            return Photo(resolved.image, page)
        }
        // If direct artist search didn't match (e.g. stylized name), try track search using local evidence
        val sampleTrack = evidence.firstOrNull { it.first.isNotBlank() }?.first ?: return null
        val trackQuery = URLEncoder.encode("$artist $sampleTrack", "UTF-8")
        val trackJson =
            runCatching {
                    deezerGet("https://api.deezer.com/search/track?q=$trackQuery&limit=5")
                }
                .getOrNull() ?: return null
        val trackData = trackJson.optJSONArray("data") ?: return null
        val looseKey = ArtistPhotoMetadata.looseName(artist)
        for (i in 0 until trackData.length()) {
            val artistObj = trackData.optJSONObject(i)?.optJSONObject("artist") ?: continue
            val name = artistObj.optString("name")
            if (
                ArtistPhotoMetadata.name(name) == exactKey ||
                    (looseKey.length >= 2 && ArtistPhotoMetadata.looseName(name) == looseKey)
            ) {
                val image =
                    artistObj.optString("picture_big").ifBlank {
                        artistObj.optString("picture_xl").ifBlank {
                            artistObj.optString("picture_medium")
                        }
                    }
                if (ArtistPhotoMetadata.isValidDeezerImage(image)) {
                    val id = artistObj.optLong("id")
                    val page =
                        artistObj.optString("link").ifBlank { "https://www.deezer.com/artist/$id" }
                    return Photo(image, page)
                }
            }
        }
        return null
    }

    private suspend fun disambiguateDeezerArtist(
        artist: String,
        sampleTitle: String,
        candidateIds: Set<Long>,
    ): Long? {
        val q = URLEncoder.encode("$artist $sampleTitle", "UTF-8")
        val json =
            runCatching { deezerGet("https://api.deezer.com/search/track?q=$q&limit=10") }
                .getOrNull() ?: return null
        val data = json.optJSONArray("data") ?: return null
        for (i in 0 until data.length()) {
            val id = data.optJSONObject(i)?.optJSONObject("artist")?.optLong("id") ?: continue
            if (id in candidateIds) return id
        }
        return null
    }

    private suspend fun deezerGet(url: String): JSONObject? =
        deezerPermits.withPermit {
            currentCoroutineContext().ensureActive()
            val body = httpGet(url, headOnly = false) ?: return@withPermit null
            val json = JSONObject(body)
            val err = json.optJSONObject("error")
            if (err != null && err.optInt("code") == 4) {
                deezerBackoffUntil = SystemClock.elapsedRealtime() + 1500L
                return@withPermit null
            }
            json
        }

    private suspend fun findOnAppleMusic(
        artist: String,
        evidence: List<Pair<String, String>>,
    ): Photo? {
        val primary =
            if (
                Regex("[А-Яа-яЁё]")
                    .containsMatchIn(artist + evidence.joinToString { "${it.first} ${it.second}" })
            )
                "ru"
            else "us"
        for (country in listOf(primary, if (primary == "ru") "us" else "ru")) {
            val songs = appleSearch(artist, "song", country, limit = 15)
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
            if (id == null) {
                val matches = appleSearch(artist, "musicArtist", country, limit = 10)
                id =
                    ArtistPhotoMetadata.matchingIds(
                            artist,
                            matches
                                .map { it.optLong("artistId") to it.optString("artistName") }
                                .filter { it.first > 0 },
                        )
                        .singleOrNull()
            }
            if (id != null) {
                val page = "https://music.apple.com/$country/artist/$id"
                val html = applePermits.withPermit { httpGet(page, headOnly = true) }
                val photo = html?.let { ArtistPhotoMetadata.image(it) }?.let { Photo(it, page) }
                if (photo != null) return photo
                return null
            }
        }
        return null
    }

    private suspend fun appleSearch(
        term: String,
        entity: String,
        country: String,
        limit: Int,
    ): List<JSONObject> {
        appleRate.withLock {
            val now = SystemClock.elapsedRealtime()
            if (appleBackoffUntil > now) delay((appleBackoffUntil - now).coerceAtMost(4000L))
            val current = SystemClock.elapsedRealtime()
            if (appleLastRefill == 0L) appleLastRefill = current
            val elapsed = (current - appleLastRefill).coerceAtLeast(0L)
            appleTokens = (appleTokens + elapsed / 1200.0).coerceAtMost(6.0)
            appleLastRefill = current
            if (appleTokens < 1.0) {
                delay(((1.0 - appleTokens) * 1200.0).toLong().coerceAtLeast(100L))
                appleLastRefill = SystemClock.elapsedRealtime()
                appleTokens = 0.0
            } else {
                appleTokens -= 1.0
            }
        }
        val url =
            "https://itunes.apple.com/search?term=${URLEncoder.encode(term, "UTF-8")}&entity=$entity&limit=$limit&country=$country"
        val text = applePermits.withPermit { httpGet(url, headOnly = false) } ?: return emptyList()
        val array = JSONObject(text).optJSONArray("results") ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private suspend fun httpGet(url: String, headOnly: Boolean): String? {
        currentCoroutineContext().ensureActive()
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", "Riff/${BuildConfig.VERSION_NAME}")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
        return http.newCall(request).execute().use { response ->
            if (response.code == 429 || response.code == 403 || response.code == 503) {
                if (url.contains("apple.com")) {
                    appleBackoffUntil = SystemClock.elapsedRealtime() + 5000L
                } else {
                    deezerBackoffUntil = SystemClock.elapsedRealtime() + 2000L
                }
                return@use null
            }
            if (!response.isSuccessful) return@use null
            val body = response.body ?: return@use null
            if (!headOnly) return@use body.string()
            body.charStream().buffered().use { reader ->
                val buffer = CharArray(4096)
                val text = StringBuilder()
                while (text.length < 192000) {
                    currentCoroutineContext().ensureActive()
                    val count = reader.read(buffer, 0, minOf(buffer.size, 192000 - text.length))
                    if (count < 0) break
                    text.append(buffer, 0, count)
                    val snapshot = text.toString()
                    if (
                        ArtistPhotoMetadata.image(snapshot) != null ||
                            snapshot.contains("</head>", ignoreCase = true)
                    )
                        break
                }
                text.toString()
            }
        }
    }

    private fun save(key: String, photo: Photo?, fingerprint: Int, lifetime: Long) {
        val expires = System.currentTimeMillis() + lifetime
        memory[key] = CacheEntry(photo, fingerprint, expires)
        val value =
            JSONObject()
                .put("image", photo?.image.orEmpty())
                .put("page", photo?.page.orEmpty())
                .put("evidence", fingerprint)
                .put("expires", expires)
        cache.edit().putString(key, value.toString()).apply()
    }
}
