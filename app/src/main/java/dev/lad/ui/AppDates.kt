package dev.lad.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object AppDates {
    val formats = listOf("system", "dd-MM-yy", "dd.MM.yyyy", "MM/dd/yyyy", "yyyy-MM-dd")

    fun format(date: LocalDate, pattern: String, locale: Locale = Locale.getDefault()): String =
        date.format(
            if (pattern in formats && pattern != "system")
                DateTimeFormatter.ofPattern(pattern, locale)
            else DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        )

    fun format(time: Long, pattern: String, locale: Locale = Locale.getDefault()): String =
        format(
            Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate(),
            pattern,
            locale,
        )
}
