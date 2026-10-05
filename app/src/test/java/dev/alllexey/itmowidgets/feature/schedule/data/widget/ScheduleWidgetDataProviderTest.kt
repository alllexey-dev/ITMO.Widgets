package dev.alllexey.itmowidgets.feature.schedule.data.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.DefaultBackendGate
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.PreferenceStores
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.javaToday
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetPendingStatus
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SingleLessonWidgetKind
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.hours
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
    private val stores = PreferenceStores(MemoryPreferences())
    private val official = FakeScheduleRepository(listOf(DaySchedule(1, 1, Time.javaToday(), null, emptyList())))
    private val pending = SnapshotPendingRepository()
    private val tokens = FakeSessionTokenStore()
    private val demo = FakeDemoMode()
    private val provider = ScheduleWidgetDataProvider(
        official, stores.scheduleChecks, stores.widgetSettings, DefaultBackendGate(stores.servicesOptIn, demo), Time, ScheduleWidgetSelector(), pending, tokens
    )

    @Test
    fun `worker settings select teachers independently for the two rendered formats`() = runTest {
        enable()
        stores.widgetSettings.setCompactWidgetTeacherHidden(true)
        stores.widgetSettings.setFullWidgetTeacherHidden(false)
        val first = available().snapshot
        assertNull(first.singleLesson.lesson?.teacher)
        assertEquals("Тестовый преподаватель", first.lessonList.first().lesson?.teacher)
        stores.widgetSettings.setCompactWidgetTeacherHidden(false)
        stores.widgetSettings.setFullWidgetTeacherHidden(true)
        val reversed = available().snapshot
        assertEquals("Тестовый преподаватель", reversed.singleLesson.lesson?.teacher)
        assertNull(reversed.lessonList.first().lesson?.teacher)
    }

    @Test
    fun `default off and disabled services never read or refresh optional backend data`() = runTest {
        stores.servicesOptIn.setCustomServicesEnabled(true)
        assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, available().snapshot.singleLesson.kind)
        stores.servicesOptIn.setCustomServicesEnabled(false)
        stores.scheduleChecks.setScheduleSportAutoSignEnabled(true)
        assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, available().snapshot.singleLesson.kind)
        assertEquals(0, pending.refreshes)
        assertEquals(0, pending.reads)
        assertTrue(official.refreshed.map { it.userIsu }.all { it == null })
    }

    @Test
    fun `the demo session alone does not count as the opt-in`() = runTest {
        stores.scheduleChecks.setScheduleSportAutoSignEnabled(true)
        demo.active.value = true
        assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, available().snapshot.singleLesson.kind)
        assertEquals(0, pending.refreshes)
        assertEquals(0, pending.reads)
    }

    @Test
    fun `cold widget refreshes sources before reading pending snapshot and includes pending only day`() = runTest {
        enable()
        stores.widgetSettings.setFullWidgetTomorrowEnabled(true)
        val result = available()
        assertEquals(ScheduleWidgetPendingStatus.PREDICTED, result.snapshot.singleLesson.lesson?.pendingStatus)
        assertEquals(listOf("refresh", "snapshot"), pending.calls)
        assertEquals(Time.javaToday() to Time.javaToday().plusDays(1), official.refreshed.map { it.startDate to it.endDate }.single())
        assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, result.snapshot.withoutPendingSport().singleLesson.kind)
        assertEquals(ScheduleWidgetSelector.PERIODIC_UPDATE_DELAY, result.nextUpdateDelay)
        assertEquals(0, official.clears)
    }

    @Test
    fun `optional error or exception removes only optional rows`() = runTest {
        enable()
        assertNotNull(available().snapshot.singleLesson.lesson)
        pending.value = AppResult.Failure(AppError.Network)
        assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, available().snapshot.singleLesson.kind)
        pending.refreshBlock = { error("Synthetic unavailable source") }
        assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, available().snapshot.singleLesson.kind)
        assertEquals(0, official.clears)
    }

    @Test
    fun `official failure without cache is not disguised as pending content`() = runTest {
        enable()
        official.days.value = emptyList()
        official.refreshResult = AppResult.Failure(AppError.Network)
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
            if (services) stores.servicesOptIn.setCustomServicesEnabled(false)
            else stores.scheduleChecks.setScheduleSportAutoSignEnabled(false)
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
        assertEquals(0, official.refreshed.map { it.userIsu }.size)
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
        stores.scheduleChecks.setScheduleSportAutoSignEnabled(true)
        stores.servicesOptIn.setCustomServicesEnabled(true)
    }

    private suspend fun available() = (provider.load() as ScheduleWidgetLoadResult.Available).selection

    private class SnapshotPendingRepository : PendingSportBookingsRepository {
        var value: AppResult<List<PendingSportBooking>> = AppResult.Success(listOf(PendingSportBooking(
            1, PendingSportBooking.QueueKind.AUTO, -1, "Плавание", Time.now() + 1.hours,
            Time.now() + 2.hours, "Тестовый преподаватель", "Бассейн", true
        )))
        var refreshBlock: suspend () -> Unit = {}
        var refreshes = 0
        var reads = 0
        val calls = mutableListOf<String>()
        override fun observePendingBookings() = error("Widgets must use the completed snapshot API")
        override suspend fun refresh() { calls += "refresh"; refreshes++; refreshBlock() }
        override suspend fun getPendingBookings(): AppResult<List<PendingSportBooking>> {
            calls += "snapshot"; reads++; return value
        }
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    private object Time : AcademicTimeProvider by FixedAcademicTime(LocalDateTime.of(2026, 9, 8, 10, 0))
}
