package dev.alllexey.itmowidgets.feature.schedule.domain

import dev.alllexey.itmowidgets.core.schedule.TeacherNowLesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import dev.alllexey.itmowidgets.feature.schedule.plusMinutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalTime

class TeacherNowTest {

    @Test
    fun theRunningLessonWinsOverTheNextOne() {
        val now = teacherNow(listOf(lesson(1, LocalTime(11, 40), "Физика"), lesson(2, LocalTime(10, 0), "Алгебра")), LocalTime(10, 30))

        assertEquals(TeacherNowLesson(LocalTime(10, 0), LocalTime(11, 30), "Алгебра", 1, "1506", "Кронверкский пр., 49", false, listOf("M3100"), isNow = true), now)
    }

    @Test
    fun betweenLessonsTheNextOneComesNotYetRunning() {
        val now = teacherNow(listOf(lesson(1, LocalTime(8, 20)), lesson(2, LocalTime(13, 30), "Алгебра")), LocalTime(11, 0))

        assertEquals("Алгебра", now?.subject)
        assertEquals(false, now?.isNow)
    }

    @Test
    fun aLessonEndsAtItsEndTimeAndNothingIsLeftAfterTheLast() {
        assertNull(teacherNow(listOf(lesson(1, LocalTime(8, 20))), LocalTime(9, 50)))
        assertNull(teacherNow(emptyList(), LocalTime(9, 0)))
    }

    @Test
    fun parallelGroupsInOneRoomMergeAnotherRoomStaysApart() {
        val start = LocalTime(10, 0)
        val lessons = listOf(
            lesson(1, start, group = "M3100"),
            lesson(2, start, group = "M3101"),
            lesson(3, start, group = "M3100"),
            lesson(4, start, group = "M3102", room = Room("2202")),
        )

        assertEquals(listOf("M3100", "M3101"), teacherNow(lessons, LocalTime(10, 5))?.groups)
    }

    @Test
    fun roomBookingsAreNotTeachingAndOnlineLessonsHaveNoPlace() {
        val booking = lesson(1, LocalTime(10, 0), flowTypeId = 5)
        val online = lesson(2, LocalTime(11, 40), room = null, building = null, formatId = 3)

        val now = teacherNow(listOf(booking, online), LocalTime(10, 30))

        assertEquals(LocalTime(11, 40), now?.start)
        assertEquals(true, now?.online)
        assertNull(now?.room)
        assertEquals(1, now?.typeId)
    }

    private fun lesson(
        pairId: Long,
        start: LocalTime,
        subject: String = "Физика",
        group: String = "M3100",
        room: Room? = Room("1506"),
        building: Building? = Building("Кронверкский пр., 49"),
        flowTypeId: Int = 2,
        formatId: Int = 1,
    ) = Lesson(
        pairId = pairId, start = start, end = start.plusMinutes(90), type = "Лекция", typeId = Lesson.TypeId(1),
        note = null, subjectName = subject, subjectId = 10, groupName = group, flowId = pairId * 100,
        flowTypeId = flowTypeId, teacherIsu = 300001, teacherFio = "Тестовый преподаватель", room = room,
        building = building, buildingId = 13, mainBuildingId = 13, format = "Очный", formatId = formatId,
        zoomUrl = null, zoomPassword = null, zoomInfo = null,
    )
}
