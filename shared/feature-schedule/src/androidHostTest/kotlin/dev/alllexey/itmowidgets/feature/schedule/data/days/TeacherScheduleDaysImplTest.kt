package dev.alllexey.itmowidgets.feature.schedule.data.days

import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.feature.schedule.data.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.schedule.data.blockingIoAppDispatchers
import dev.alllexey.itmowidgets.feature.schedule.data.remote.scheduleMyItmoClient
import io.ktor.client.request.HttpRequestData
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TeacherScheduleDaysImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val requests = CopyOnWriteArrayList<HttpRequestData>()
    private var answer = 200 to """{"code":0,"data":[],"message":null}"""
    private val myItmo = scheduleMyItmoClient { request -> requests += request; answer }

    @Test
    fun `asks the teacher timetable and merges lessons taught together to several groups`() = runTest {
        answer = 200 to timetable(
            day(MONDAY, lesson(1, "10:00", "M3100"), lesson(2, "10:00", "M3101"), lesson(3, "13:30", "M3102"), booking()),
            day(TUESDAY),
        )
        val days = source()

        assertEquals(AppResult.Success(Unit), days.refresh(TEACHER, MONDAY, TUESDAY))

        val request = requests.single()
        assertEquals("/api/schedule/teacher/$TEACHER", request.url.encodedPath)
        assertEquals("$MONDAY", request.url.parameters["date_start"])
        assertEquals("$TUESDAY", request.url.parameters["date_end"])
        val (monday, tuesday) = days.observe(TEACHER, MONDAY, TUESDAY).first()
        assertEquals(listOf(listOf("M3100", "M3101"), listOf("M3102")), monday.lessons.map { it.groups })
        assertNull(monday.lessons.first().teacher)
        assertEquals("M3100, M3101", (monday.lessons.first().target as LessonTarget.Lesson).args.flowName)
        assertEquals(9, monday.week)
        assertTrue(tuesday.lessons.isEmpty())
    }

    @Test
    fun `a failure keeps what was fetched and a later answer replaces its range`() = runTest {
        val days = source()
        answer = 200 to timetable(day(MONDAY, lesson(1, "10:00", "M3100")))
        days.refresh(TEACHER, MONDAY, MONDAY)

        answer = 500 to """{"code":500,"data":null,"message":"Synthetic failure"}"""
        assertTrue(days.refresh(TEACHER, MONDAY, MONDAY) is AppResult.Failure)
        assertEquals(1, days.observe(TEACHER, MONDAY, MONDAY).first().single().lessons.size)

        answer = 200 to timetable(day(MONDAY))
        days.refresh(TEACHER, MONDAY, MONDAY)
        assertTrue(days.observe(TEACHER, MONDAY, MONDAY).first().single().lessons.isEmpty())
        assertTrue(days.observe(OTHER_TEACHER, MONDAY, MONDAY).first().isEmpty())
    }

    @Test
    fun `the demo answers from its own week without a request`() = runTest {
        val days = source(FakeDemoMode(active = true))

        assertEquals(AppResult.Success(Unit), days.refresh(DemoStudy.ALGORITHMS.teacher.isu, MONDAY, TUESDAY))
        assertTrue(requests.isEmpty())
    }

    private fun source(demo: DemoMode = noDemo()) = TeacherScheduleDaysImpl(
        myItmo,
        FixedAcademicTime(MONDAY.atTime(10, 30)),
        demo,
        blockingIoAppDispatchers(mainDispatcherRule.dispatcher),
    )

    private fun timetable(vararg days: String) = """{"code":0,"message":null,"data":[${days.joinToString()}]}"""

    private fun day(date: LocalDate, vararg lessons: String) =
        """{"day_number":${date.dayOfWeek.ordinal + 1},"week_number":9,"date":"$date","lessons":[${lessons.joinToString()}]}"""

    private fun lesson(pairId: Int, start: String, group: String, flowType: Int = 2) =
        """{"pair_id":$pairId,"subject":"Алгебра","subject_id":10,"time_start":"$start","time_end":"${end(start)}",
            "teacher_id":$TEACHER,"room":"1506","building":"Кронверкский пр., 49","work_type_id":1,"group":"$group",
            "flow_type_id":$flowType,"flow_id":7,"format_id":1}"""

    private fun booking() = lesson(9, "15:20", "", flowType = 5)

    private fun end(start: String) = when (start) {
        "10:00" -> "11:30"
        "13:30" -> "15:00"
        else -> "16:50"
    }

    private companion object {
        val MONDAY = LocalDate(2026, 10, 19)
        val TUESDAY = LocalDate(2026, 10, 20)
        const val TEACHER = 300001
        const val OTHER_TEACHER = 300002
    }
}
