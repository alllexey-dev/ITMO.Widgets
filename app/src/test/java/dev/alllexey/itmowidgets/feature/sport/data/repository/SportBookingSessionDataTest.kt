package dev.alllexey.itmowidgets.feature.sport.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.testing.myItmoStub
import dev.alllexey.itmowidgets.core.util.CustomDataState
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SportBookingSessionDataTest {

    @Test
    fun `clear removes confirmed bookings from live observation and replay`() = runTest {
        val fixture = fixture()
        fixture.repository.refreshSportBookings()
        val content = fixture.repository.observeConfirmedSportBookings().first()
        assertEquals(listOf(42L), (content as DataState.Success).data.map { it.lessonId })
        val observed = mutableListOf<DataState<List<SportBooking>>>()
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
        val fixture = fixture().apply {
            responseCode = 403
            responseBody = { EMPTY_RESULT }
        }

        fixture.repository.refreshSportBookings()

        assertEquals(
            DataState.Error(AppError.Forbidden),
            fixture.repository.observeConfirmedSportBookings().first()
        )
    }

    @Test
    fun `HTTP 200 api errors are not reported as empty confirmed bookings`() = runTest {
        val fixture = fixture().apply {
            responseBody = { """{"error_code":401,"result":[]}""" }
        }

        fixture.repository.refreshSportBookings()

        assertEquals(
            DataState.Error(AppError.Unauthorized),
            fixture.repository.observeConfirmedSportBookings().first()
        )
    }

    @Test
    fun `missing result is an error but a successful empty list is valid`() = runTest {
        val fixture = fixture().apply {
            responseBody = { """{"error_code":0,"result":null}""" }
        }
        fixture.repository.refreshSportBookings()
        assertTrue(fixture.repository.observeConfirmedSportBookings().first() is DataState.Error)

        fixture.responseBody = { EMPTY_RESULT }
        fixture.repository.refreshSportBookings()

        assertEquals(emptyBookings(), fixture.repository.observeConfirmedSportBookings().first())
    }

    private suspend fun TestScope.assertLateResponseIgnored(response: String) {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<String>()
        val fixture = fixture().apply {
            settings.setCustomServicesEnabled(true)
            responseBody = {
                started.complete(Unit)
                runBlocking { release.await() }
            }
        }
        val observed = mutableListOf<DataState<List<SportBooking>>>()
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
        val refreshed = fixture.repository.observeConfirmedSportBookings().first() as DataState.Success
        assertEquals(listOf(42L), refreshed.data.map { it.lessonId })
        assertEquals(1, fixture.syncCalls.get())
    }

    private fun fixture() = Fixture()

    private fun emptyBookings(): DataState<List<SportBooking>> = DataState.Success(emptyList())

    private class Fixture {
        val settings = AppSettingsStorage(InMemoryPreferencesDataStore())
        val syncCalls = AtomicInteger()
        @Volatile var responseCode = 200
        @Volatile var responseBody: () -> String = { BOOKINGS }

        private val myItmo = myItmoStub { responseBody() }.apply {
            okHttpClient = okHttpClient.newBuilder().apply {
                interceptors().add(0, Interceptor { chain ->
                    chain.proceed(chain.request()).newBuilder()
                        .code(responseCode).message("Synthetic response").build()
                })
            }.build()
        }
        private val widgetsApi = Proxy.newProxyInstance(
            ItmoWidgetsApi::class.java.classLoader,
            arrayOf(ItmoWidgetsApi::class.java)
        ) { _, method, _ ->
            check(method.name == "syncSportLessons") { "Unexpected Backend call: ${method.name}" }
            syncCalls.incrementAndGet()
            ApiResponse.success("OK")
        } as ItmoWidgetsApi
        private val sportData = Proxy.newProxyInstance(
            SportDataRepository::class.java.classLoader,
            arrayOf(SportDataRepository::class.java)
        ) { _, method, _ ->
            check(method.name in setOf("observeSportQueueEntries", "observeFriendsBookings")) {
                "Unexpected sport-data call: ${method.name}"
            }
            flowOf(CustomDataState.Disabled)
        } as SportDataRepository

        val repository = SportBookingRepositoryImpl(settings, sportData, myItmo.api, widgetsApi)
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        private val mutex = Mutex()
        override val data = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }

    private companion object {
        const val EMPTY_RESULT = """{"error_code":0,"result":[]}"""
        const val BOOKINGS = """{"error_code":0,"result":[{"id":1,"section_name":"Sport","level":1,"lesson_groups":[{"id":2,"level":1,"lessons":[{"id":42,"date_start":"2026-09-21T12:00:00+03:00","date_end":"2026-09-21T13:30:00+03:00","room_name":"Room","teacher_fio":"Teacher","teacher_isu":900001}]}]}]}"""
    }
}
