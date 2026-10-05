package dev.alllexey.itmowidgets.feature.schedule.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.testing.FakeCalendarSync
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.testing.FakePendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.testing.FakeSchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.MutableAcademicTime
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SchedulePendingSportTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test
    fun `disabled preference never observes or refreshes sport`() = runTest(dispatcher.dispatcher) {
        val pending = FakePendingSportBookingsRepository(booking())
        val model = model(pending = pending)
        model.refresh(RefreshMode.Silent)
        runCurrent()

        assertTrue(model.content().displayDays.all { it.pendingSport.isEmpty() })
        assertEquals(0, pending.observations)
        assertEquals(0, pending.refreshes)
        model.refresh(RefreshMode.Pull)
        runCurrent()
        assertEquals(0, pending.refreshes)
    }

    @Test
    fun `time updates remove started pending rows without reloading or clearing official data`() = runTest(dispatcher.dispatcher) {
        val clock = MutableAcademicTime(LocalDateTime(2026, 9, 7, 12, 0).toJavaLocalDateTime())
        val official = FakeScheduleRepository(listOf(day()))
        val pending = FakePendingSportBookingsRepository(booking())
        val model = ScheduleViewModel(official, clock, SavedStateHandle(), FakeSchedulePreferencesRepository(true), pending, FakeScheduleChangesRepository(), FakeCalendarSync())
        model.refresh(RefreshMode.Silent)
        runCurrent()
        val original = model.content().schedule
        assertEquals(1, model.content().displayDays.single().pendingSport.size)
        clock.current = booking().start.toLocalDateTime(Today.timeZone).toJavaLocalDateTime()
        model.updateTimeState()
        assertEquals(original, model.content().schedule)
        assertTrue(model.content().displayDays.single().pendingSport.isEmpty())
        assertEquals(1, official.refreshed.size)
        assertEquals(1, pending.refreshes)
        assertEquals(0, official.clears)
    }

    @Test
    fun `live toggles and queue emissions update the overlay without touching official data`() = runTest(dispatcher.dispatcher) {
        val preference = FakeSchedulePreferencesRepository()
        val pending = FakePendingSportBookingsRepository(booking())
        val official = FakeScheduleRepository(listOf(day()))
        val model = model(official, preference, pending)
        model.refresh(RefreshMode.Silent)
        runCurrent()

        preference.enabled.value = true
        runCurrent()
        assertEquals(listOf(booking()), model.content().displayDays.single().pendingSport)
        assertEquals(1, pending.refreshes)
        assertEquals(official.days.value, model.content().schedule)
        assertEquals(1, official.refreshed.size)

        val next = booking(id = 2)
        pending.values.value = AppResult.Success(listOf(next))
        runCurrent()
        assertEquals(listOf(next), model.content().displayDays.single().pendingSport)
        preference.enabled.value = false
        runCurrent()
        assertTrue(model.content().displayDays.all { it.pendingSport.isEmpty() })
        pending.values.value = AppResult.Success(listOf(booking(id = 3)))
        runCurrent()
        assertTrue(model.content().displayDays.all { it.pendingSport.isEmpty() })
        assertEquals(1, official.refreshed.size)
    }

    @Test
    fun `friend root argument never exposes own queues even without selected user`() = runTest(dispatcher.dispatcher) {
        val pending = FakePendingSportBookingsRepository(booking())
        val official = FakeScheduleRepository(listOf(day())).apply { schedulesFor(123456).value = listOf(day()) }
        val model = model(official, FakeSchedulePreferencesRepository(true), pending,
            saved = SavedStateHandle(mapOf(ScheduleViewModel.ARG_USER_ISU to 123456)))
        model.refresh(RefreshMode.Silent)
        runCurrent()

        assertNull(model.content().selectedUser)
        assertTrue(model.content().displayDays.all { it.pendingSport.isEmpty() })
        assertEquals(0, pending.observations)
        assertEquals(0, pending.refreshes)
    }

    @Test
    fun `selecting friend hides queues immediately and returning to own restarts projection`() = runTest(dispatcher.dispatcher) {
        val pending = FakePendingSportBookingsRepository(booking())
        val model = model(preferences = FakeSchedulePreferencesRepository(true), pending = pending)
        model.refresh(RefreshMode.Silent)
        runCurrent()
        assertEquals(1, model.content().displayDays.single().pendingSport.size)

        model.setSelectedUser(SelectedUser(123456, "Тестовый друг", null))
        assertTrue(model.content().displayDays.all { it.pendingSport.isEmpty() })
        model.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(1, pending.observations)
        model.setSelectedUser(null)
        model.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(2, pending.observations)
        assertEquals(1, model.content().displayDays.single().pendingSport.size)
    }

    @Test
    fun `pending errors and an unfinished refresh do not replace academic content or spinner state`() = runTest(dispatcher.dispatcher) {
        val pending = FakePendingSportBookingsRepository(booking())
        val model = model(preferences = FakeSchedulePreferencesRepository(true), pending = pending)
        model.refresh(RefreshMode.Silent)
        runCurrent()
        val official = model.content().schedule
        pending.values.value = AppResult.Failure(AppError.Network)
        runCurrent()
        assertEquals(official, model.content().schedule)
        assertFalse(model.content().loadingMore)
        assertTrue(model.content().displayDays.all { it.pendingSport.isEmpty() })

        val wait = CompletableDeferred<Unit>()
        pending.refreshBlock = { wait.await() }
        model.refresh(RefreshMode.Pull)
        runCurrent()
        assertEquals(2, pending.refreshes)
        assertEquals(official, model.content().schedule)
        assertFalse(model.content().loadingMore)
        wait.complete(Unit)
        runCurrent()
        pending.values.value = AppResult.Success(emptyList())
        runCurrent()
        assertTrue(model.content().displayDays.all { it.pendingSport.isEmpty() })
    }

    @Test
    fun `pending does not disguise failure of an empty official schedule`() = runTest(dispatcher.dispatcher) {
        val official = FakeScheduleRepository(listOf(day())).apply {
            days.value = emptyList()
            refreshResult = AppResult.Failure(AppError.Network)
        }
        val model = model(official, FakeSchedulePreferencesRepository(true))
        model.refresh(RefreshMode.Silent)
        runCurrent()
        assertEquals(ScheduleUiState.Error(AppError.Network, null), model.uiState.value)
    }

    @Test
    fun `pending only initial load stays loading and does not hide its failure`() = runTest(dispatcher.dispatcher) {
        val response = CompletableDeferred<AppResult<Unit>>()
        val official = FakeScheduleRepository(listOf(day())).apply {
            days.value = emptyList()
            refreshHandler = { response.await() }
        }
        val pending = FakePendingSportBookingsRepository(booking())
        val model = model(official, FakeSchedulePreferencesRepository(true), pending)
        model.refresh(RefreshMode.Silent)
        runCurrent()

        assertEquals(ScheduleUiState.Loading(null), model.uiState.value)
        assertEquals(1, pending.observations)
        response.complete(AppResult.Failure(AppError.Network))
        runCurrent()
        assertEquals(ScheduleUiState.Error(AppError.Network, null), model.uiState.value)

        pending.values.value = AppResult.Success(listOf(booking(id = 2)))
        runCurrent()
        assertEquals(ScheduleUiState.Error(AppError.Network, null), model.uiState.value)
    }

    @Test
    fun `pending only content survives delayed refresh failure and paginated loading after official success`() = runTest(dispatcher.dispatcher) {
        val official = FakeScheduleRepository(listOf(day())).apply { days.value = emptyList() }
        val near = booking(day = Today.today().plus(2, DateTimeUnit.DAY))
        val nextPage = booking(id = 2, day = Today.today().plus(15, DateTimeUnit.DAY))
        val pending = FakePendingSportBookingsRepository(booking()).apply { values.value = AppResult.Success(listOf(near, nextPage)) }
        val model = model(official, FakeSchedulePreferencesRepository(true), pending)
        model.refresh(RefreshMode.Silent)
        runCurrent()
        val initialDisplay = model.content().displayDays

        val refresh = CompletableDeferred<AppResult<Unit>>()
        official.refreshHandler = { refresh.await() }
        model.refresh(RefreshMode.Pull)
        runCurrent()
        assertEquals(initialDisplay, model.content().displayDays)
        assertTrue(model.content().loadingMore)
        assertTrue(model.content().schedule.isEmpty())

        val error = async { model.events.first() }
        refresh.complete(AppResult.Failure(AppError.Network))
        runCurrent()
        assertEquals(ScheduleEvent.ShowError(AppError.Network), error.await())
        assertEquals(initialDisplay, model.content().displayDays)
        assertFalse(model.content().loadingMore)

        val page = CompletableDeferred<AppResult<Unit>>()
        official.refreshHandler = { page.await() }
        model.fetchNextDays()
        runCurrent()
        assertTrue(model.content().loadingMore)
        assertTrue(model.content().schedule.isEmpty())
        assertEquals(listOf(near, nextPage), model.content().displayDays.flatMap { it.pendingSport })
        page.complete(AppResult.Success(Unit))
        runCurrent()
        assertFalse(model.content().loadingMore)
        assertEquals(listOf(near, nextPage), model.content().displayDays.flatMap { it.pendingSport })
        assertEquals(0, official.clears)
    }

    @Test
    fun `returning from a friend must successfully load own schedule again before showing pending only data`() = runTest(dispatcher.dispatcher) {
        val official = FakeScheduleRepository(listOf(day())).apply { days.value = emptyList() }
        val model = model(official, FakeSchedulePreferencesRepository(true))
        model.refresh(RefreshMode.Silent)
        runCurrent()
        assertEquals(1, model.content().displayDays.single().pendingSport.size)

        val friend = SelectedUser(123456, "Тестовый друг", null)
        val friendResponse = CompletableDeferred<AppResult<Unit>>()
        official.refreshHandler = { friendResponse.await() }
        model.setSelectedUser(friend)
        model.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(ScheduleUiState.Loading(friend), model.uiState.value)
        friendResponse.complete(AppResult.Success(Unit))
        runCurrent()
        assertEquals(ScheduleUiState.Empty(friend), model.uiState.value)

        val ownResponse = CompletableDeferred<AppResult<Unit>>()
        official.refreshHandler = { ownResponse.await() }
        model.setSelectedUser(null)
        model.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(ScheduleUiState.Loading(null), model.uiState.value)
        ownResponse.complete(AppResult.Failure(AppError.Network))
        runCurrent()
        assertEquals(ScheduleUiState.Error(AppError.Network, null), model.uiState.value)
    }

    @Test
    fun `removing pending only data during refresh restores loading and subsequent official error`() = runTest(dispatcher.dispatcher) {
        for (disablePreference in listOf(true, false)) {
            val official = FakeScheduleRepository(listOf(day())).apply { days.value = emptyList() }
            val preference = FakeSchedulePreferencesRepository(true)
            val pending = FakePendingSportBookingsRepository(booking())
            val model = model(official, preference, pending)
            model.refresh(RefreshMode.Silent)
            runCurrent()
            val refresh = CompletableDeferred<AppResult<Unit>>()
            official.refreshHandler = { refresh.await() }
            model.refresh(RefreshMode.Pull)
            runCurrent()
            assertTrue(model.content().loadingMore)

            if (disablePreference) preference.enabled.value = false
            else pending.values.value = AppResult.Failure(AppError.Network)
            runCurrent()
            assertEquals(ScheduleUiState.Loading(null), model.uiState.value)
            refresh.complete(AppResult.Failure(AppError.Network))
            runCurrent()
            assertEquals(ScheduleUiState.Error(AppError.Network, null), model.uiState.value)
        }
    }

    @Test
    fun `queue-only dates follow loaded range and never enter official schedule`() = runTest(dispatcher.dispatcher) {
        val official = FakeScheduleRepository(listOf(day())).apply { days.value = emptyList() }
        val near = booking(day = Today.today().plus(2, DateTimeUnit.DAY))
        val nextPage = booking(id = 2, day = Today.today().plus(15, DateTimeUnit.DAY))
        val pending = FakePendingSportBookingsRepository(booking()).apply { values.value = AppResult.Success(listOf(near, nextPage)) }
        val model = model(official, FakeSchedulePreferencesRepository(true), pending)
        model.refresh(RefreshMode.Silent)
        runCurrent()
        assertTrue(model.content().schedule.isEmpty())
        assertEquals(listOf(near.localDate()), model.content().displayDays.map { it.date })
        assertNull(model.content().displayDays.single().officialDay)

        model.fetchNextDays()
        runCurrent()
        assertEquals(listOf(near.localDate(), nextPage.localDate()), model.content().displayDays.map { it.date })
        assertTrue(model.content().schedule.isEmpty())
        assertEquals(0, official.clears)
    }

    @Test
    fun `projection normalizes dates and deduplicates queue keys not titles times or academic IDs`() {
        val booking = booking().copy(
            start = Today.today().atTime(22, 0).toInstant(TimeZone.UTC),
            end = Today.today().atTime(23, 0).toInstant(TimeZone.UTC)
        )
        val another = booking.copy(queueId = 2)
        val anotherKind = booking.copy(queueKind = PendingSportBooking.QueueKind.FREE)
        val days = buildScheduleDisplayDays(listOf(day()), listOf(booking, booking, another, anotherKind),
            Today.today(), Today.today().plus(1, DateTimeUnit.DAY), Today.timeZone, Today.now())

        assertEquals(2, days.size)
        assertTrue(days.first().pendingSport.isEmpty())
        assertEquals(Today.today().plus(1, DateTimeUnit.DAY), days.last().date)
        assertEquals(3, days.last().pendingSport.size)
        assertEquals(1, days.last().pendingSport.first().start.toLocalDateTime(Today.timeZone).hour)
        assertNull(days.last().officialDay)
    }

    private fun model(
        official: FakeScheduleRepository = FakeScheduleRepository(listOf(day())), preferences: FakeSchedulePreferencesRepository = FakeSchedulePreferencesRepository(),
        pending: FakePendingSportBookingsRepository = FakePendingSportBookingsRepository(booking()), saved: SavedStateHandle = SavedStateHandle()
    ) = ScheduleViewModel(official, Today, saved, preferences, pending, FakeScheduleChangesRepository(), FakeCalendarSync())

    private fun ScheduleViewModel.content() = uiState.value as ScheduleUiState.Content

    private fun PendingSportBooking.localDate() = start.toLocalDateTime(Today.timeZone).date

    private object Today : AcademicTimeProvider by FixedAcademicTime(LocalDateTime(2026, 9, 7, 12, 0))

    companion object {
        private fun day() = DaySchedule(1, 1, Today.today(), null, emptyList())
        private fun booking(id: Long = 1, day: LocalDate = Today.today()) = PendingSportBooking(
            queueId = id, queueKind = PendingSportBooking.QueueKind.AUTO, lessonId = 100 + id,
            sectionName = "Тестовая секция", start = day.atTime(16, 0).toInstant(Today.timeZone),
            end = day.atTime(17, 30).toInstant(Today.timeZone), teacherFio = "Тестовый преподаватель",
            roomName = "Тестовый корпус", isPrediction = true
        )
    }
}
