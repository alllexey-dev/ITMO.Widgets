package dev.alllexey.itmowidgets.feature.schedule.ui

import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleDisplayDay
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleItem.LessonState
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleItem.LessonState.COMPLETED
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleItem.LessonState.CURRENT
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleItem.LessonState.NEXT
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleItem.LessonState.UPCOMING
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleTimelineResolverTest {

    @Test
    fun `resolves completed current next and later lessons in one snapshot`() {
        val days = listOf(
            day(
                TODAY,
                lesson(1, "08:00", "09:30"),
                lesson(2, "09:45", "11:15"),
                lesson(3, "11:30", "13:00"),
                lesson(4, "13:30", "15:00")
            )
        )

        assertEquals(
            listOf(listOf(COMPLETED, CURRENT, NEXT, UPCOMING)),
            resolveScheduleTimeline(days, NOW)
        )
    }

    @Test
    fun `next uses the earliest full datetime without reordering unsorted days or lessons`() {
        val days = listOf(
            day(TODAY.plusDays(1), lesson(1, "08:00", "09:30"), lesson(2, "12:00", "13:30")),
            day(TODAY, lesson(3, "16:00", "17:30"), lesson(4, "13:00", "14:30")),
            day(TODAY.minusDays(1), lesson(5, "19:00", "20:30"))
        )

        assertEquals(
            listOf(listOf(UPCOMING, UPCOMING), listOf(UPCOMING, NEXT), listOf(COMPLETED)),
            resolveScheduleTimeline(days, NOW)
        )
    }

    @Test
    fun `exact start is current while exact end is completed and a later lesson remains next`() {
        val days = listOf(
            day(
                TODAY,
                lesson(1, "09:00", "10:00"),
                lesson(2, "10:00", "11:00"),
                lesson(3, "11:00", "12:00")
            )
        )

        assertEquals(
            listOf(listOf(COMPLETED, CURRENT, NEXT)),
            resolveScheduleTimeline(days, NOW)
        )
        assertEquals(
            listOf(listOf(COMPLETED, COMPLETED, CURRENT)),
            resolveScheduleTimeline(days, NOW.plusHours(1))
        )
        assertEquals(
            listOf(listOf(COMPLETED, COMPLETED, COMPLETED)),
            resolveScheduleTimeline(days, NOW.plusHours(2))
        )
    }

    @Test
    fun `there is no next lesson when only completed and current lessons remain`() {
        val days = listOf(
            day(TODAY.minusDays(1), lesson(1, "18:00", "19:00")),
            day(TODAY, lesson(2, "09:30", "11:00"), lesson(3, "08:00", "09:00"))
        )

        assertEquals(
            listOf(listOf(COMPLETED), listOf(CURRENT, COMPLETED)),
            resolveScheduleTimeline(days, NOW)
        )
    }

    @Test
    fun `duplicate lesson ids cannot mark more than one next position`() {
        val days = listOf(
            day(TODAY, lesson(7, "15:00", "16:00"), lesson(7, "11:00", "12:00")),
            day(TODAY.plusDays(1), lesson(7, "08:00", "09:00"))
        )

        assertEquals(
            listOf(listOf(UPCOMING, NEXT), listOf(UPCOMING)),
            resolveScheduleTimeline(days, NOW)
        )
    }

    @Test
    fun `equal lesson objects and equal days select only the first original position on a tie`() {
        val identical = lesson(1, "11:00", "12:00")
        val firstDay = day(TODAY, identical, identical.copy(), identical)
        val days = listOf(firstDay, firstDay.copy())

        assertEquals(
            listOf(listOf(NEXT, UPCOMING, UPCOMING), listOf(UPCOMING, UPCOMING, UPCOMING)),
            resolveScheduleTimeline(days, NOW)
        )
    }

    @Test
    fun `simultaneous distinct lessons break next ties by original day then lesson index`() {
        val days = listOf(
            day(TODAY, lesson(1, "13:00", "14:00"), lesson(2, "11:00", "12:00"), lesson(3, "11:00", "12:30")),
            day(TODAY, lesson(4, "11:00", "12:00"))
        )

        assertEquals(
            listOf(listOf(UPCOMING, NEXT, UPCOMING), listOf(UPCOMING)),
            resolveScheduleTimeline(days, NOW)
        )
    }

    @Test
    fun `earlier pending sport never steals official next and pending only dates keep empty indices`() {
        val days = listOf(
            ScheduleDisplayDay(
                date = TODAY,
                officialDay = null,
                pendingSport = listOf(pendingSport(TODAY, "10:30"))
            ),
            day(TODAY.plusDays(1), lesson(1, "12:00", "13:30")).copy(
                pendingSport = listOf(pendingSport(TODAY.plusDays(1), "11:00"))
            ),
            day(TODAY.plusDays(2))
        )

        assertEquals(
            listOf(emptyList(), listOf(NEXT), emptyList()),
            resolveScheduleTimeline(days, NOW)
        )
    }

    @Test
    fun `empty schedule and days without official lessons preserve empty result shapes`() {
        assertEquals(emptyList<List<LessonState>>(), resolveScheduleTimeline(emptyList(), NOW))
        assertEquals(
            listOf(emptyList<LessonState>(), emptyList()),
            resolveScheduleTimeline(listOf(day(TODAY), ScheduleDisplayDay(TODAY.plusDays(1), null)), NOW)
        )
    }

    @Test
    fun `recomputing after an earlier day arrives moves next away from the existing day`() {
        val existingDay = day(TODAY.plusDays(1), lesson(1, "08:00", "09:30"))
        assertEquals(listOf(listOf(NEXT)), resolveScheduleTimeline(listOf(existingDay), NOW))

        val earlierDay = day(TODAY, lesson(2, "11:00", "12:30"))
        assertEquals(
            listOf(listOf(UPCOMING), listOf(NEXT)),
            resolveScheduleTimeline(listOf(existingDay, earlierDay), NOW)
        )
    }

    private fun day(date: LocalDate, vararg lessons: Lesson) = ScheduleDisplayDay(
        date = date,
        officialDay = DaySchedule(
            dayNumber = date.dayOfWeek.value,
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

    private fun pendingSport(date: LocalDate, start: String): PendingSportBooking {
        val startsAt = date.atTime(LocalTime.parse(start)).atOffset(ZoneOffset.ofHours(3))
        return PendingSportBooking(
            queueId = 1,
            queueKind = PendingSportBooking.QueueKind.AUTO,
            lessonId = 101,
            sectionName = "Тестовая секция",
            start = startsAt,
            end = startsAt.plusHours(1),
            teacherFio = "Тестовый преподаватель",
            roomName = "Тестовый корпус",
            isPrediction = true
        )
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 9, 8)
        val NOW: LocalDateTime = TODAY.atTime(10, 0)
    }
}
