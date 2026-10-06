package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** The JVM half of the auto-sign limit case in `SportSignViewModelTest`: the text it expects is java.time's. */
class SportAutoSignLegacyDateTest {

    @Test
    fun theLimitDateIsWhatJavaTimeWroteOnARussianDevice() {
        // CLDR 42+ puts U+202F before "г."; the device text had plain spaces.
        val legacy = OffsetDateTime.parse("2026-09-12T09:00+03:00")
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag("ru")))
            .replace('\u202F', ' ').replace('\u00A0', ' ')

        assertEquals("12 сент. 2026 г., 09:00:00", legacy)
    }
}
