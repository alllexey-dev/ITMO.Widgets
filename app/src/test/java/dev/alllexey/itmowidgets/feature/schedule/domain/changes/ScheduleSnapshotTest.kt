package dev.alllexey.itmowidgets.feature.schedule.domain.changes

import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleSnapshotTest {

    @Test
    fun `sport and room bookings are left out`() {
        val snapshot = listOf(day(START, lesson(1), lesson(2, flowTypeId = 3), lesson(3, flowTypeId = 5)))
            .academicSnapshot(START, END)

        assertEquals(listOf(1L), snapshot.lessons.map { it.pairId })
    }

    @Test
    fun `a repeated pair id is kept once at its earliest slot`() {
        val snapshot = listOf(
            day(START.plusDays(1), lesson(1, start = LocalTime.of(8, 20))),
            day(START, lesson(1, start = LocalTime.of(13, 30)), lesson(1, start = LocalTime.of(10, 0)))
        ).academicSnapshot(START, END)

        val kept = snapshot.lessons.single()
        assertEquals(START, kept.date)
        assertEquals(LocalTime.of(10, 0), kept.start)
    }

    @Test
    fun `days outside the window are left out and both ends are kept`() {
        val snapshot = listOf(
            day(START.minusDays(1), lesson(1)),
            day(START, lesson(2)),
            day(END, lesson(3)),
            day(END.plusDays(1), lesson(4))
        ).academicSnapshot(START, END)

        assertEquals(listOf(2L, 3L), snapshot.lessons.map { it.pairId })
        assertEquals(START, snapshot.start)
        assertEquals(END, snapshot.end)
    }

    @Test
    fun `empty room building and flow name become null`() {
        val lesson = listOf(day(START, lesson(1, room = Room(" "), building = Building(""), groupName = "  ")))
            .academicSnapshot(START, END).lessons.single()

        assertNull(lesson.room)
        assertNull(lesson.building)
        assertNull(lesson.flowName)
    }

    @Test
    fun `the lesson fields are carried`() {
        val lesson = listOf(day(START, lesson(7))).academicSnapshot(START, END).lessons.single()

        assertEquals(
            SnapshotLesson(
                pairId = 7, date = START, start = LocalTime.of(8, 20), end = LocalTime.of(9, 50), subjectId = 70,
                subjectName = "Физика", typeId = 1, flowId = 700, flowName = "ФИЗ ПИИКТ 3.2", teacherIsu = 300001,
                teacherName = "Тестовый преподаватель", room = "1506", building = "Кронверкский проспект, 49",
                formatId = 1, format = "Очный"
            ),
            lesson
        )
    }

    private fun day(date: LocalDate, vararg lessons: Lesson) = DaySchedule(date.dayOfWeek.value, 1, date, null, lessons.toList())

    private fun lesson(
        pairId: Long,
        start: LocalTime = LocalTime.of(8, 20),
        flowTypeId: Int = ACADEMIC_FLOW,
        room: Room? = Room("1506"),
        building: Building? = Building("Кронверкский проспект, 49"),
        groupName: String = "ФИЗ ПИИКТ 3.2"
    ) = Lesson(
        pairId = pairId, start = start, end = start.plusMinutes(90), type = "Лекция", typeId = Lesson.TypeId(1),
        note = null, subjectName = "Физика", subjectId = pairId * 10, groupName = groupName, flowId = pairId * 100,
        flowTypeId = flowTypeId, teacherIsu = 300001, teacherFio = "Тестовый преподаватель", room = room,
        building = building, buildingId = 13, mainBuildingId = 13, format = "Очный", formatId = 1,
        zoomUrl = null, zoomPassword = null, zoomInfo = null
    )

    private companion object {
        val START: LocalDate = LocalDate.of(2026, 9, 7)
        val END: LocalDate = LocalDate.of(2026, 9, 14)
    }
}
