package dev.alllexey.itmowidgets.feature.schedule.data

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.core.testing.myItmoResponses
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherLessonsGatewayImplTest {
    private val requests = CopyOnWriteArrayList<ClosedRange<LocalDate>>()
    private val days = mutableMapOf<LocalDate, List<String>>()
    private val failing = mutableSetOf<LocalDate>()
    private val gateway = TeacherLessonsGatewayImpl(myItmoResponses { request ->
        val start = LocalDate.parse(checkNotNull(request.url.queryParameter("date_start")))
        val end = LocalDate.parse(checkNotNull(request.url.queryParameter("date_end")))
        assertEquals("/api/schedule/schedule/personal", request.url.encodedPath)
        requests += start..end
        if (start in failing) 500 to """{"code":500,"data":null,"message":"Synthetic failure"}"""
        else 200 to """{"code":0,"data":[${days[start].orEmpty().joinToString(",")}],"message":null}"""
    }.api, FixedTime(TODAY))

    @Test
    fun `requests the personal schedule once per study period with its bounds`() = runTest {
        gateway.taughtBy(TEACHER)

        assertEquals(PERIODS, requests)
    }

    @Test
    fun `keeps only academic lessons of the teacher with unique subjects from newer periods first`() = runTest {
        days[PERIODS[0].start] = listOf(
            day("2026-09-02", lesson(11, "Старое", TEACHER)),
            day("2026-10-01",
                lesson(12, "Новое", TEACHER),
                lesson(13, "Физкультура", TEACHER, flowType = 3),
                lesson(14, "Другой преподаватель", 200002),
                lesson(15, "Без преподавателя", null),
            ),
        )
        days[PERIODS[1].start] = listOf(day("2026-05-12", lesson(21, " Новое ", TEACHER), lesson(22, "Алгебра", TEACHER)))
        days[PERIODS[2].start] = listOf(day("2025-10-01", lesson(31, " ", TEACHER)))

        val lessons = (gateway.taughtBy(TEACHER) as AppResult.Success).value

        assertEquals(TeacherLessons(setOf(12L, 11L, 21L, 22L, 31L), listOf("Новое", "Старое", "Алгебра")), lessons)
    }

    @Test
    fun `past periods come from memory until the session is cleared`() = runTest {
        days[PERIODS[1].start] = listOf(day("2026-05-12", lesson(21, "Алгебра", TEACHER)))
        gateway.taughtBy(TEACHER)
        requests.clear()

        val again = (gateway.taughtBy(TEACHER) as AppResult.Success).value

        assertEquals(listOf(PERIODS[0]), requests)
        assertEquals(listOf("Алгебра"), again.subjects)
        requests.clear()
        gateway.clearSessionData()
        gateway.taughtBy(TEACHER)
        assertEquals(PERIODS, requests)
    }

    @Test
    fun `a failed period is skipped and asked again next time`() = runTest {
        failing += PERIODS[2].start
        days[PERIODS[1].start] = listOf(day("2026-05-12", lesson(21, "Алгебра", TEACHER)))

        val lessons = (gateway.taughtBy(TEACHER) as AppResult.Success).value
        requests.clear()
        gateway.taughtBy(TEACHER)

        assertEquals(TeacherLessons(setOf(21L), listOf("Алгебра")), lessons)
        assertEquals(listOf(PERIODS[0], PERIODS[2]), requests)
    }

    @Test
    fun `every period failing is a failure`() = runTest {
        failing += PERIODS.map { it.start }

        val result = gateway.taughtBy(TEACHER)

        assertTrue((result as AppResult.Failure).error is AppError.Unknown)
        assertEquals(PERIODS, requests)
    }

    private fun day(date: String, vararg lessons: String) =
        """{"day_number":1,"week_number":1,"date":"$date","lessons":[${lessons.joinToString(",")}]}"""

    private fun lesson(flowId: Int, subject: String, teacher: Int?, flowType: Int = 2) =
        """{"pair_id":$flowId,"subject":"$subject","teacher_id":${teacher ?: "null"},"flow_type_id":$flowType,"flow_id":$flowId}"""

    private class FixedTime(private val today: LocalDate) : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")
        override fun today(): LocalDate = today
        override fun now(): OffsetDateTime = today.atTime(12, 0).atZone(zoneId).toOffsetDateTime()
    }

    private companion object {
        const val TEACHER = 100001
        val TODAY: LocalDate = LocalDate.of(2026, 10, 15)
        val PERIODS = listOf(
            LocalDate.of(2026, 9, 1)..TODAY,
            LocalDate.of(2026, 2, 1)..LocalDate.of(2026, 8, 31),
            LocalDate.of(2025, 9, 1)..LocalDate.of(2026, 1, 31),
            LocalDate.of(2025, 2, 1)..LocalDate.of(2025, 8, 31),
            LocalDate.of(2024, 9, 1)..LocalDate.of(2025, 1, 31),
            LocalDate.of(2024, 2, 1)..LocalDate.of(2024, 8, 31),
            LocalDate.of(2023, 9, 1)..LocalDate.of(2024, 1, 31),
            LocalDate.of(2023, 2, 1)..LocalDate.of(2023, 8, 31),
        )
    }
}
