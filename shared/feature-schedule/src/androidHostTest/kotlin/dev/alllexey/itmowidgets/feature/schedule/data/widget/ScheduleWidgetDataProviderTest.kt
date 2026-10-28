package dev.alllexey.itmowidgets.feature.schedule.data.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.SportMark
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.PreferenceStores
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleDays
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewLabels
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.WidgetContentKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ScheduleWidgetDataProviderTest {
    private val stores = PreferenceStores(MemoryPreferences())
    private val days = FakeDays()
    private val tokens = FakeSessionTokenStore()
    private val demo = FakeDemoMode()
    private val provider = ScheduleWidgetDataProvider(
        days, stores.scheduleChecks, stores.widgetSettings, StoredOptInGate(stores.servicesOptIn, demo), Time, ScheduleWidgetSelector(), tokens
    )

    @Test
    fun `a scheduled update refreshes today and the week ahead and selects from the cached days`() = runTest {
        val selection = available()

        assertEquals(listOf(Time.today() to Time.today().plus(6, DateTimeUnit.DAY)), days.refreshed)
        assertEquals(WidgetContentKind.CONTENT, selection.snapshot.next.kind)
        assertEquals("Программирование", selection.snapshot.next.lesson?.subject)
    }

    @Test
    fun `a minute tick reads the cache only`() = runTest {
        provider.load(refresh = false)

        assertTrue(days.refreshed.isEmpty())
    }

    @Test
    fun `the text sizes and the day settings reach the snapshot`() = runTest {
        stores.widgetSettings.setFullWidgetTeacherHidden(true)
        stores.widgetSettings.setFullWidgetPastLessonsHidden(true)

        val day = available().snapshot.day

        assertTrue(day.lessons.all { it.teacher == null })
        assertEquals(3, day.lessons.size)
    }

    @Test
    fun `queues refresh the widgets every few minutes only through both gates`() = runTest {
        stores.scheduleChecks.setScheduleSportAutoSignEnabled(true)
        assertTrue(available().nextUpdateDelay > ScheduleWidgetSelector.PERIODIC_UPDATE_DELAY)
        stores.servicesOptIn.setCustomServicesEnabled(true)
        assertEquals(ScheduleWidgetSelector.PERIODIC_UPDATE_DELAY, available().nextUpdateDelay)
    }

    @Test
    fun `without a shown queue the widgets wait for the next lesson boundary`() = runTest {
        stores.scheduleChecks.setScheduleSportAutoSignEnabled(true)
        stores.servicesOptIn.setCustomServicesEnabled(true)
        days.cached = days.cached.map { day -> day.copy(lessons = day.lessons.filter { it.sport != SportMark.QUEUED }) }

        assertTrue(available().nextUpdateDelay > ScheduleWidgetSelector.PERIODIC_UPDATE_DELAY)
    }

    @Test
    fun `a failed refresh without a cache is unavailable`() = runTest {
        days.cached = emptyList()
        days.result = AppResult.Failure(AppError.Network)

        assertEquals(ScheduleWidgetLoadResult.Unavailable, provider.load())
        assertEquals(ScheduleWidgetTimelineLoadResult.Unavailable, provider.loadTimeline(endOfTomorrow()))
    }

    @Test
    fun `signed out widgets never fetch`() = runTest {
        tokens.signedIn = false

        assertEquals(WidgetContentKind.SIGNED_OUT, available().snapshot.next.kind)
        val timeline = (provider.loadTimeline(endOfTomorrow()) as ScheduleWidgetTimelineLoadResult.Available).timeline
        assertEquals(WidgetContentKind.SIGNED_OUT, timeline.entries.single().snapshot.day.kind)
        assertTrue(days.refreshed.isEmpty())
    }

    @Test
    fun `the timeline reads through the week after its end and starts with the worker snapshot`() = runTest {
        val until = endOfTomorrow()
        val timeline = (provider.loadTimeline(until) as ScheduleWidgetTimelineLoadResult.Available).timeline

        assertEquals(listOf(Time.today() to Time.today().plus(7, DateTimeUnit.DAY)), days.refreshed)
        assertEquals(Time.now(), timeline.generatedAt)
        assertEquals(until, timeline.validUntil)
        assertEquals(available().snapshot.copy(pendingValidUntil = null), timeline.entryAt(Time.now())!!.snapshot.copy(pendingValidUntil = null))
    }

    @Test
    fun `the timeline must end after now`() = runTest {
        try {
            provider.loadTimeline(Time.now())
            fail("A timeline ending now must be rejected")
        } catch (_: IllegalArgumentException) {
            assertTrue(days.refreshed.isEmpty())
        }
    }

    private fun endOfTomorrow() = Time.today().plus(2, DateTimeUnit.DAY).atStartOfDayIn(Time.timeZone)

    private suspend fun available() = (provider.load() as ScheduleWidgetLoadResult.Available).selection

    private class FakeDays : ScheduleDays {
        var cached: List<LessonDay> = SchedulePreviewScenario(ScheduleWidgetSelector()).days(
            SchedulePreviewLabels("Матанализ", "Физика", "Программирование", "Английский", "Плавание", "Алгоритмы", "История", "Иванов Иван")
        )
        var result: AppResult<Unit> = AppResult.Success(Unit)
        val refreshed = mutableListOf<Pair<LocalDate, LocalDate>>()

        override fun observe(userIsu: Int?, from: LocalDate, to: LocalDate): Flow<List<LessonDay>> =
            flowOf(cached.filter { it.date in from..to })

        override suspend fun refresh(userIsu: Int?, from: LocalDate, to: LocalDate): AppResult<Unit> {
            refreshed += from to to
            return result
        }
    }

    /** The app's `DefaultBackendGate` over the stored opt-in, which this test switches through [stores]. */
    private class StoredOptInGate(private val optIn: ServicesOptInPreferences, private val demo: DemoMode) : BackendGate {
        override suspend fun isConnected(): Boolean = demo.isActive() || isOptedIn()
        override fun observeConnected(): Flow<Boolean> =
            combine(demo.observeActive(), optIn.observeCustomServicesEnabled()) { demo, optedIn -> demo || optedIn }
        override suspend fun mayCallBackend(): Boolean = !demo.isActive() && isOptedIn()
        override suspend fun isOptedIn(): Boolean = optIn.getCustomServicesEnabled()
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    // The sample Monday of the preview scenario, during its third lesson.
    private object Time : AcademicTimeProvider by FixedAcademicTime(LocalDateTime(2026, 9, 7, 12, 22))
}
