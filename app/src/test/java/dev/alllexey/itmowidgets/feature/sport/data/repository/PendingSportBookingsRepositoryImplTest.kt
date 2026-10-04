package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.*
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingSportBookingsRepositoryImplTest {
    private val bookings = Bookings()
    private val data = SportData()
    private val services = FakeCustomServicesRepository(enabled = true)
    private val repository = PendingSportBookingsRepositoryImpl(bookings, data, services, FixedTime)

    @Test fun `disabled services emit empty and make no requests`() = runTest {
        services.enabled.value = false
        data.entries.value = LoadState.Content(listOf(SportCardFixtures.entry()))

        assertEquals(AppResult.Success(emptyList<PendingSportBooking>()), repository.observePendingBookings().first())
        repository.refresh()
        assertEquals(0, bookings.refreshes)
        assertEquals(0, data.refreshes)
    }

    @Test fun `future prediction shifts once while bound real and free lessons use actual dates`() = runTest {
        val prototype = SportCardFixtures.entry().targetLesson.copy(
            id = 20, start = SportCardFixtures.start.minusWeeks(2), end = SportCardFixtures.start.minusWeeks(2).plusMinutes(90),
            sectionName = "  Секция  ", teacherFio = " Преподаватель ", roomName = " Зал ", teacherIsu = 300001
        )
        val real = prototype.copy(id = 30, start = SportCardFixtures.start.plusDays(1), end = SportCardFixtures.start.plusDays(1).plusMinutes(90), teacherIsu = 300002)
        data.entries.value = LoadState.Content(listOf(
            auto(prototype = prototype),
            auto(id = 3, prototype = prototype.copy(id = 21), real = real),
            SportCardFixtures.entry()
        ))

        val pending = repository.observePendingBookings().first { it is AppResult.Success && it.value.size == 3 } as AppResult.Success
        val predicted = pending.value.first { it.isPrediction }
        assertEquals(SportCardFixtures.start, predicted.start)
        assertEquals(-20L, predicted.lessonId)
        assertEquals("Секция", predicted.sectionName)
        assertEquals("Преподаватель", predicted.teacherFio)
        assertEquals(300001, predicted.teacherIsu)
        assertEquals(300001, predicted.toDetailsArgs().teacherIsu)
        assertEquals("Зал", predicted.roomName)
        val bound = pending.value.first { it.lessonId == 30L }
        assertFalse(bound.isPrediction)
        assertEquals(300002, bound.teacherIsu)
        assertEquals(300002, bound.toDetailsArgs().teacherIsu)
        assertEquals(real.start, bound.start)
        assertEquals(real.end, bound.end)
        assertEquals(PendingSportBooking.QueueKind.AUTO, bound.queueKind)
        val free = pending.value.first { it.queueKind == PendingSportBooking.QueueKind.FREE }
        assertEquals(SportCardFixtures.start, free.start)
        assertEquals(SportCardFixtures.entry().targetLesson.teacherIsu.toInt(), free.teacherIsu)
        assertEquals(free.teacherIsu, free.toDetailsArgs().teacherIsu)
    }

    @Test fun `only active future unsigned queues remain and multiple queues for real lesson do not duplicate`() = runTest {
        val base = SportCardFixtures.entry()
        val signed = base.copy(id = 8, targetLesson = base.targetLesson.copy(id = 88))
        bookings.confirmed.value = AppResult.Success(listOf(SportCardFixtures.booking(88)))
        data.entries.value = LoadState.Content(
            SportQueueEntryStatus.entries.mapIndexed { index, status ->
                base.copy(id = index + 10L, status = status, targetLesson = base.targetLesson.copy(id = index + 10L))
            } + listOf(
                base.copy(isCancelled = true),
                base.copy(targetLesson = base.targetLesson.copy(start = FixedTime.now(), end = FixedTime.now().plusMinutes(90))),
                signed,
                auto(real = signed.targetLesson),
                base.copy(id = 99, targetLesson = base.targetLesson.copy(id = 10))
            )
        )

        val pending = repository.observePendingBookings().first { it is AppResult.Success && it.value.isNotEmpty() } as AppResult.Success
        assertEquals(listOf(10L, 11L), pending.value.map { it.lessonId })
    }

    @Test fun `queue updates confirmation and opt out update observers without requiring friends data`() = runTest {
        val states = mutableListOf<AppResult<List<PendingSportBooking>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observePendingBookings().toList(states) }
        data.entries.value = LoadState.Content(listOf(SportCardFixtures.entry()))
        runCurrent()
        assertEquals(1, (states.last() as AppResult.Success).value.size)

        bookings.confirmed.value = AppResult.Success(listOf(SportCardFixtures.booking()))
        runCurrent()
        assertEquals(AppResult.Success(emptyList<PendingSportBooking>()), states.last())
        bookings.confirmed.value = AppResult.Success(emptyList())
        runCurrent()
        assertEquals(1, (states.last() as AppResult.Success).value.size)
        data.entries.value = LoadState.Content(listOf(SportCardFixtures.entry().copy(isCancelled = true)))
        runCurrent()
        assertEquals(AppResult.Success(emptyList<PendingSportBooking>()), states.last())

        data.entries.value = LoadState.Content(listOf(SportCardFixtures.entry()))
        runCurrent()
        services.enabled.value = false
        runCurrent()
        assertEquals(AppResult.Success(emptyList<PendingSportBooking>()), states.last())
    }

    @Test fun `source failures are not interpreted as no confirmed bookings`() = runTest {
        data.entries.value = LoadState.Content(listOf(SportCardFixtures.entry()))
        bookings.confirmed.value = AppResult.Failure(AppError.Unauthorized)
        assertEquals(AppResult.Failure(AppError.Unauthorized), repository.observePendingBookings().first { it is AppResult.Failure })
        bookings.confirmed.value = AppResult.Success(emptyList())
        data.entries.value = LoadState.Error(AppError.Network)
        assertEquals(AppResult.Failure(AppError.Network), repository.observePendingBookings().first { it is AppResult.Failure })
    }

    @Test fun `refresh requests official bookings and own queues only`() = runTest {
        repository.refresh()
        assertEquals(1, bookings.refreshes)
        assertEquals(1, data.refreshes)
    }

    @Test fun `refresh cancellation is propagated`() = runTest {
        bookings.cancelRefresh = true
        try {
            repository.refresh()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            // Structured concurrency cancels the sibling queue refresh as well.
        }
    }

    @Test fun `first snapshot after cold refresh reads actual replay without an active observer`() = runTest {
        val sources = useColdSources()
        val entry = SportCardFixtures.entry()
        bookings.refreshAction = { sources.confirmed.emit(AppResult.Success(emptyList())) }
        data.refreshAction = { sources.queues.emit(LoadState.Content(listOf(entry))) }

        repository.refresh()

        assertEquals(0, sources.confirmed.subscriptionCount.value)
        assertEquals(0, sources.queues.subscriptionCount.value)
        val snapshot = repository.getPendingBookings()
        assertTrue(snapshot is AppResult.Success)
        val pending = (snapshot as AppResult.Success).value.single()
        assertEquals(entry.id, pending.queueId)
        assertEquals(entry.lessonId, pending.lessonId)
        assertEquals(entry.targetLesson.start, pending.start)
        assertEquals(PendingSportBooking.QueueKind.FREE, pending.queueKind)
        assertEquals(1, bookings.refreshes)
        assertEquals(1, data.refreshes)
    }

    @Test fun `snapshot preserves source errors instead of returning synthetic empty data`() = runTest {
        val sources = useColdSources()
        sources.confirmed.emit(AppResult.Failure(AppError.Unauthorized))
        sources.queues.emit(LoadState.Content(listOf(SportCardFixtures.entry())))

        assertEquals(AppResult.Failure(AppError.Unauthorized), repository.getPendingBookings())

        sources.confirmed.emit(AppResult.Success(emptyList()))
        sources.queues.emit(LoadState.Error(AppError.Network))

        assertEquals(AppResult.Failure(AppError.Network), repository.getPendingBookings())
        assertEquals(0, bookings.refreshes)
        assertEquals(0, data.refreshes)
    }

    @Test fun `disabled snapshot returns immediately without subscribing to unseeded sources or requesting data`() = runTest {
        val sources = useColdSources()
        services.enabled.value = false
        val startedAt = currentTime

        assertEquals(AppResult.Success(emptyList<PendingSportBooking>()), repository.getPendingBookings())

        assertEquals(startedAt, currentTime)
        assertEquals(0, sources.confirmed.subscriptionCount.value)
        assertEquals(0, sources.queues.subscriptionCount.value)
        assertEquals(0, bookings.refreshes)
        assertEquals(0, data.refreshes)
    }

    @Test fun `snapshot before source replay times out after one second without making requests`() = runTest {
        useColdSources()
        val snapshot = async { repository.getPendingBookings() }
        runCurrent()
        assertFalse(snapshot.isCompleted)

        advanceTimeBy(999)
        runCurrent()
        assertFalse(snapshot.isCompleted)

        advanceTimeBy(1)
        runCurrent()
        assertTrue(snapshot.isCompleted)
        assertEquals(AppResult.Failure(AppError.Unknown()), snapshot.await())
        assertEquals(1_000L, currentTime)
        assertEquals(0, bookings.refreshes)
        assertEquals(0, data.refreshes)
    }

    @Test fun `snapshot propagates caller cancellation rather than returning an error snapshot`() = runTest {
        useColdSources()
        var returnedSnapshot = false
        var propagatedCancellation: CancellationException? = null
        val lookup = launch {
            try {
                repository.getPendingBookings()
                returnedSnapshot = true
            } catch (cancellation: CancellationException) {
                propagatedCancellation = cancellation
                throw cancellation
            }
        }
        runCurrent()
        assertFalse(lookup.isCompleted)

        lookup.cancelAndJoin()

        assertFalse(returnedSnapshot)
        assertNotNull(propagatedCancellation)
        assertEquals(0L, currentTime)
        assertEquals(0, bookings.refreshes)
        assertEquals(0, data.refreshes)
    }

    private fun useColdSources(): ColdSources = ColdSources().also {
        bookings.confirmedOutput = it.confirmed
        data.entriesOutput = it.queues
    }

    private class ColdSources {
        val confirmed = MutableSharedFlow<AppResult<List<SportBooking>>>(replay = 1)
        val queues = MutableSharedFlow<LoadState<List<SportQueueEntry>>>(replay = 1)
    }

    private fun auto(
        id: Long = 2,
        prototype: SportQueueLesson = SportCardFixtures.entry().targetLesson,
        real: SportQueueLesson? = null
    ) = SportAutoSignEntry(
        id = id, prototypeLessonId = prototype.id, realLessonId = real?.id,
        position = 1, total = 1, isCancelled = false, status = SportQueueEntryStatus.WAITING,
        createdAt = FixedTime.now(), firstNotifiedAt = null, lastNotifiedAt = null,
        cancelledAt = null, satisfiedAt = null, expiredAt = null, notificationAttempts = 0,
        maxNotificationAttempts = 10, targetLesson = prototype, realLesson = real
    )

    private object FixedTime : AcademicTimeProvider by FixedAcademicTime(LocalDateTime.of(2026, 9, 7, 12, 0))

    private class Bookings : SportBookingRepository {
        val confirmed = MutableStateFlow<AppResult<List<SportBooking>>>(AppResult.Success(emptyList()))
        var confirmedOutput: Flow<AppResult<List<SportBooking>>> = confirmed
        var refreshes = 0
        var cancelRefresh = false
        var refreshAction: suspend () -> Unit = {}
        override fun observeConfirmedSportBookings() = confirmedOutput
        override fun observeSportBookings(): Flow<LoadState<List<SportBooking>>> = error("Friend-enriched bookings are not required")
        override suspend fun refreshSportBookings() {
            if (cancelRefresh) throw CancellationException("Test cancellation")
            refreshes++
            refreshAction()
        }
    }

    private class SportData : SportDataRepository {
        val entries = MutableStateFlow<LoadState<List<SportQueueEntry>>>(LoadState.Content(emptyList()))
        var entriesOutput: Flow<LoadState<List<SportQueueEntry>>> = entries
        var refreshes = 0
        var refreshAction: suspend () -> Unit = {}
        override fun observeSportQueueEntries() = entriesOutput
        override suspend fun refreshSportQueueEntries() {
            refreshes++
            refreshAction()
        }
        override fun observeSportScore(): Flow<AppResult<SportScore>> = error("Not needed")
        override suspend fun refreshSportScore() = error("Not needed")
        override fun observeSportAttempts(): Flow<AppResult<SportAttempts>> = error("Not needed")
        override suspend fun refreshSportAttempts() = error("Not needed")
        override fun observeSportAutoSignLimits(): Flow<LoadState<SportAutoSignLimits>> = error("Not needed")
        override suspend fun refreshSportAutoSignLimits() = error("Not needed")
        override fun observeSportQueues(): Flow<LoadState<List<SportQueue>>> = error("Not needed")
        override suspend fun refreshSportQueues() = error("Not needed")
        override fun observeFriendsBookings(): Flow<LoadState<List<FriendSportBooking>>> = error("Not needed")
        override suspend fun refreshFriendsBookings() = error("Not needed")
    }
}
