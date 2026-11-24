package dev.lad.ui

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class AppDatesTest {
    @Test
    fun explicitFormatsDoNotSwapMonthAndDay() {
        val date = LocalDate.of(2026, 10, 18)
        assertEquals("18-10-26", AppDates.format(date, "dd-MM-yy", Locale.ENGLISH))
        assertEquals("18.10.2026", AppDates.format(date, "dd.MM.yyyy", Locale.ENGLISH))
        assertEquals("10/18/2026", AppDates.format(date, "MM/dd/yyyy", Locale.ENGLISH))
        assertEquals("2026-10-18", AppDates.format(date, "yyyy-MM-dd", Locale.ENGLISH))
    }

    @Test
    fun invalidSavedPatternFallsBackToDeviceLocale() {
        val date = LocalDate.of(2026, 10, 18)
        assertEquals(
            AppDates.format(date, "system", Locale.ENGLISH),
            AppDates.format(date, "invalid", Locale.ENGLISH),
        )
    }
}
