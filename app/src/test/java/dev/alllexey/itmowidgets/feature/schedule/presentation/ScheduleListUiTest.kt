package dev.alllexey.itmowidgets.feature.schedule.presentation

import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleLessonState.COMPLETED
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleLessonState.CURRENT
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleLessonState.NEXT
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleLessonState.UPCOMING
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleListUiTest {

    @Test
    fun `a gap of more than an hour between lessons becomes a break row`() {
        val rows = rowsOf(day(TODAY, lesson(1, "08:20", "09:50"), lesson(2, "11:00", "12:30"), lesson(3, "13:31", "15:00")))

        assertEquals(
            listOf(
                ScheduleRowUi.LessonRow::class,
                ScheduleRowUi.BreakRow::class,
                ScheduleRowUi.LessonRow::class,
                ScheduleRowUi.BreakRow::class,
                ScheduleRowUi.LessonRow::class
            ),
            rows.map { it::class }
        )
        assertEquals(ScheduleRowUi.BreakRow(LocalTime(9, 50), LocalTime(11, 0)), rows[1])
        assertEquals(70, (rows[1] as ScheduleRowUi.BreakRow).minutes)
        assertEquals(61, (rows[3] as ScheduleRowUi.BreakRow).minutes)
    }

    @Test
    fun `a gap of exactly an hour is not a break`() {
        val rows = rowsOf(day(TODAY, lesson(1, "08:20", "09:50"), lesson(2, "10:50", "12:20")))

        assertTrue(rows.none { it is ScheduleRowUi.BreakRow })
    }

    @Test
    fun `lessons are ordered by start and only the last row is flagged last`() {
        val rows = rowsOf(day(TODAY, lesson(2, "11:00", "12:30"), lesson(1, "09:30", "11:00")))

        assertEquals(listOf(1L, 2L), rows.map { (it as ScheduleRowUi.LessonRow).lesson.pairId })
        assertEquals(listOf(false, true), rows.map { (it as ScheduleRowUi.LessonRow).isLast })
    }

    @Test
    fun `states follow the original lesson index after sorting`() {
        val rows = rowsOf(day(TODAY, lesson(2, "10:30", "12:00"), lesson(1, "08:00", "09:30")))

        assertEquals(listOf(COMPLETED, NEXT), rows.map { (it as ScheduleRowUi.LessonRow).state })
    }

    @Test
    fun `pending sport rows merge by start, fill a gap and can be last`() {
        val rows = rowsOf(
            day(TODAY, lesson(1, "08:20", "09:50"), lesson(2, "13:30", "15:00")).copy(
                pendingSport = listOf(pending(TODAY, "11:00", prediction = false), pending(TODAY, "17:00", prediction = true))
            )
        )

        assertEquals(
            listOf(LocalTime(8, 20), LocalTime(11, 0), LocalTime(13, 30), LocalTime(17, 0)),
            rows.map { row ->
                when (row) {
                    is ScheduleRowUi.LessonRow -> row.lesson.start
                    is ScheduleRowUi.PendingSportRow -> row.start
                    else -> error("Unexpected row $row")
                }
            }
        )
        val waiting = rows[1] as ScheduleRowUi.PendingSportRow
        val predicted = rows[3] as ScheduleRowUi.PendingSportRow
        assertEquals(PendingSportStatus.WAITING, waiting.status)
        assertEquals(LocalTime(12, 30), waiting.end)
        assertFalse(waiting.isLast)
        assertEquals(PendingSportStatus.PREDICTION, predicted.status)
        assertTrue(predicted.isLast)
        assertFalse((rows[2] as ScheduleRowUi.LessonRow).isLast)
    }

    @Test
    fun `a pending row outside the gap keeps the break`() {
        val rows = rowsOf(
            day(TODAY, lesson(1, "08:20", "09:50"), lesson(2, "13:30", "15:00")).copy(
                pendingSport = listOf(pending(TODAY, "15:00", prediction = true))
            )
        )

        assertEquals(1, rows.count { it is ScheduleRowUi.BreakRow })
        assertTrue(rows.last() is ScheduleRowUi.PendingSportRow)
    }

    @Test
    fun `summary counts official lessons only`() {
        val days = build(
            day(TODAY, lesson(1, "08:20", "09:50"), lesson(2, "10:00", "11:30")).copy(
                pendingSport = listOf(pending(TODAY, "17:00", prediction = true))
            ),
            ScheduleDisplayDay(TODAY.plus(1, DateTimeUnit.DAY), null, listOf(pending(TODAY.plus(1, DateTimeUnit.DAY), "12:00", prediction = false))),
            day(TODAY.plus(2, DateTimeUnit.DAY))
        )

        assertEquals(
            listOf(ScheduleDaySummary.Lessons(2), ScheduleDaySummary.AutoSignOnly, ScheduleDaySummary.NoLessons),
            days.map { it.summary }
        )
        assertEquals(listOf(ScheduleRowUi.NoLessons), days[2].rows)
    }

    @Test
    fun `changed lessons are marked by pair id`() {
        val rows = rowsOf(
            day(TODAY, lesson(1, "08:20", "09:50"), lesson(2, "10:00", "11:30")).copy(changedPairIds = setOf(2L))
        )

        assertEquals(listOf(false, true), rows.map { (it as ScheduleRowUi.LessonRow).changed })
    }

    @Test
    fun `past today and future flags and the title follow the academic date`() {
        val days = build(day(TODAY.minus(1, DateTimeUnit.DAY)), day(TODAY), day(TODAY.plus(1, DateTimeUnit.DAY)))

        assertEquals(listOf(true, false, false), days.map { it.isPast })
        assertEquals(listOf(false, true, false), days.map { it.isToday })
        assertEquals(ScheduleDayTitle(DayOfWeek.TUESDAY, TODAY), days[1].title)
    }

    @Test
    fun `flags flip exactly at midnight`() {
        val days = listOf(day(TODAY), day(TODAY.plus(1, DateTimeUnit.DAY)))

        val beforeMidnight = buildScheduleListUi(days, TODAY.atTime(23, 59, 59, 999_999_999), ZONE)
        val atMidnight = buildScheduleListUi(days, TODAY.plus(1, DateTimeUnit.DAY).atTime(0, 0), ZONE)

        assertEquals(listOf(true, false), beforeMidnight.map { it.isToday })
        assertEquals(listOf(false, false), beforeMidnight.map { it.isPast })
        assertEquals(listOf(false, true), atMidnight.map { it.isToday })
        assertEquals(listOf(true, false), atMidnight.map { it.isPast })
    }

    @Test
    fun `lesson boundaries switch states on the exact instant`() {
        val days = listOf(day(TODAY, lesson(1, "10:00", "11:30"), lesson(2, "11:40", "13:10")))

        fun statesAt(hour: Int, minute: Int, nanosecond: Int = 0) =
            buildScheduleListUi(days, TODAY.atTime(hour, minute, 0, nanosecond), ZONE)
                .single().rows.map { (it as ScheduleRowUi.LessonRow).state }

        assertEquals(listOf(NEXT, UPCOMING), statesAt(9, 59, 999_999_999))
        assertEquals(listOf(CURRENT, NEXT), statesAt(10, 0))
        assertEquals(listOf(CURRENT, NEXT), statesAt(11, 29, 999_999_999))
        assertEquals(listOf(COMPLETED, NEXT), statesAt(11, 30))
        assertEquals(listOf(COMPLETED, CURRENT), statesAt(11, 40))
        assertEquals(listOf(COMPLETED, COMPLETED), statesAt(13, 10))
    }

    @Test
    fun `a later instant without boundaries yields an equal model`() {
        val days = listOf(day(TODAY, lesson(1, "10:00", "11:30")))

        assertEquals(
            buildScheduleListUi(days, TODAY.atTime(8, 0), ZONE),
            buildScheduleListUi(days, TODAY.atTime(9, 59), ZONE)
        )
    }

    private fun build(vararg days: ScheduleDisplayDay) = buildScheduleListUi(days.toList(), NOW, ZONE)

    private fun rowsOf(day: ScheduleDisplayDay) = build(day).single().rows

    private fun day(date: LocalDate, vararg lessons: Lesson) = ScheduleDisplayDay(
        date = date,
        officialDay = DaySchedule(
            dayNumber = date.dayOfWeek.isoDayNumber,
            weekNumber = 1,
            date = date,
            note = null,
            lessons = lessons.toList()
        )
    )

    private fun lesson(id: Long, start: String, end: String) = Lesson(
        pairId = id,
        start = LocalTime.parse(start),
        end = LocalTime.parse(end),
        type = "Лекция",
        typeId = Lesson.TypeId(1),
        note = null,
        subjectName = "Тестовый предмет",
        subjectId = id,
        groupName = "M0000",
        flowId = id,
        flowTypeId = 2,
        teacherIsu = null,
        teacherFio = null,
        room = null,
        building = null,
        buildingId = null,
        mainBuildingId = null,
        format = "Очно",
        formatId = 1,
        zoomUrl = null,
        zoomPassword = null,
        zoomInfo = null
    )

    private fun pending(date: LocalDate, start: String, prediction: Boolean): PendingSportBooking {
        val startsAt = date.atTime(LocalTime.parse(start)).toInstant(ZONE)
        return PendingSportBooking(
            queueId = startsAt.epochSeconds,
            queueKind = PendingSportBooking.QueueKind.AUTO,
            lessonId = 101,
            sectionName = "Тестовая секция",
            start = startsAt,
            end = startsAt + 90.minutes,
            teacherFio = "Тестовый преподаватель",
            roomName = "Тестовый корпус",
            isPrediction = prediction
        )
    }

    private companion object {
        val ZONE: TimeZone = TimeZone.of("Europe/Moscow")
        val TODAY: LocalDate = LocalDate(2026, 9, 8)
        val NOW: LocalDateTime = TODAY.atTime(10, 0)
    }
}
