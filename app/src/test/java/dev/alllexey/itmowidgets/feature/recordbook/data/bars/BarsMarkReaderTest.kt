package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.feature.recordbook.CountingBarsSessionListener
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsCheckpointMark
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Background reads of the own journals over the real MyItmoApi 2.x client on a MockEngine ([BarsTestServer]). */
class BarsMarkReaderTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    private val old = OLD_HEADER
    private val store = MemoryBarsTokens().store
    private val storage = OwnerBoundBarsStorage(store)
    private val owner = object : CurrentUserProvider {
        override suspend fun getCurrentUser() = CurrentUser(123, null, null)
    }
    private val silentLogin = object : BarsSilentLogin {
        override suspend fun authorizationCode(state: String): String? = null
    }
    private val renewals = mutableListOf<BarsCookieRenewal>()
    private val backgroundLogin = object : BarsBackgroundLogin {
        var requests = 0
        override suspend fun renew(state: String): BarsCookieRenewal {
            requests++
            return renewals.removeFirstOrNull() ?: BarsCookieRenewal.SessionEnded
        }
    }
    private val server = BarsTestServer()
    private val reader = BarsRenewal(silentLogin, backgroundLogin).let { renewal ->
        BarsMarkReader(
            BarsClient(
                server.library(storage, renewal), renewal, storage, owner, CountingBarsSessionListener(), noDemo(),
                dispatchers
            )
        )
    }

    @Test fun `the half is selected and every journal becomes a plan of own marks`() = runTest {
        store.install(123, old)
        server.year = "2026/2027"
        server.term = 1

        val read = reader.read(HALF) as BarsMarkRead.Journals

        assertEquals(0, read.skipped)
        assertEquals(listOf(1L, 2L), read.plans.map { it.planId })
        val first = read.plans.first()
        assertEquals("Тестовый предмет 1", first.name)
        assertEquals("flow", first.type)
        assertEquals("7", first.identifier)
        assertEquals(listOf(BarsCheckpointMark(10, 7.5, false)), first.marks)
        assertEquals(7.5, first.score!!, 0.0)
        assertEquals(emptyList<String>(), server.settings)
    }

    @Test fun `the period the user had is selected again afterwards`() = runTest {
        store.install(123, old)
        server.year = "2025/2026"
        server.term = 0

        assertTrue(reader.read(HALF) is BarsMarkRead.Journals)

        assertEquals(
            listOf("current_year=2026/2027", "current_term=1", "current_year=2025/2026", "current_term=0"),
            server.settings
        )
        assertEquals("2025/2026" to 0, server.year to server.term)
    }

    @Test fun `a plan the mapper rejects is skipped and the rest is read`() = runTest {
        store.install(123, old)
        server.courseProjects += 2L

        val read = reader.read(HALF) as BarsMarkRead.Journals

        assertEquals(1, read.skipped)
        assertEquals(listOf(1L), read.plans.map { it.planId })
    }

    @Test fun `without a saved session nothing is requested`() = runTest {
        assertEquals(BarsMarkRead.NoSession, reader.read(HALF))
        assertEquals(0, server.requestCount)
        assertEquals(0, backgroundLogin.requests)
    }

    @Test fun `an ended ITMO ID session is reported as such`() = runTest {
        store.install(123, old)
        server.rejected = old
        renewals += BarsCookieRenewal.SessionEnded

        assertEquals(BarsMarkRead.SessionEnded, reader.read(HALF))
        assertEquals(1, backgroundLogin.requests)
    }

    @Test fun `a lost journal fails the read and the period is still given back`() = runTest {
        store.install(123, old)
        server.year = "2025/2026"
        server.term = 0
        server.offline += "marks/2/"

        assertEquals(BarsMarkRead.Failure(AppError.Network), reader.read(HALF))
        assertEquals(listOf("current_year=2025/2026", "current_term=0"), server.settings.takeLast(2))
    }

    @Test fun `the network lost on the second journal of three fails the whole read`() = runTest {
        store.install(123, old)
        server.plans = listOf(1L, 2L, 3L)
        server.offline += "marks/2/"

        val read = reader.read(HALF)

        assertEquals(BarsMarkRead.Failure(AppError.Network), read)
        assertEquals(0, backgroundLogin.requests)
    }

    private companion object {
        val HALF = StudyHalf(2026, 1)
    }
}
