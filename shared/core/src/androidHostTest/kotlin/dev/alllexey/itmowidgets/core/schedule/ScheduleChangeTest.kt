package dev.alllexey.itmowidgets.core.schedule

import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField.FORMAT
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField.PLACE
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField.TEACHER
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField.TIME
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleChangeTest {

    @Test
    fun `an added lesson marks its new occurrence`() {
        assertEquals(setOf(LessonOccurrence(1, TUE)), change(ScheduleChangeKind.ADDED, after = slot(1, TUE)).occurrences())
    }

    @Test
    fun `a cancelled lesson marks its old occurrence`() {
        assertEquals(setOf(LessonOccurrence(2, WED)), change(ScheduleChangeKind.CANCELLED, before = slot(2, WED)).occurrences())
    }

    @Test
    fun `a moved lesson marks both occurrences`() {
        val moved = change(ScheduleChangeKind.UPDATED, setOf(TIME, PLACE), before = slot(3, TUE), after = slot(4, WED))

        assertEquals(setOf(LessonOccurrence(3, TUE), LessonOccurrence(4, WED)), moved.occurrences())
    }

    @Test
    fun `a lesson changed in place marks one occurrence`() {
        val placed = change(ScheduleChangeKind.UPDATED, setOf(PLACE), before = slot(5, TUE), after = slot(5, TUE))

        assertEquals(setOf(LessonOccurrence(5, TUE)), placed.occurrences())
    }

    @Test
    fun `the headline field follows the priority of the fields`() {
        assertEquals(TIME, change(ScheduleChangeKind.UPDATED, setOf(TEACHER, PLACE, TIME), slot(1, TUE), slot(1, WED)).headlineField)
        assertEquals(FORMAT, change(ScheduleChangeKind.UPDATED, setOf(TEACHER, FORMAT, PLACE), slot(1, TUE), slot(1, TUE)).headlineField)
        assertEquals(PLACE, change(ScheduleChangeKind.UPDATED, setOf(TEACHER, PLACE), slot(1, TUE), slot(1, TUE)).headlineField)
        assertEquals(TEACHER, change(ScheduleChangeKind.UPDATED, setOf(TEACHER), slot(1, TUE), slot(1, TUE)).headlineField)
        assertNull(change(ScheduleChangeKind.ADDED, after = slot(1, TUE)).headlineField)
    }

    @Test
    fun `a one-sided change is over once its lesson has ended`() {
        val cancelled = change(ScheduleChangeKind.CANCELLED, before = slot(1, TUE))

        assertFalse(cancelled.isOver(TUE.atTime(9, 49)))
        assertTrue(cancelled.isOver(TUE.atTime(9, 50)))
    }

    @Test
    fun `a move is over only once both sides have ended`() {
        val moved = change(ScheduleChangeKind.UPDATED, setOf(TIME), before = slot(1, TUE), after = slot(1, WED))

        assertFalse(moved.isOver(TUE.atTime(12, 0)))
        assertTrue(moved.isOver(WED.atTime(9, 50)))
        assertEquals(TUE.atTime(8, 20), moved.soonestStart())
        assertTrue(moved.touches(TUE) && moved.touches(WED))
        assertFalse(moved.touches(TUE.plus(2, DateTimeUnit.DAY)))
    }

    private fun change(
        kind: ScheduleChangeKind,
        fields: Set<ScheduleChangeField> = emptySet(),
        before: LessonSlot? = null,
        after: LessonSlot? = null
    ) = ScheduleChange(
        id = "1", detectedAt = Instant.parse("2026-09-07T09:00:00Z"), kind = kind, fields = fields,
        subjectName = "Физика", typeId = 1, flowName = null, before = before, after = after, read = false, notified = false
    )

    private fun slot(pairId: Long, date: LocalDate) = LessonSlot(
        pairId = pairId, date = date, start = LocalTime(8, 20), end = LocalTime(9, 50), room = "1506",
        building = null, formatId = 1, format = "Очный", teacherIsu = null, teacherName = null
    )

    private companion object {
        val TUE: LocalDate = LocalDate(2026, 9, 8)
        val WED: LocalDate = LocalDate(2026, 9, 9)
    }
}
