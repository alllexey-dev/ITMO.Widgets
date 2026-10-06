package dev.alllexey.itmowidgets.feature.schedule.domain.home

import dev.alllexey.itmowidgets.core.home.HomeLessonState
import dev.alllexey.itmowidgets.core.home.HomeScheduleRow
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import dev.alllexey.itmowidgets.feature.schedule.plusMinutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

class HomeScheduleSelectorTest {

    private val selector = HomeScheduleSelector()

    @Test
    fun theLessonInProgressIsCurrentAndTheOnesAfterItUpcoming() {
        val card = selector.select(
            days = listOf(day(TODAY, lesson(1, "09:30"), lesson(2, "11:20"), lesson(3, "13:30"))),
            pending = emptyList(),
            now = at("10:00"), timeZone = ZONE
        )

        assertEquals(TODAY, card.date)
        assertFalse(card.tomorrow)
        assertEquals(0, card.completed)
        assertEquals(
            listOf(HomeLessonState.CURRENT, HomeLessonState.UPCOMING, HomeLessonState.UPCOMING),
            card.rows.map { (it as HomeScheduleRow.Lesson).state }
        )
        // 30 of 90 minutes have passed; only the current lesson reports progress.
        assertEquals(1f / 3, (card.rows.first() as HomeScheduleRow.Lesson).progress!!, 0.01f)
        assertEquals(listOf(null, null), card.rows.drop(1).map { (it as HomeScheduleRow.Lesson).progress })
    }

    @Test
    fun duringABreakTheNextLessonIsMarkedNextAndFinishedOnesAreOnlyCounted() {
        val card = selector.select(
            days = listOf(day(TODAY, lesson(1, "09:30"), lesson(2, "11:20"))),
            pending = emptyList(),
            now = at("11:05"), timeZone = ZONE
        )

        assertEquals(1, card.completed)
        val row = card.rows.single() as HomeScheduleRow.Lesson
        assertEquals(2L, row.args.pairId)
        assertEquals(HomeLessonState.NEXT, row.state)
        assertEquals(null, row.progress)
    }

    @Test
    fun futureBookingsJoinTheTimelineByTimeAndDuplicatesCollapse() {
        val card = selector.select(
            days = listOf(day(TODAY, lesson(1, "09:30"), lesson(2, "13:30"))),
            pending = listOf(booking(7, "12:00"), booking(7, "12:00"), booking(8, "08:00")),
            now = at("10:00"), timeZone = ZONE
        )

        assertEquals(listOf("lesson 1", "pending 7", "lesson 2"), card.rows.map(::label))
        assertTrue((card.rows[1] as HomeScheduleRow.PendingSport).predicted)
    }

    @Test
    fun onceTodayIsOverTomorrowTakesTheCard() {
        val card = selector.select(
            days = listOf(day(TODAY, lesson(1, "09:30")), day(TODAY.plus(1, DateTimeUnit.DAY), lesson(5, "09:30"), lesson(6, "11:20"))),
            pending = listOf(booking(9, "16:00", date = TODAY.plus(1, DateTimeUnit.DAY))),
            now = at("18:00"), timeZone = ZONE
        )

        assertTrue(card.tomorrow)
        assertEquals(TODAY.plus(1, DateTimeUnit.DAY), card.date)
        assertEquals(1, card.completed)
        assertEquals(listOf("lesson 5", "lesson 6", "pending 9"), card.rows.map(::label))
        assertEquals(HomeLessonState.NEXT, (card.rows.first() as HomeScheduleRow.Lesson).state)
    }

    @Test
    fun aFreeDayWithoutLessonsTomorrowIsAnEmptyCard() {
        val card = selector.select(days = emptyList(), pending = emptyList(), now = at("10:00"), timeZone = ZONE)

        assertFalse(card.tomorrow)
        assertTrue(card.rows.isEmpty())
        assertEquals(0, card.completed)
    }

    @Test
    fun aFinishedDayWithoutTomorrowKeepsTheCount() {
        val card = selector.select(
            days = listOf(day(TODAY, lesson(1, "09:30"), lesson(2, "11:20"))),
            pending = emptyList(),
            now = at("20:00"), timeZone = ZONE
        )

        assertEquals(TODAY, card.date)
        assertTrue(card.rows.isEmpty())
        assertEquals(2, card.completed)
    }

    @Test
    fun aBookingAloneTodayStillFillsTheCardWithoutAFocusLesson() {
        val card = selector.select(days = emptyList(), pending = listOf(booking(1, "16:00")), now = at("10:00"), timeZone = ZONE)

        assertEquals(listOf("pending 1"), card.rows.map(::label))
    }

    private fun label(row: HomeScheduleRow) = when (row) {
        is HomeScheduleRow.Lesson -> "lesson ${row.args.pairId}"
        is HomeScheduleRow.PendingSport -> "pending ${row.args.lessonId - 100}"
    }

    private fun at(time: String): Instant = LocalDateTime(TODAY, LocalTime.parse(time)).toInstant(ZONE)

    private fun day(date: LocalDate, vararg lessons: Lesson) =
        DaySchedule(date.dayOfWeek.isoDayNumber, 1, date, null, lessons.toList())

    private fun lesson(pairId: Long, start: String) = Lesson(
        pairId = pairId, start = LocalTime.parse(start), end = LocalTime.parse(start).plusMinutes(90), type = "Лекция",
        typeId = Lesson.TypeId(1), note = null, subjectName = "Предмет $pairId", subjectId = pairId, groupName = "M3100",
        flowId = 100 + pairId, flowTypeId = 2, teacherIsu = 300001, teacherFio = "Преподаватель", room = Room("1506"),
        building = Building("Кронверкский проспект, 49"), buildingId = 13, mainBuildingId = 13, format = "Очный", formatId = 1,
        zoomUrl = null, zoomPassword = null, zoomInfo = null
    )

    private fun booking(id: Long, start: String, date: LocalDate = TODAY): PendingSportBooking {
        val startsAt = LocalDateTime(date, LocalTime.parse(start)).toInstant(ZONE)
        return PendingSportBooking(
            queueId = id, queueKind = PendingSportBooking.QueueKind.AUTO, lessonId = 100 + id,
            sectionName = "Бассейн", start = startsAt,
            end = startsAt + 90.minutes,
            teacherFio = "Тренер", roomName = "Бассейн", isPrediction = true
        )
    }

    private companion object {
        val TODAY: LocalDate = LocalDate(2026, 9, 7)
        val ZONE: TimeZone = UtcOffset(hours = 3).asTimeZone()
    }
}
