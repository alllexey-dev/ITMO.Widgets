package dev.alllexey.itmowidgets.feature.schedule.data.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.dataOrNull
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.home.HomeScheduleSelector
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Today and tomorrow from the schedule cache, re-evaluated every minute so the current lesson moves. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ScheduleHomeCardSource @Inject constructor(
    private val repository: ScheduleRepository,
    private val pending: PendingSportBookingsRepository,
    private val preferences: SchedulePreferencesRepository,
    private val timeProvider: AcademicTimeProvider,
    private val selector: HomeScheduleSelector
) : HomeCardSource {

    override fun observe(): Flow<List<HomeCard>> = ticker().flatMapLatest {
        val today = timeProvider.today()
        combine(
            repository.observeScheduleForRange(null, today, today.plusDays(1)),
            pendingRows()
        ) { days, bookings -> listOf(selector.select(days, bookings, timeProvider.now())) }
    }

    override suspend fun refresh(): AppResult<Unit> = coroutineScope {
        val today = timeProvider.today()
        val schedule = async { repository.refreshSchedule(null, today, today.plusDays(1)) }
        val bookings = async { pending.refresh() }
        bookings.await()
        schedule.await()
    }

    private fun pendingRows(): Flow<List<PendingSportBooking>> =
        preferences.observeSportAutoSignEnabled().flatMapLatest { enabled ->
            if (enabled) pending.observePendingBookings().map { it.dataOrNull().orEmpty() }
            else flowOf(emptyList())
        }

    private fun ticker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MILLIS)
        }
    }

    private companion object {
        const val TICK_MILLIS = 60_000L
    }
}
