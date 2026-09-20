package dev.alllexey.itmowidgets.feature.schedule.domain.home

import dev.alllexey.itmowidgets.core.home.HomeLessonState
import dev.alllexey.itmowidgets.core.home.HomeScheduleRow
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScheduleSelectorTest {

    private val selector = HomeScheduleSelector()

    @Test
    fun `the lesson in progress is current and the ones after it upcoming`() {
        val card = selector.select(
            days = listOf(day(TODAY, lesson(1, "09:30"), lesson(2, "11:20"), lesson(3, "13:30"))),
            pending = emptyList(),
            now = at("10:00")
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
    fun `during a break the next lesson is marked next and finished ones are only counted`() {
        val card = selector.select(
            days = listOf(day(TODAY, lesson(1, "09:30"), lesson(2, "11:20"))),
            pending = emptyList(),
            now = at("11:05")
        )

        assertEquals(1, card.completed)
        val row = card.rows.single() as HomeScheduleRow.Lesson
        assertEquals(2L, row.args.pairId)
        assertEquals(HomeLessonState.NEXT, row.state)
        assertEquals(null, row.progress)
    }

    @Test
    fun `future bookings join the timeline by time and duplicates collapse`() {
        val card = selector.select(
            days = listOf(day(TODAY, lesson(1, "09:30"), lesson(2, "13:30"))),
            pending = listOf(booking(7, "12:00"), booking(7, "12:00"), booking(8, "08:00")),
            now = at("10:00")
        )

        assertEquals(listOf("lesson 1", "pending 7", "lesson 2"), card.rows.map(::label))
        assertTrue((card.rows[1] as HomeScheduleRow.PendingSport).predicted)
    }

    @Test
    fun `once today is over tomorrow takes the card`() {
        val card = selector.select(
            days = listOf(day(TODAY, lesson(1, "09:30")), day(TODAY.plusDays(1), lesson(5, "09:30"), lesson(6, "11:20"))),
            pending = listOf(booking(9, "16:00", date = TODAY.plusDays(1))),
            now = at("18:00")
        )

        assertTrue(card.tomorrow)
        assertEquals(TODAY.plusDays(1), card.date)
        assertEquals(1, card.completed)
        assertEquals(listOf("lesson 5", "lesson 6", "pending 9"), card.rows.map(::label))
        assertEquals(HomeLessonState.NEXT, (card.rows.first() as HomeScheduleRow.Lesson).state)
    }

    @Test
    fun `a free day without lessons tomorrow is an empty card`() {
        val card = selector.select(days = emptyList(), pending = emptyList(), now = at("10:00"))

        assertFalse(card.tomorrow)
        assertTrue(card.rows.isEmpty())
        assertEquals(0, card.completed)
    }

    @Test
    fun `a finished day without tomorrow keeps the count`() {
        val card = selector.select(
            days = listOf(day(TODAY, lesson(1, "09:30"), lesson(2, "11:20"))),
            pending = emptyList(),
            now = at("20:00")
        )

        assertEquals(TODAY, card.date)
        assertTrue(card.rows.isEmpty())
        assertEquals(2, card.completed)
    }

    @Test
    fun `a booking alone today still fills the card without a focus lesson`() {
        val card = selector.select(days = emptyList(), pending = listOf(booking(1, "16:00")), now = at("10:00"))

        assertEquals(listOf("pending 1"), card.rows.map(::label))
    }

    private fun label(row: HomeScheduleRow) = when (row) {
        is HomeScheduleRow.Lesson -> "lesson ${row.args.pairId}"
        is HomeScheduleRow.PendingSport -> "pending ${row.args.lessonId - 100}"
    }

    private fun at(time: String): OffsetDateTime = OffsetDateTime.of(TODAY, LocalTime.parse(time), OFFSET)

    private fun day(date: LocalDate, vararg lessons: Lesson) =
        DaySchedule(date.dayOfWeek.value, 1, date, null, lessons.toList())

    private fun lesson(pairId: Long, start: String) = Lesson(
        pairId = pairId, start = LocalTime.parse(start), end = LocalTime.parse(start).plusMinutes(90), type = "Лекция",
        typeId = Lesson.TypeId(1), note = null, subjectName = "Предмет $pairId", subjectId = pairId, groupName = "M3100",
        flowId = 100 + pairId, flowTypeId = 2, teacherIsu = 300001, teacherFio = "Преподаватель", room = Room("1506"),
        building = Building("Кронверкский проспект, 49"), buildingId = 13, mainBuildingId = 13, format = "Очный", formatId = 1,
        zoomUrl = null, zoomPassword = null, zoomInfo = null
    )

    private fun booking(id: Long, start: String, date: LocalDate = TODAY): PendingSportBooking {
        val startsAt = OffsetDateTime.of(date, LocalTime.parse(start), OFFSET)
        return PendingSportBooking(
            queueId = id, queueKind = PendingSportBooking.QueueKind.AUTO, lessonId = 100 + id,
            sectionName = "Бассейн", start = startsAt, end = startsAt.plusMinutes(90),
            teacherFio = "Тренер", roomName = "Бассейн", isPrediction = true
        )
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 9, 7)
        val OFFSET: ZoneOffset = ZoneOffset.ofHours(3)
    }
}
