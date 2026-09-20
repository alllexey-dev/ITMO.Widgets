package dev.alllexey.itmowidgets.feature.schedule.data

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.schedule.subjectsIn
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubjectLessonsGatewayImplTest {
    private val repository = FakeScheduleRepository()
    private val gateway = SubjectLessonsGatewayImpl(repository)
    private val monday = LocalDate.of(2026, 9, 7)

    @Test
    fun `academic lessons come out flat, dated and ordered, without sport or bookings`() = runTest {
        repository.days.value = listOf(
            DaySchedule(2, 1, monday.plusDays(1), null, listOf(lesson(3, "13:30", subjectId = 20, flowTypeId = 2))),
            DaySchedule(1, 1, monday, null, listOf(
                lesson(2, "11:30", subjectId = 10, flowTypeId = 2),
                lesson(9, "16:00", subjectId = 99, flowTypeId = 3, typeId = 11),
                lesson(8, "18:00", subjectId = 98, flowTypeId = 5),
                lesson(1, "09:30", subjectId = 10, flowTypeId = 2, flowId = 500)
            ))
        )

        val lessons = gateway.observeOwnLessons(monday, monday.plusDays(7)).first()

        assertEquals(listOf(1L, 2L, 3L), lessons.map { it.pairId })
        assertEquals(listOf(monday, monday, monday.plusDays(1)), lessons.map { it.date })
        assertEquals("1506", lessons.first().room)
        assertEquals("Кронверкский проспект, 49", lessons.first().building)
        assertNull(repository.observed.single().userIsu)
    }

    @Test
    fun `subjects group every flow under the first name seen`() {
        val lessons = listOf(
            lessonSummary(subjectId = 10, name = "Физика", flowId = 1),
            lessonSummary(subjectId = 20, name = "Алгебра", flowId = 2),
            lessonSummary(subjectId = 10, name = "Физика (лекции)", flowId = 3)
        )

        assertEquals(
            listOf(ScheduleSubject(10, "Физика", setOf(1L, 3L)), ScheduleSubject(20, "Алгебра", setOf(2L))),
            subjectsIn(lessons)
        )
    }

    private fun lessonSummary(subjectId: Long, name: String, flowId: Long) = SubjectLesson(
        pairId = flowId, date = monday, start = LocalTime.of(9, 30), end = LocalTime.of(11, 0), typeId = 1, type = "Лекция",
        subjectId = subjectId, subjectName = name, flowId = flowId, teacherIsu = null, teacherFio = null,
        room = null, building = null, formatId = 1
    )

    private fun lesson(
        pairId: Long, start: String, subjectId: Long, flowTypeId: Int, flowId: Long = 100 + subjectId,
        typeId: Int = 1, name: String = "Предмет $subjectId"
    ) = Lesson(
        pairId = pairId, start = LocalTime.parse(start), end = LocalTime.parse(start).plusMinutes(90), type = "Лекция",
        typeId = Lesson.TypeId(typeId), note = null, subjectName = name, subjectId = subjectId, groupName = "M3100",
        flowId = flowId, flowTypeId = flowTypeId, teacherIsu = 300001, teacherFio = "Преподаватель", room = Room("1506"),
        building = Building("Кронверкский проспект, 49"), buildingId = 13, mainBuildingId = 13, format = "Очный", formatId = 1,
        zoomUrl = null, zoomPassword = null, zoomInfo = null
    )

    private class FakeScheduleRepository : ScheduleRepository {
        val days = MutableStateFlow<List<DaySchedule>>(emptyList())
        val observed = mutableListOf<Request>()

        data class Request(val userIsu: Int?, val start: LocalDate, val end: LocalDate)

        override fun observeScheduleForRange(userIsu: Int?, startDate: LocalDate, endDate: LocalDate): Flow<List<DaySchedule>> {
            observed += Request(userIsu, startDate, endDate)
            return days.map { list -> list.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) } }
        }

        override suspend fun refreshSchedule(userIsu: Int?, startDate: LocalDate, endDate: LocalDate): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun clearCaches() = Unit
    }
}
