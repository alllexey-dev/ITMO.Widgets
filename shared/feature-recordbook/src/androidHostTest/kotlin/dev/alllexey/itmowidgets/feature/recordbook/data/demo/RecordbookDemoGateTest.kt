package dev.alllexey.itmowidgets.feature.recordbook.data.demo

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookRepositoryImpl
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import io.ktor.client.engine.mock.MockEngine
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecordbookDemoGateTest {

    private val main = TestMainDispatcher()
    private val dispatchers = main.dispatcher.let { AppDispatchers(io = it, default = it, main = it) }

    @Before fun installMain() = main.install()

    @After fun resetMain() = main.reset()

    @Test
    fun `the program, subjects and control points come from the demo set`() = runTest {
        val repository = RecordbookRepositoryImpl(unreachableMyItmoClient(), FixedAcademicTime(), FakeDemoMode(active = true), dispatchers = dispatchers)

        val program = (repository.getPrograms() as AppResult.Success).value.single()
        val current = program.periods.single { it.actual }
        val subjects = (repository.getSubjects(program.id, current.semester) as AppResult.Success).value
        val closed = (repository.getSubjects(program.id, 1) as AppResult.Success).value
        val algorithms = subjects.single { it.disciplineId == DemoStudy.ALGORITHMS.id }
        val controls = (repository.getControls(algorithms.entryId) as AppResult.Success).value

        assertEquals(3, current.semester)
        assertEquals(DemoStudy.CURRENT.map { it.id }, subjects.map { it.disciplineId })
        assertTrue(closed.all { !it.rate.isNullOrBlank() })
        assertTrue(controls.any { it.parentId != null })
        assertEquals(algorithms.score!!, controls.filter { it.parentId == null }.sumOf { it.score ?: 0.0 }, 0.001)
    }

    /** MyItmoApi 2.x whose every request, token read included, fails the test. */
    private fun unreachableMyItmoClient(): MyItmoClient = MyItmoClientFactory.create(
        storage = object : TokenStorage {
            override suspend fun read(): TokenSet? = throw AssertionError("The demo recordbook read the ITMO session")

            override suspend fun write(tokens: TokenSet?) = throw AssertionError("The demo recordbook wrote the ITMO session")
        },
        engine = MockEngine { request -> throw AssertionError("The demo recordbook asked ${request.url.host}${request.url.encodedPath}") },
        clock = Clock.System
    )
}
