package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.feature.schedule.data.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.schedule.data.remote.requestedRange
import dev.alllexey.itmowidgets.feature.schedule.data.remote.scheduleMyItmoClient
import dev.alllexey.itmowidgets.feature.schedule.data.remote.unreachableScheduleMyItmoClient
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

class MyItmoOwnScheduleSourceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val requests = mutableListOf<String>()
    private var answer: Pair<Int, String> = 200 to """{"code":0,"data":[],"message":null}"""
    private val source = MyItmoOwnScheduleSource(
        scheduleMyItmoClient { request ->
            requests += request.requestedRange()
            if (requests.size == 1) {
                200 to """{"code":0,"data":[{"day_number":1,"week_number":6,"date":"2026-10-05","note":null,"lessons":[]}],"message":null}"""
            } else {
                answer
            }
        },
        FixedAcademicTime(),
        noDemo(),
        mainDispatcherRule.appDispatchers
    )

    @Test
    fun `a long range is asked in pieces of 31 days one after another`() = runTest {
        val days = source.read(LocalDate(2026, 10, 5), LocalDate(2026, 12, 10))

        assertEquals(listOf("2026-10-05..2026-11-04", "2026-11-05..2026-12-05", "2026-12-06..2026-12-10"), requests)
        assertEquals(listOf(LocalDate(2026, 10, 5)), days.map { it.date })
    }

    @Test
    fun `a failed piece fails the whole read`() = runTest {
        answer = 200 to """{"code":7,"message":"Synthetic schedule error","data":null}"""

        try {
            source.read(LocalDate(2026, 10, 5), LocalDate(2026, 12, 10))
            fail("Expected a failure")
        } catch (error: MyItmoException) {
            assertTrue(error is MyItmoException.Api)
        }
        assertEquals(2, requests.size)
    }

    /** The demo schedule is read from the demo set; My ITMO is never asked. */
    @Test
    fun `the export source reads the demo schedule`() = runTest {
        val time = FixedAcademicTime()
        val monday = time.today().minus(time.today().dayOfWeek.isoDayNumber - 1L, DateTimeUnit.DAY)
        val demo = MyItmoOwnScheduleSource(
            unreachableScheduleMyItmoClient(), time, FakeDemoMode(active = true), mainDispatcherRule.appDispatchers
        )

        val days = demo.read(monday, monday.plus(40, DateTimeUnit.DAY))

        assertEquals(41, days.size)
        assertTrue(days.sumOf { it.lessons.size } > 20)
    }
}
