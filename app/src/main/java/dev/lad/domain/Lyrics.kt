package dev.lad.domain

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

data class LyricWord(val timeMs: Long, val start: Int, val end: Int, val endTimeMs: Long? = null)

data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList())

data class OnlineLyrics(
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val text: String,
    val synced: Boolean,
)

object LyricsMatch {
    private fun normalized(value: String) =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    fun exact(candidate: OnlineLyrics, title: String, artist: String, durationMs: Long): Boolean =
        candidate.synced &&
            LrcParser.parse(candidate.text).any { it.text.isNotBlank() } &&
            normalized(candidate.title) == normalized(title) &&
            normalized(candidate.artist) == normalized(artist) &&
            durationMs > 0 &&
            candidate.duration > 0 &&
            abs(candidate.duration * 1000L - durationMs) <= 2000

    fun rank(
        candidates: List<OnlineLyrics>,
        title: String,
        artist: String,
        durationMs: Long,
        album: String = "",
    ): List<OnlineLyrics> =
        candidates
            .filter { it.synced && LrcParser.parse(it.text).any { line -> line.text.isNotBlank() } }
            .sortedWith(
                compareBy<OnlineLyrics> {
                        if (
                            normalized(it.title) == normalized(title) &&
                                normalized(it.artist) == normalized(artist)
                        )
                            0
                        else 1
                    }
                    .thenBy { if (durationMs > 0) abs(it.duration * 1000L - durationMs) else 0L }
                    .thenBy {
                        if (album.isNotBlank() && normalized(it.album) == normalized(album)) 0
                        else 1
                    }
            )
            .distinctBy { it.text }
}

object LrcParser {
    private val stamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val wordStamp = Regex("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>")

    private fun time(match: MatchResult): Long? {
        if (match.groupValues[2].toInt() >= 60) return null
        val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
        return match.groupValues[1].toLong() * 60_000 +
            match.groupValues[2].toLong() * 1000 +
            fraction
    }

    private fun content(raw: String): Pair<String, List<LyricWord>> {
        val tags = wordStamp.findAll(raw).toList()
        if (tags.isEmpty()) return raw.trim() to emptyList()
        val result = StringBuilder(raw.substring(0, tags.first().range.first))
        val words = mutableListOf<LyricWord>()
        var valid = true
        tags.forEachIndexed { i, tag ->
            val chunk =
                raw.substring(tag.range.last + 1, tags.getOrNull(i + 1)?.range?.first ?: raw.length)
            val start = result.length + chunk.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            result.append(chunk)
            val end = result.length - chunk.takeLastWhile { it.isWhitespace() }.length
            val stampTime = time(tag)
            if (stampTime == null) valid = false
            else if (start < end)
                words += LyricWord(stampTime, start, end, tags.getOrNull(i + 1)?.let(::time))
        }
        val text = result.toString()
        val leading = text.length - text.trimStart().length
        val trimmed = text.trim()
        val spans =
            words
                .map { it.copy(start = it.start - leading, end = it.end - leading) }
                .filter { it.start >= 0 && it.end <= trimmed.length }

        valid = valid && spans.zipWithNext().all { (a, b) -> a.timeMs <= b.timeMs }
        return trimmed to if (valid) spans else emptyList()
    }

    fun parse(text: String): List<LyricLine> {
        val offset =
            Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
                .find(text)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull() ?: 0
        return text
            .lineSequence()
            .flatMap { raw ->
                val stamps = stamp.findAll(raw).toList()
                val (words, timedWords) = content(raw.replace(stamp, ""))
                val first = stamps.firstNotNullOfOrNull(::time) ?: 0L
                stamps.asSequence().mapNotNull { m ->
                    val lineTime = time(m) ?: return@mapNotNull null
                    LyricLine(
                        (lineTime + offset).coerceAtLeast(0),
                        words,
                        timedWords.map {
                            it.copy(
                                timeMs = (it.timeMs + offset + lineTime - first).coerceAtLeast(0),
                                endTimeMs =
                                    it.endTimeMs?.let { end ->
                                        (end + offset + lineTime - first).coerceAtLeast(0)
                                    },
                            )
                        },
                    )
                }
            }
            .sortedBy { it.timeMs }
            .toList()
    }

    fun currentWord(line: LyricLine, positionMs: Long): Int {
        if (positionMs < line.timeMs) return -1
        return line.words.indexOfLast {
            it.timeMs <= positionMs && (it.endTimeMs == null || positionMs < it.endTimeMs)
        }
    }

    fun current(lines: List<LyricLine>, positionMs: Long): Int {
        var low = 0
        var high = lines.size
        while (low < high) {
            val middle = (low + high) / 2
            if (lines[middle].timeMs <= positionMs) low = middle + 1 else high = middle
        }
        return low - 1
    }
}
