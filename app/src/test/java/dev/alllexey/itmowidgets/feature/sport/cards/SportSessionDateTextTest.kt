package dev.alllexey.itmowidgets.feature.sport.cards

import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSessionTiming
import dev.alllexey.itmowidgets.feature.sport.ui.common.fullDateText
import dev.alllexey.itmowidgets.feature.sport.ui.common.shareDateText
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** The app-side date texts of `SportSessionPresentationTest`; they move with the `ui/common` helpers (LP-5b). */
class SportSessionDateTextTest {

    @Test fun `russian weekday and month names are capitalised for display`() {
        val time = FixedAcademicTime(LocalDate.of(2026, 9, 1))
        val friday = Instant.parse("2026-09-25T08:10:00+03:00")
        assertEquals("Пятница, 25 сентября 2026", SportSessionTiming(friday, friday + 90.minutes, time).fullDateText())
    }

    @Test fun `shared date names the weekday and the date, never today or tomorrow`() {
        val lesson = SportCardFixtures.lesson()
        val time = FixedAcademicTime(LocalDateTime.of(2026, 9, 8, 17, 30))
        assertEquals("вторник, 8 сентября, 18:30–20:00", SportSessionTiming(lesson.start, lesson.end, time).shareDateText())
    }
}
