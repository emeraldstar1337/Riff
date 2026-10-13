package dev.lad.domain

import java.text.Normalizer
import java.util.Locale

class ArtistCredits(rawNames: Iterable<String>, private val enabled: Boolean = true) {
    data class Artist(val key: String, val name: String)

    data class Group<T>(val key: String, val name: String, val items: List<T>)

    private val names = rawNames.groupingBy { it }.eachCount()
    private val parsed = names.keys.associateWith { split(it) }
    private val displayNames =
        buildMap<String, String> {
            val votes = mutableMapOf<String, MutableMap<String, Int>>()
            names.forEach { (raw, count) ->
                val parts = parsed.getValue(raw)
                parts.forEach { name ->
                    val choices = votes.getOrPut(key(name)) { mutableMapOf() }
                    choices[name] = (choices[name] ?: 0) + count * (if (parts.size == 1) 5 else 1)
                }
            }
            votes.forEach { (key, choices) ->
                put(
                    key,
                    choices.entries
                        .sortedWith(
                            compareByDescending<Map.Entry<String, Int>> { it.value }
                                .thenBy { it.key }
                        )
                        .first()
                        .key,
                )
            }
        }

    private fun resolve(parts: List<String>): List<Artist> =
        parts
            .map { name ->
                val key = key(name)
                Artist(key, displayNames[key] ?: name)
            }
            .distinctBy { it.key }

    private val resolved = parsed.mapValues { resolve(it.value) }

    fun artists(raw: String): List<Artist> = resolved[raw] ?: resolve(split(raw))

    fun <T> groups(items: List<T>, credit: (T) -> String): List<Group<T>> {
        val groups = linkedMapOf<String, MutableList<T>>()
        val labels = mutableMapOf<String, String>()
        items.forEach { item ->
            artists(credit(item)).forEach { artist ->
                labels[artist.key] = artist.name
                groups.getOrPut(artist.key) { mutableListOf() }.add(item)
            }
        }
        return groups
            .map { (key, rows) -> Group(key, labels.getValue(key), rows.toList()) }
            .sortedBy { it.key }
    }

    private fun key(name: String) = if (enabled) name.lowercase(Locale.ROOT) else name

    private fun split(raw: String): List<String> {
        if (!enabled) return listOf(raw.ifBlank { "Unknown artist" })
        val normalized =
            Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .replace("&amp;", "&", ignoreCase = true)
                .replace("\r\n", "\u001f")
                .replace("\n", "\u001f")
                .replace("\r", "\u001f")
                .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        if (normalized.isBlank() || normalized.lowercase(Locale.ROOT) in unknownNames)
            return listOf("Unknown artist")
        val featured = feature.replace(normalized, "\u001f")
        return featured
            .split(separators)
            .flatMap { piece ->
                val trimmed =
                    if (featured != normalized || separators.containsMatchIn(featured))
                        piece.trim().trim('(', ')', '[', ']').trim()
                    else piece.trim()
                val slash = trimmed.split('/').map { it.trim() }
                if (
                    slash.size > 1 &&
                        slash.none { it.isEmpty() } &&
                        !slash.all { it.length <= 3 && it.all { c -> c.isLetter() } }
                )
                    slash
                else listOf(if (slash.size > 1) slash.joinToString("/") else trimmed)
            }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("Unknown artist") }
    }

    companion object {
        private val unknownNames =
            setOf("<unknown>", "unknown artist", "неизвестный артист", "неизвестный исполнитель")
        private val feature =
            Regex(
                "\\s*(?:\\(|\\[)?\\s*\\b(?:feat(?:uring)?|ft|фит)(?:\\.\\s*|\\s*:\\s*|\\s+)",
                RegexOption.IGNORE_CASE,
            )
        private val separators =
            Regex(
                "\\s*[\\u0000\\u001f&,;+|、]\\s*|\\s+(?:x|×|and|with|vs\\.?|versus|w/|и|с|при участии|с участием|совместно с)\\s+|\\s+[-–—•·]\\s+",
                RegexOption.IGNORE_CASE,
            )
    }
}
