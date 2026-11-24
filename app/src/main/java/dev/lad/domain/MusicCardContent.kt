package dev.lad.domain

object MusicCardContent {
    fun currentLine(text: String, positionMs: Long, offsetMs: Long = 0): String {
        val lines = LrcParser.parse(text)
        return lines.getOrNull(LrcParser.current(lines, positionMs + offsetMs))?.text.orEmpty()
    }
}
