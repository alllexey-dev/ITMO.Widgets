package dev.alllexey.itmowidgets.feature.schedule.domain.calendar

import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarEventsTest {

    @Test
    fun `every lesson of the answer becomes an event, sport and room bookings too`() {
        val events = CalendarEvents.from(
            listOf(day(MONDAY, lesson(1), lesson(2, flowTypeId = 3), lesson(3, flowTypeId = 5))),
            MOSCOW
        ) { null }

        assertEquals(listOf("lesson-1", "lesson-2", "lesson-3"), events.map { it.key })
    }

    @Test
    fun `a repeated lesson id is one event at its first slot`() {
        val events = CalendarEvents.from(
            listOf(
                day(MONDAY.plusDays(1), lesson(1, start = LocalTime.of(8, 20))),
                day(MONDAY, lesson(1, start = LocalTime.of(13, 30)), lesson(1, start = LocalTime.of(10, 0)))
            ),
            MOSCOW
        ) { null }

        assertEquals(Instant.parse("2026-09-07T07:00:00Z"), events.single().start)
    }

    @Test
    fun `lessons without an id are named by their slot`() {
        val events = CalendarEvents.from(
            listOf(day(MONDAY, lesson(0, start = LocalTime.of(10, 0)), lesson(-1, start = LocalTime.of(11, 40)))),
            MOSCOW
        ) { null }

        assertEquals(listOf("lesson-2026-09-07-600-0-0", "lesson-2026-09-07-700--100--10"), events.map { it.key })
    }

    @Test
    fun `times are Moscow wall times and events are in time order`() {
        val events = CalendarEvents.from(
            listOf(day(MONDAY, lesson(2, start = LocalTime.of(13, 30)), lesson(1, start = LocalTime.of(8, 20)))),
            MOSCOW
        ) { null }

        assertEquals(listOf("lesson-1", "lesson-2"), events.map { it.key })
        assertEquals(Instant.parse("2026-09-07T05:20:00Z"), events.first().start)
        assertEquals(Instant.parse("2026-09-07T06:50:00Z"), events.first().end)
    }

    @Test
    fun `title location and description come from the lesson`() {
        val event = CalendarEvents.from(listOf(day(MONDAY, lesson(1))), MOSCOW) { "Кронверкский проспект, 49, Санкт-Петербург" }
            .single()

        assertEquals("Физика", event.title)
        assertEquals("1506, Кронверкский проспект, 49, Санкт-Петербург", event.location)
        assertEquals("Лекция\nТестовый преподаватель\nФИЗ ПИИКТ 3.2", event.description)
        assertEquals("lesson-1@widgets.alllexey.dev", event.uid)
    }

    @Test
    fun `an unknown building keeps its own text and missing parts are left out`() {
        val known = CalendarEvents.from(listOf(day(MONDAY, lesson(1, room = null))), MOSCOW) { null }.single()
        val nothing = CalendarEvents.from(
            listOf(day(MONDAY, lesson(1, room = null, building = null, teacher = null, groupName = " "))),
            MOSCOW
        ) { null }.single()

        assertEquals("Кронверкский пр., 49", known.location)
        assertNull(nothing.location)
        assertEquals("Лекция", nothing.description)
    }

    private fun day(date: LocalDate, vararg lessons: Lesson) = DaySchedule(date.dayOfWeek.value, 1, date, null, lessons.toList())

    private fun lesson(
        pairId: Long,
        start: LocalTime = LocalTime.of(8, 20),
        flowTypeId: Int = 2,
        room: Room? = Room("1506"),
        building: Building? = Building("Кронверкский пр., 49"),
        teacher: String? = "Тестовый преподаватель",
        groupName: String = "ФИЗ ПИИКТ 3.2"
    ) = Lesson(
        pairId = pairId, start = start, end = start.plusMinutes(90), type = "Лекция", typeId = Lesson.TypeId(1),
        note = null, subjectName = "Физика", subjectId = pairId * 10, groupName = groupName, flowId = pairId * 100,
        flowTypeId = flowTypeId, teacherIsu = 300001, teacherFio = teacher, room = room,
        building = building, buildingId = 13, mainBuildingId = 13, format = "Очный", formatId = 1,
        zoomUrl = null, zoomPassword = null, zoomInfo = null
    )

    private companion object {
        val MONDAY: LocalDate = LocalDate.of(2026, 9, 7)
        val MOSCOW: ZoneId = ZoneId.of("Europe/Moscow")
    }
}
