package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordbookSessionWindowTest {
    private val zone = TimeZone.of("Europe/Moscow")
    private val autumn = RecordbookPeriod("2026/2027", semester = 3, course = 2, actual = true)
    private val spring = RecordbookPeriod("2026/2027", semester = 4, course = 2, actual = false)

    private fun open(period: RecordbookPeriod, today: String, exam: String? = null) = RecordbookSessionWindow.isOpen(
        period,
        listOf(recordbookSubject().copy(examDate = exam?.let { Instant.parse("${it}T10:00:00+03:00") })),
        LocalDate.parse(today),
        zone,
    )

    @Test fun `mid-semester the session is hidden`() {
        assertFalse(open(autumn, "2026-09-07"))
        assertFalse(open(autumn, "2026-11-30"))
        assertFalse(open(spring, "2027-05-14"))
    }

    @Test fun `the last weeks by the calendar open it`() {
        assertTrue(open(autumn, "2026-12-01"))
        assertTrue(open(autumn, "2027-01-20"))
        assertTrue(open(spring, "2027-05-15"))
        assertTrue(open(spring, "2027-07-01"))
    }

    @Test fun `an exam at most four weeks ahead opens it earlier`() {
        assertTrue(open(autumn, "2026-11-10", exam = "2026-12-08"))
        assertFalse(open(autumn, "2026-11-10", exam = "2026-12-09"))
    }

    @Test fun `an earlier period always shows it, a later one never`() {
        assertTrue(open(RecordbookPeriod("2025/2026", 2, 1, false), "2026-09-07"))
        assertTrue(open(RecordbookPeriod("2025/2026", 1, 1, false), "2026-09-07"))
        assertFalse(open(spring, "2026-12-20"))
    }

    @Test fun `a study year of another shape never shows it`() {
        assertFalse(open(RecordbookPeriod("2026", 3, 2, true), "2026-12-20"))
    }
}
