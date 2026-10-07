package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.sport.data.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.sport.data.blockingIoAppDispatchers
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.feature.sport.data.Core2Harness
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.http.HttpStatusCode
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SportBookingSessionDataTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** The fake network holds a request on a blocked thread; see [blockingIoAppDispatchers]. */
    private val dispatchers = blockingIoAppDispatchers(mainDispatcherRule.dispatcher)

    @Test
    fun `clear removes confirmed bookings from live observation and replay`() = runTest {
        val fixture = fixture()
        fixture.repository.refreshSportBookings()
        val content = fixture.repository.observeConfirmedSportBookings().first()
        assertEquals(listOf(42L), (content as AppResult.Success).value.map { it.lessonId })
        val observed = mutableListOf<AppResult<List<SportBooking>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.repository.observeConfirmedSportBookings().toList(observed)
        }

        fixture.repository.clearSessionData()

        assertEquals(listOf(content, emptyBookings()), observed)
        assertEquals(emptyBookings(), fixture.repository.observeConfirmedSportBookings().first())
        assertEquals(0, fixture.syncCalls.get())
    }

    @Test
    fun `clear before the first refresh supplies an empty replay`() = runTest {
        val fixture = fixture()

        fixture.repository.clearSessionData()

        assertEquals(emptyBookings(), fixture.repository.observeConfirmedSportBookings().first())
    }

    @Test
    fun `late confirmed bookings cannot repopulate a cleared session or sync to backend`() = runTest {
        assertLateResponseIgnored(BOOKINGS)
    }

    @Test
    fun `late api error cannot replace a cleared session and a new refresh still works`() = runTest {
        assertLateResponseIgnored("""{"error_code":401,"result":null}""")
    }

    @Test
    fun `HTTP errors are not reported as empty confirmed bookings`() = runTest {
        // MyItmoApi 2.x reads a bare MyITMO 403 as a lost session, as it reads a 401.
        for ((code, error) in listOf(403 to AppError.Unauthorized, 404 to AppError.NotFound)) {
            val fixture = fixture().apply {
                responseCode = code
                responseBody = { EMPTY_RESULT }
            }

            fixture.repository.refreshSportBookings()

            assertEquals(AppResult.Failure(error), fixture.repository.observeConfirmedSportBookings().first())
        }
        val failing = fixture().apply {
            responseCode = 502
            responseBody = { EMPTY_RESULT }
        }
        failing.repository.refreshSportBookings()
        val failure = failing.repository.observeConfirmedSportBookings().first() as AppResult.Failure
        assertTrue(failure.error is AppError.Unknown)
    }

    @Test
    fun `HTTP 200 api errors are not reported as empty confirmed bookings`() = runTest {
        val fixture = fixture().apply {
            responseBody = { """{"error_code":401,"result":[]}""" }
        }

        fixture.repository.refreshSportBookings()

        assertEquals(
            AppResult.Failure(AppError.Unauthorized),
            fixture.repository.observeConfirmedSportBookings().first()
        )
    }

    @Test
    fun `missing result is an error but a successful empty list is valid`() = runTest {
        val fixture = fixture().apply {
            responseBody = { """{"error_code":0,"result":null}""" }
        }
        fixture.repository.refreshSportBookings()
        assertTrue(fixture.repository.observeConfirmedSportBookings().first() is AppResult.Failure)

        fixture.responseBody = { EMPTY_RESULT }
        fixture.repository.refreshSportBookings()

        assertEquals(emptyBookings(), fixture.repository.observeConfirmedSportBookings().first())
    }

    private suspend fun TestScope.assertLateResponseIgnored(response: String) {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<String>()
        val fixture = fixture().apply {
            gate.optedIn.value = true
            responseBody = {
                started.complete(Unit)
                runBlocking { release.await() }
            }
        }
        val observed = mutableListOf<AppResult<List<SportBooking>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.repository.observeConfirmedSportBookings().toList(observed)
        }
        val oldRefresh = async { fixture.repository.refreshSportBookings() }
        try {
            started.await()
            fixture.repository.clearSessionData()
        } finally {
            release.complete(response)
        }
        oldRefresh.await()

        assertEquals(listOf(emptyBookings()), observed)
        assertEquals(emptyBookings(), fixture.repository.observeConfirmedSportBookings().first())
        assertEquals(0, fixture.syncCalls.get())

        fixture.responseBody = { BOOKINGS }
        fixture.repository.refreshSportBookings()
        val refreshed = fixture.repository.observeConfirmedSportBookings().first() as AppResult.Success
        assertEquals(listOf(42L), refreshed.value.map { it.lessonId })
        assertEquals(1, fixture.syncCalls.get())
    }

    @Test
    fun `bookings reach Backend only with the opt-in`() = runTest {
        val fixture = fixture()

        fixture.repository.refreshSportBookings()
        assertEquals(0, fixture.syncCalls.get())

        fixture.gate.optedIn.value = true
        fixture.repository.refreshSportBookings()
        assertEquals(1, fixture.syncCalls.get())
    }

    private fun fixture() = Fixture()

    private fun emptyBookings(): AppResult<List<SportBooking>> = AppResult.Success(emptyList())

    private inner class Fixture {
        val gate = FakeBackendGate(optedIn = false)
        val syncCalls = AtomicInteger()
        @Volatile var responseCode = 200
        @Volatile var responseBody: () -> String = { BOOKINGS }

        private val clients = Core2Harness(Core2Harness.session()) { request ->
            if (request.url.host == "my.itmo.ru") {
                respondJson(responseBody(), HttpStatusCode.fromValue(responseCode))
            } else {
                check(request.url.encodedPath == "/api/sport/sign/sync") { "Unexpected Backend call: ${request.url}" }
                syncCalls.incrementAndGet()
                respondJson(Core2Harness.contractFixture("http/sport/syncSportLessons.json"))
            }
        }
        private val sportData = Proxy.newProxyInstance(
            SportDataRepository::class.java.classLoader,
            arrayOf(SportDataRepository::class.java)
        ) { _, method, _ ->
            check(method.name in setOf("observeSportQueueEntries", "observeFriendsBookings")) {
                "Unexpected sport-data call: ${method.name}"
            }
            flowOf(LoadState.Disabled)
        } as SportDataRepository

        val repository = SportBookingRepositoryImpl(gate, sportData, clients.myItmo, clients.client.sport, FixedAcademicTime(), noDemo(), dispatchers)
    }

    private companion object {
        const val EMPTY_RESULT = """{"error_code":0,"result":[]}"""
        const val BOOKINGS = """{"error_code":0,"result":[{"id":1,"section_name":"Sport","level":1,"lesson_groups":[{"id":2,"level":1,"lessons":[{"id":42,"date_start":"2026-09-21T12:00:00+03:00","date_end":"2026-09-21T13:30:00+03:00","room_name":"Room","teacher_fio":"Teacher","teacher_isu":900001}]}]}]}"""
    }
}
