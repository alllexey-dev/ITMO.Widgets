package dev.alllexey.itmowidgets.feature.sport.data.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.core.util.dataOrNull
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Score progress and own queues; gone once the semester is scored and nothing is queued. */
@Singleton
class SportHomeCardSource @Inject constructor(
    private val sportData: SportDataRepository,
    private val pending: PendingSportBookingsRepository,
    private val timeProvider: AcademicTimeProvider
) : HomeCardSource {

    override fun observe(): Flow<List<HomeCard>> = combine(
        sportData.observeSportScore().map { it.dataOrNull()?.summary },
        pending.observePendingBookings().map { it.dataOrNull().orEmpty() }
    ) { score, bookings ->
        val now = timeProvider.now()
        val queue = bookings
            .distinctBy { it.queueKind to it.queueId }
            .filter { it.end.isAfter(now) }
            .sortedBy { it.start }
        val scored = score != null && score.totalCapped >= SCORE_GOAL
        if ((score == null || scored) && queue.isEmpty()) emptyList()
        else listOf(HomeCard.Sport(score, queue))
    }

    override suspend fun refresh(): AppResult<Unit> = coroutineScope {
        val score = async { sportData.refreshSportScore() }
        val bookings = async { pending.refresh() }
        score.await()
        bookings.await()
        when (val state = sportData.observeSportScore().first()) {
            is DataState.Error -> AppResult.Failure(state.error)
            is DataState.Success -> AppResult.Success(Unit)
        }
    }

    private companion object {
        const val SCORE_GOAL = 100
    }
}
