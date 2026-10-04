package dev.alllexey.itmowidgets.feature.schedule.data.remote

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.myItmoStub
import dev.alllexey.itmowidgets.core.testing.noDemo
import java.lang.reflect.Proxy
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRemoteDataSourceImplTest {
    private val start = LocalDate.of(2026, 9, 7)
    private val calls = mutableListOf<String>()
    private val backend = Proxy.newProxyInstance(ItmoWidgetsApi::class.java.classLoader, arrayOf(ItmoWidgetsApi::class.java)) { _, method, _ ->
        calls += method.name
        ApiResponse.success("OK")
    } as ItmoWidgetsApi
    private val myItmo = myItmoStub { """{"code":0,"data":[],"message":null}""" }.api

    @Test
    fun `the own schedule reaches Backend only with the opt-in`() = runTest {
        val gate = FakeBackendGate(optedIn = false)
        val remote = ScheduleRemoteDataSourceImpl(gate, myItmo, backend, FixedAcademicTime(), noDemo())

        assertTrue(remote.getSchedule(null, start, start.plusDays(6)).isEmpty())
        assertTrue(calls.isEmpty())

        gate.optedIn.value = true
        remote.getSchedule(null, start, start.plusDays(6))
        assertEquals(listOf("syncLessons"), calls)
    }
}
