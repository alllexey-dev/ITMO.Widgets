package dev.alllexey.itmowidgets.feature.schedule.data

import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.feature.schedule.data.remote.scheduleMyItmoClient
import io.ktor.client.request.HttpRequestData
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TeacherNowGatewayImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val requests = CopyOnWriteArrayList<HttpRequestData>()
    private var answer = 200 to """{"code":0,"data":[],"message":null}"""
    private val myItmo = scheduleMyItmoClient { request -> requests += request; answer }

    @Test
    fun `asks the teacher timetable for today only and picks the running lesson`() = runTest {
        answer = 200 to """{"code":0,"message":null,"data":[{"day_number":1,"week_number":9,"date":"$TODAY","lessons":[
            ${lesson("10:00", "11:30", "M3100")}, ${lesson("10:00", "11:30", "M3101")}, ${lesson("13:30", "15:00", "M3102")}]}]}"""

        val result = gateway().lessonOf(TEACHER) as AppResult.Success

        val request = requests.single()
        assertEquals("/api/schedule/teacher/$TEACHER", request.url.encodedPath)
        assertEquals("$TODAY", request.url.parameters["date_start"])
        assertEquals("$TODAY", request.url.parameters["date_end"])
        assertEquals(LocalTime(10, 0), result.value?.start)
        assertEquals(listOf("M3100", "M3101"), result.value?.groups)
        assertEquals(true, result.value?.isNow)
    }

    @Test
    fun `someone who teaches nothing today is null, a failure is a failure`() = runTest {
        answer = 200 to """{"code":0,"message":null,"data":[{"day_number":1,"week_number":9,"date":"$TODAY","lessons":[]}]}"""
        assertNull((gateway().lessonOf(TEACHER) as AppResult.Success).value)

        answer = 500 to """{"code":500,"data":null,"message":"Synthetic failure"}"""
        assertTrue(gateway().lessonOf(TEACHER) is AppResult.Failure)
    }

    @Test
    fun `the demo answers from its own week without a request`() = runTest {
        val result = gateway(FakeDemoMode(active = true)).lessonOf(DemoStudy.ALGORITHMS.teacher.isu)

        assertTrue(result is AppResult.Success)
        assertTrue(requests.isEmpty())
    }

    private fun gateway(demo: DemoMode = noDemo()) =
        TeacherNowGatewayImpl(myItmo, FixedAcademicTime(TODAY.atTime(10, 30)), demo, blockingIoAppDispatchers(mainDispatcherRule.dispatcher))

    private fun lesson(start: String, end: String, group: String) =
        """{"pair_id":1,"subject":"Алгебра","subject_id":10,"time_start":"$start","time_end":"$end","teacher_id":$TEACHER,
            "room":"1506","building":"Кронверкский пр., 49","work_type_id":1,"group":"$group","flow_type_id":2,"flow_id":7,"format_id":1}"""

    private companion object {
        val TODAY = LocalDate(2026, 10, 19)
        const val TEACHER = 300001
    }
}
