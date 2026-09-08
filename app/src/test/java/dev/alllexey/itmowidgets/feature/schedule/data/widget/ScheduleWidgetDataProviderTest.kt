package dev.alllexey.itmowidgets.feature.schedule.data.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.session.SessionTokens
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetPendingStatus
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SingleLessonWidgetKind
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleWidgetDataProviderTest {
    private val settings = AppSettingsStorage(MemoryPreferences())
    private val official = OfficialRepository()
    private val pending = PendingRepository()
    private val tokens = Tokens()
    private val provider = ScheduleWidgetDataProvider(official, settings, Time, ScheduleWidgetSelector(), pending, tokens)

    @Test
    fun `default off and disabled services never read or refresh optional backend data`() = runTest {
        settings.setCustomServicesEnabled(true)
        assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, available().snapshot.singleLesson.kind)
        settings.setCustomServicesEnabled(false)
        settings.setScheduleSportAutoSignEnabled(true)
        assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, available().snapshot.singleLesson.kind)
        assertEquals(0, pending.refreshes)
        assertEquals(0, pending.reads)
        assertTrue(official.users.all { it == null })
    }

    @Test
    fun `cold widget refreshes sources before reading pending snapshot and includes pending only day`() = runTest {
        enable()
        settings.setWidgetFutureScheduleEnabled(true)
        val result = available()
        assertEquals(ScheduleWidgetPendingStatus.PREDICTED, result.snapshot.singleLesson.lesson?.pendingStatus)
        assertEquals(listOf("refresh", "snapshot"), pending.calls)
        assertEquals(Time.today() to Time.today().plusDays(1), official.ranges.single())
        assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, result.snapshot.withoutPendingSport().singleLesson.kind)
        assertEquals(ScheduleWidgetSelector.PERIODIC_UPDATE_DELAY, result.nextUpdateDelay)
        assertEquals(0, official.clears)
    }

    @Test
    fun `optional error or exception removes only optional rows`() = runTest {
        enable()
        assertNotNull(available().snapshot.singleLesson.lesson)
        pending.value = DataState.Error(AppError.Network)
        assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, available().snapshot.singleLesson.kind)
        pending.refreshBlock = { error("Synthetic unavailable source") }
        assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, available().snapshot.singleLesson.kind)
        assertEquals(0, official.clears)
    }

    @Test
    fun `official failure without cache is not disguised as pending content`() = runTest {
        enable()
        official.value.value = emptyList()
        official.result = AppResult.Failure(AppError.Network)
        assertTrue(provider.load() is ScheduleWidgetLoadResult.Unavailable)
    }

    @Test
    fun `turning either flag off during refresh excludes pending response`() = runTest {
        for (services in listOf(false, true)) {
            enable()
            val gate = CompletableDeferred<Unit>()
            pending.refreshBlock = { gate.await() }
            val load = async { available() }
            runCurrent()
            if (services) settings.setCustomServicesEnabled(false)
            else settings.setScheduleSportAutoSignEnabled(false)
            gate.complete(Unit)
            assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, load.await().snapshot.singleLesson.kind)
        }
    }

    @Test
    fun `signed out widgets never call either source`() = runTest {
        enable()
        tokens.signedIn = false
        assertEquals(SingleLessonWidgetKind.SIGNED_OUT, available().snapshot.singleLesson.kind)
        assertEquals(0, pending.refreshes)
        assertEquals(0, official.users.size)
    }

    @Test
    fun `sign out during fetch cannot return pending content`() = runTest {
        enable()
        val gate = CompletableDeferred<Unit>()
        pending.refreshBlock = { gate.await() }
        val load = async { available() }
        runCurrent()
        tokens.signedIn = false
        gate.complete(Unit)
        assertEquals(SingleLessonWidgetKind.SIGNED_OUT, load.await().snapshot.singleLesson.kind)
    }

    @Test
    fun `optional cancellation cancels worker load instead of rendering a replacement`() = runTest {
        enable()
        pending.refreshBlock = { throw CancellationException("Session changed") }
        try {
            provider.load()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(0, pending.reads)
        }
    }

    private suspend fun enable() {
        settings.setScheduleSportAutoSignEnabled(true)
        settings.setCustomServicesEnabled(true)
    }

    private suspend fun available() = (provider.load() as ScheduleWidgetLoadResult.Available).selection

    private class OfficialRepository : ScheduleRepository {
        val value = MutableStateFlow(listOf(DaySchedule(1, 1, Time.today(), null, emptyList())))
        var result: AppResult<Unit> = AppResult.Success(Unit)
        val users = mutableListOf<Int?>()
        val ranges = mutableListOf<Pair<LocalDate, LocalDate>>()
        var clears = 0
        override fun observeScheduleForRange(userIsu: Int?, startDate: LocalDate, endDate: LocalDate) = value
        override suspend fun refreshSchedule(userIsu: Int?, startDate: LocalDate, endDate: LocalDate): AppResult<Unit> {
            users += userIsu
            ranges += startDate to endDate
            return result
        }
        override suspend fun clearCaches() { clears++ }
    }

    private class PendingRepository : PendingSportBookingsRepository {
        var value: DataState<List<PendingSportBooking>> = DataState.Success(listOf(PendingSportBooking(
            1, PendingSportBooking.QueueKind.AUTO, -1, "Плавание", Time.now().plusHours(1),
            Time.now().plusHours(2), "Тестовый преподаватель", "Бассейн", true
        )))
        var refreshBlock: suspend () -> Unit = {}
        var refreshes = 0
        var reads = 0
        val calls = mutableListOf<String>()
        override fun observePendingBookings() = error("Widgets must use the completed snapshot API")
        override suspend fun refresh() { calls += "refresh"; refreshes++; refreshBlock() }
        override suspend fun getPendingBookings(): DataState<List<PendingSportBooking>> {
            calls += "snapshot"; reads++; return value
        }
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    private class Tokens : SessionTokenStore {
        var signedIn = true
        override fun hasRefreshToken() = signedIn
        override fun getIdToken(): String? = null
        override fun replaceWithRefreshToken(refreshToken: String) = Unit
        override fun replaceWithTokens(tokens: SessionTokens) = Unit
        override fun clearTokens() { signedIn = false }
    }

    private object Time : AcademicTimeProvider {
        override val zoneId = ZoneId.of("Europe/Moscow")
        override fun now(): OffsetDateTime = OffsetDateTime.parse("2026-09-08T10:00:00+03:00")
        override fun today(): LocalDate = now().toLocalDate()
    }
}
