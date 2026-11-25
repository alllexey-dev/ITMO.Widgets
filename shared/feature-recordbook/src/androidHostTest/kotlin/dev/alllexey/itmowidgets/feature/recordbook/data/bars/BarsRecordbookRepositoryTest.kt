package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The period's BARS subjects over the real MyItmoApi 2.x client on a MockEngine ([BarsTestServer]). */
class BarsRecordbookRepositoryTest {

    private val main = TestMainDispatcher()
    private val dispatchers = main.dispatcher.let { AppDispatchers(io = it, default = it, main = it) }

    @Before fun installMain() = main.install()

    @After fun resetMain() = main.reset()

    private val store = MemoryBarsTokens().store
    private val storage = OwnerBoundBarsStorage(store)
    private val owner = object : CurrentUserProvider {
        override suspend fun getCurrentUser() = CurrentUser(123, null, null)
    }
    private val silentLogin = object : BarsSilentLogin {
        override suspend fun authorizationCode(state: String): String? = null
    }
    private val backgroundLogin = object : BarsBackgroundLogin {
        override suspend fun renew(state: String): BarsCookieRenewal = BarsCookieRenewal.SessionEnded
    }
    private val diagnostics = RecordingDiagnostics()
    private val server = BarsTestServer()
    private val repository = BarsRenewal(silentLogin, backgroundLogin).let { renewal ->
        BarsRecordbookRepositoryImpl(
            BarsClient(
                server.library(storage, renewal), renewal, storage, owner, CountingBarsSessionListener(), noDemo(),
                dispatchers
            ),
            diagnostics
        )
    }

    @Test fun `a journal that does not map leaves the rest of the period`() = runTest {
        store.install(123, OLD_HEADER)
        server.courseProjects += 2L

        val subjects = (repository.getSubjects(PERIOD) as AppResult.Success).value

        assertEquals(listOf("Тестовый предмет 1"), subjects.map { it.name })
        assertEquals(
            listOf("WARNING:BarsRecordbook:BARS journal of plan 2 does not map; its subject keeps My ITMO"),
            diagnostics.messages
        )
    }

    @Test fun `every mapped journal is a subject`() = runTest {
        store.install(123, OLD_HEADER)

        val subjects = (repository.getSubjects(PERIOD) as AppResult.Success).value

        assertEquals(listOf("Тестовый предмет 1", "Тестовый предмет 2"), subjects.map { it.name })
        assertTrue(diagnostics.messages.isEmpty())
    }

    private companion object {
        val PERIOD = RecordbookPeriod("2026/2027", semester = 5, course = 3, actual = true)
    }
}
