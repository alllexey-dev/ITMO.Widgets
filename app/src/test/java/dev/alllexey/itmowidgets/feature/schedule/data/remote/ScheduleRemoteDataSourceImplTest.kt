package dev.alllexey.itmowidgets.feature.schedule.data.remote

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.myItmoStub
import dev.alllexey.itmowidgets.core.testing.noDemo
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScheduleRemoteDataSourceImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    private val start = LocalDate(2026, 9, 7)
    private val calls = mutableListOf<String>()
    private val backend = Proxy.newProxyInstance(ItmoWidgetsApi::class.java.classLoader, arrayOf(ItmoWidgetsApi::class.java)) { _, method, _ ->
        calls += method.name
        ApiResponse.success("OK")
    } as ItmoWidgetsApi
    private val myItmo = myItmoStub { """{"code":0,"data":[],"message":null}""" }.api

    @Test
    fun `the own schedule reaches Backend only with the opt-in`() = runTest {
        val gate = FakeBackendGate(optedIn = false)
        val remote = ScheduleRemoteDataSourceImpl(gate, myItmo, backend, FixedAcademicTime(), noDemo(), dispatchers)

        assertTrue(remote.getSchedule(null, start, start.plus(6, DateTimeUnit.DAY)).isEmpty())
        assertTrue(calls.isEmpty())

        gate.optedIn.value = true
        remote.getSchedule(null, start, start.plus(6, DateTimeUnit.DAY))
        assertEquals(listOf("syncLessons"), calls)
    }
}
