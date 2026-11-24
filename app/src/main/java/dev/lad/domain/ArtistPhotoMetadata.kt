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
