package dev.lad.domain

import java.net.URI
import java.text.Normalizer
import java.util.Locale

object ArtistPhotoMetadata {
    fun name(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace('’', '\'')

    fun matchingIds(query: String, artists: List<Pair<Long, String>>): List<Long> =
        artists.filter { name(it.second) == name(query) }.map { it.first }.distinct()

    data class Recording(
        val artistId: Long,
        val artist: String,
        val title: String,
        val album: String,
    )

    fun resolve(
        query: String,
        recordings: List<Recording>,
        local: List<Pair<String, String>>,
    ): Long? {
        val candidates = recordings.filter { it.artistId > 0 && name(it.artist) == name(query) }
        val ids = candidates.map { it.artistId }.distinct()
        if (ids.size == 1) return ids.single()
        val titles = local.map { name(it.first) }.filter { it.isNotBlank() }.toSet()
        val albums = local.map { name(it.second) }.filter { it.isNotBlank() }.toSet()
        val scores =
            candidates
                .groupBy { it.artistId }
                .mapValues { (_, songs) ->
                    songs.map { name(it.title) }.distinct().count { it in titles } * 2 +
                        songs.map { name(it.album) }.distinct().count { it in albums }
                }
        val best = scores.values.maxOrNull() ?: return null
        if (best == 0) return null
        return scores.filterValues { it == best }.keys.singleOrNull()
    }

    fun looseName(value: String): String =
        name(value)
            .replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{N}]+"), "")

    data class DeezerArtist(
        val id: Long,
        val name: String,
        val fans: Int,
        val image: String,
        val page: String,
    )

    fun isValidDeezerImage(url: String): Boolean {
        if (url.isBlank()) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        if (uri.scheme != "https" || uri.userInfo != null) return false
        if (host != "cdn-images.dzcdn.net" && !host.endsWith(".dzcdn.net") && host != "e-cdns-images.dzcdn.net")
            return false
        val path = uri.path ?: return false
        if (!path.startsWith("/images/artist/")) return false
        // Reject Deezer empty-hash default placeholder silhouettes
        if (path.contains("/images/artist//")) return false
        if (path.contains("d41d8cd98f00b204e9800998ecf8427e")) return false
        return true
    }

    fun resolveDeezer(
        query: String,
        candidates: List<DeezerArtist>,
        preferredId: Long? = null,
    ): DeezerArtist? {
        val valid = candidates.filter { it.id > 0 && isValidDeezerImage(it.image) }
        if (valid.isEmpty()) return null
        if (preferredId != null) {
            valid.firstOrNull { it.id == preferredId }?.let { return it }
        }
        val exactKey = name(query)
        val exact = valid.filter { name(it.name) == exactKey }
        if (exact.isNotEmpty()) return exact.maxByOrNull { it.fans }
        val looseKey = looseName(query)
        if (looseKey.length >= 2) {
            val loose = valid.filter { looseName(it.name) == looseKey }
            if (loose.isNotEmpty()) return loose.maxByOrNull { it.fans }
        }
        return null
    }

    fun image(page: String): String? {
        for (tag in Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(page)) {
            val attributes =
                Regex("([\\w:-]+)\\s*=\\s*([\"'])(.*?)\\2", RegexOption.DOT_MATCHES_ALL)
                    .findAll(tag.value)
                    .associate { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[3] }
            if (attributes["property"]?.lowercase(Locale.ROOT) != "og:image") continue
            val value = attributes["content"]?.replace("&amp;", "&") ?: continue
            val uri = runCatching { URI(value) }.getOrNull() ?: continue
            val host = uri.host?.lowercase(Locale.ROOT) ?: continue
            if (uri.scheme != "https" || !host.endsWith(".mzstatic.com") || uri.userInfo != null)
                continue
            if (!uri.path.startsWith("/image/thumb/")) continue
            return value.replace(
                Regex("/\\d+x\\d+[^/?]*\\.(?:png|jpg|jpeg|webp)(?=\\?|$)"),
                "/512x512bb.jpg",
            )
        }
        return null
    }
}
