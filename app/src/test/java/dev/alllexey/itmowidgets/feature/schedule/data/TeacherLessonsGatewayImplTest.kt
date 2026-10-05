package dev.alllexey.itmowidgets.feature.schedule.data

import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.blockingIoAppDispatchers
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.core.testing.myItmoResponses
import dev.alllexey.itmowidgets.feature.schedule.domain.StudyWeeks
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TeacherLessonsGatewayImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** The fake network holds a request on a blocked thread; see [blockingIoAppDispatchers]. */
    private val dispatchers = blockingIoAppDispatchers(mainDispatcherRule.dispatcher)

    @get:Rule val temporary = TemporaryFolder()
    private val folder by lazy { temporary.newFolder() }
    private val requests = CopyOnWriteArrayList<ClosedRange<LocalDate>>()
    private val days = ConcurrentHashMap<LocalDate, List<String>>()
    private val failing = ConcurrentHashMap.newKeySet<LocalDate>()
    /** A week with a gate answers only once the test opens it. */
    private val gates = ConcurrentHashMap<LocalDate, CountDownLatch>()
    private val api = myItmoResponses { request ->
        val start = LocalDate.parse(checkNotNull(request.url.queryParameter("date_start")))
        val end = LocalDate.parse(checkNotNull(request.url.queryParameter("date_end")))
        assertEquals("/api/schedule/schedule/personal", request.url.encodedPath)
        requests += start..end
        gates[start]?.let { check(it.await(WAIT_SECONDS, TimeUnit.SECONDS)) { "Week $start was never opened" } }
        if (start in failing) 500 to """{"code":500,"data":null,"message":"Synthetic failure"}"""
        else 200 to """{"code":0,"data":[${days[start].orEmpty().joinToString(",")}],"message":null}"""
    }.api
    private val file get() = File(folder, "weeks.json")

    @Test
    fun `asks for every sampled week with its bounds at once`() = runTest {
        WEEKS.forEach { gates[it.start] = CountDownLatch(1) }
        val all = launch(Dispatchers.Default) { gateway().taughtBy(TEACHER).toList() }

        // Every week is asked while none has answered, so no request waits for another.
        awaitRequests(WEEKS.size)
        gates.values.forEach(CountDownLatch::countDown)
        all.join()

        assertEquals(WEEKS.toSet(), requests.toSet())
        assertEquals(WEEKS.size, requests.size)
    }

    @Test
    fun `keeps only academic lessons of the teacher with unique subjects from newer weeks first`() = runTest {
        days[WEEKS[0].start] = listOf(
            day("2026-10-12", lesson(11, "Старое", TEACHER)),
            day("2026-10-14",
                lesson(12, "Новое", TEACHER),
                lesson(13, "Физкультура", TEACHER, flowType = 3),
                lesson(14, "Другой преподаватель", 200002),
                lesson(15, "Без преподавателя", null),
            ),
        )
        days[WEEKS[1].start] = listOf(day("2026-09-22", lesson(21, " Новое ", TEACHER), lesson(22, "Алгебра", TEACHER)))
        days[WEEKS[5].start] = listOf(day("2025-09-23", lesson(31, " ", TEACHER)))

        val lessons = (gateway().taughtBy(TEACHER).toList().last() as AppResult.Success).value

        assertEquals(TeacherLessons(setOf(12L, 11L, 21L, 22L, 31L), listOf("Новое", "Старое", "Алгебра")), lessons)
    }

    @Test
    fun `lessons come as the weeks answer with newer weeks first`() = runTest {
        WEEKS.forEach { gates[it.start] = CountDownLatch(1) }
        days[WEEKS.last().start] = listOf(day("2023-09-26", lesson(41, "Старое", TEACHER)))
        days[WEEKS[0].start] = listOf(day("2026-10-13", lesson(11, "Новое", TEACHER)))
        val emissions = Channel<AppResult<TeacherLessons>>(Channel.UNLIMITED)
        val collecting = launch { gateway().taughtBy(TEACHER).collect(emissions::send) }

        gates.getValue(WEEKS.last().start).countDown()
        assertEquals(AppResult.Success(TeacherLessons(setOf(41L), listOf("Старое"))), emissions.receive())
        gates.getValue(WEEKS[0].start).countDown()
        val both = AppResult.Success(TeacherLessons(setOf(11L, 41L), listOf("Новое", "Старое")))
        assertEquals(both, emissions.receive())
        gates.values.forEach(CountDownLatch::countDown)
        collecting.join()

        assertEquals(List(WEEKS.size - 2) { both }, generateSequence { emissions.tryReceive().getOrNull() }.toList())
    }

    @Test
    fun `finished weeks come from disk without a request and the current week is asked every time`() = runTest {
        days[WEEKS[1].start] = listOf(day("2026-09-22", lesson(21, "Алгебра", TEACHER)))
        days[WEEKS[0].start] = listOf(day("2026-10-13", lesson(11, "Физика", TEACHER)))
        gateway().taughtBy(TEACHER).toList()
        requests.clear()
        gates[WEEKS[0].start] = CountDownLatch(1)
        val emissions = Channel<AppResult<TeacherLessons>>(Channel.UNLIMITED)

        val collecting = launch { gateway().taughtBy(TEACHER).collect(emissions::send) }
        val cached = emissions.receive()
        gates.getValue(WEEKS[0].start).countDown()
        collecting.join()

        assertEquals(AppResult.Success(TeacherLessons(setOf(21L), listOf("Алгебра"))), cached)
        assertEquals(AppResult.Success(TeacherLessons(setOf(11L, 21L), listOf("Физика", "Алгебра"))), emissions.receive())
        assertEquals(listOf(WEEKS[0]), requests)
    }

    @Test
    fun `the disk keeps only the minimal lessons of finished weeks still sampled`() = runTest {
        TeacherWeeksFileStore(folder.toOkioPath()).write(mapOf(LocalDate(2022, 9, 19) to listOf(WeekLesson(TEACHER.toLong(), 1, "Старое"))))
        days[WEEKS[1].start] = listOf(day("2026-09-22", lesson(21, "Алгебра", TEACHER), lesson(22, "Спорт", TEACHER, flowType = 3)))
        days[WEEKS[0].start] = listOf(day("2026-10-13", lesson(11, "Физика", TEACHER)))

        gateway().taughtBy(TEACHER).toList()

        val stored = TeacherWeeksFileStore(folder.toOkioPath()).read()
        assertEquals(WEEKS.drop(1).map { it.start }.toSet(), stored.keys)
        assertEquals(listOf(WeekLesson(TEACHER.toLong(), 21, "Алгебра")), stored[WEEKS[1].start])
    }

    @Test
    fun `a corrupt or unknown file is ignored and replaced`() = runTest {
        for (content in listOf("{unreadable", """{"format":2,"weeks":{}}""", """{"format":1,"weeks":{"2026-09-22":[]}}""")) {
            file.writeText(content)
            requests.clear()
            days[WEEKS[1].start] = listOf(day("2026-09-22", lesson(21, "Алгебра", TEACHER)))

            val lessons = (gateway().taughtBy(TEACHER).toList().last() as AppResult.Success).value

            assertEquals(TeacherLessons(setOf(21L), listOf("Алгебра")), lessons)
            assertEquals(WEEKS.toSet(), requests.toSet())
            assertEquals(WEEKS.size - 1, TeacherWeeksFileStore(folder.toOkioPath()).read().size)
        }
    }

    @Test
    fun `clearing the session deletes the stored weeks`() = runTest {
        val gateway = gateway()
        gateway.taughtBy(TEACHER).toList()
        assertTrue(file.exists())
        requests.clear()

        gateway.clearSessionData()
        gateway.taughtBy(TEACHER).toList()

        assertEquals(WEEKS.toSet(), requests.toSet())
        assertEquals(WEEKS.size, requests.size)
    }

    @Test
    fun `an answer asked for the previous account is not stored for the next one`() = runTest {
        WEEKS.forEach { gates[it.start] = CountDownLatch(1) }
        days[WEEKS[1].start] = listOf(day("2026-09-22", lesson(21, "Алгебра", TEACHER)))
        val gateway = gateway()
        val previous = launch(Dispatchers.Default) { gateway.taughtBy(TEACHER).toList() }
        awaitRequests(WEEKS.size)

        gateway.clearSessionData()
        gates.values.forEach(CountDownLatch::countDown)
        previous.join()
        assertFalse(file.exists())
        requests.clear()
        gateway.taughtBy(TEACHER).toList()

        assertEquals(WEEKS.size, requests.size)
    }

    @Test
    fun `a failed week is skipped and asked again next time`() = runTest {
        failing += WEEKS[2].start
        days[WEEKS[1].start] = listOf(day("2026-09-22", lesson(21, "Алгебра", TEACHER)))

        val emissions = gateway().taughtBy(TEACHER).toList()
        requests.clear()
        gateway().taughtBy(TEACHER).toList()

        assertEquals(WEEKS.size - 1, emissions.size)
        assertEquals(AppResult.Success(TeacherLessons(setOf(21L), listOf("Алгебра"))), emissions.last())
        assertEquals(setOf(WEEKS[0], WEEKS[2]), requests.toSet())
    }

    @Test
    fun `every week failing is one failure`() = runTest {
        failing += WEEKS.map { it.start }

        val emissions = gateway().taughtBy(TEACHER).toList()

        assertTrue((emissions.single() as AppResult.Failure).error is AppError.Unknown)
        assertEquals(WEEKS.toSet(), requests.toSet())
        assertFalse(file.exists())
    }

    private fun awaitRequests(count: Int) {
        repeat(WAIT_SECONDS.toInt() * 100) { if (requests.size >= count) return; Thread.sleep(10) }
        error("Only ${requests.size} of $count weeks were asked")
    }

    @Test
    fun `the demo names the teacher's subjects from its own week`() = runTest {
        val teacher = DemoStudy.ALGORITHMS.teacher.isu

        val lessons = gateway(FakeDemoMode(active = true)).taughtBy(teacher).toList()

        val answer = (lessons.single() as AppResult.Success).value
        assertEquals(listOf(DemoStudy.ALGORITHMS.name), answer.subjects)
        assertTrue(answer.flowIds.isNotEmpty())
        assertTrue(requests.isEmpty())
    }

    private fun gateway(demo: DemoMode = noDemo()) = TeacherLessonsGatewayImpl(api, TeacherWeeksFileStore(folder.toOkioPath()), FixedAcademicTime(TODAY.atTime(12, 0)), demo, dispatchers)

    private fun day(date: String, vararg lessons: String) =
        """{"day_number":1,"week_number":1,"date":"$date","lessons":[${lessons.joinToString(",")}]}"""

    private fun lesson(flowId: Int, subject: String, teacher: Int?, flowType: Int = 2) =
        """{"pair_id":$flowId,"subject":"$subject","teacher_id":${teacher ?: "null"},"flow_type_id":$flowType,"flow_id":$flowId}"""

    private companion object {
        const val TEACHER = 100001
        const val WAIT_SECONDS = 10L
        val TODAY: LocalDate = LocalDate(2026, 10, 15)
        /** The current week 12–18 October 2026 first, then 13 finished weeks down to 25 September 2023. */
        val WEEKS = StudyWeeks.sampled(TODAY)
    }
}
