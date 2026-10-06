package dev.alllexey.itmowidgets.feature.schedule.data.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

/**
 * Unread changes of lessons still ahead, from the local store only. The minute ticker drops a change once its lesson
 * is over; the check itself runs in the background, so [refresh] has nothing to ask.
 */
class ScheduleChangesHomeCardSource(
    private val repository: ScheduleChangesRepository,
    private val timeProvider: AcademicTimeProvider
) : HomeCardSource {

    override fun observe(): Flow<List<HomeCard>> = combine(ticker(), repository.observeChanges()) { _, changes ->
        val now = timeProvider.localNow()
        val unread = changes.filter { !it.read && !it.isOver(now) }
        val latest = unread.maxWithOrNull(NEWEST) ?: return@combine emptyList()
        listOf(HomeCard.ScheduleChanges(unread = unread.size, latest = latest))
    }

    override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun dismiss(kind: HomeCardKind) {
        if (kind == HomeCardKind.SCHEDULE_CHANGES) repository.markAllRead()
    }

    private fun ticker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MILLIS)
        }
    }

    private companion object {
        const val TICK_MILLIS = 60_000L

        /** The newest detection wins; within one check the sooner lesson does. */
        val NEWEST: Comparator<ScheduleChange> = compareBy<ScheduleChange> { it.detectedAt }
            .then(compareByDescending { it.soonestStart() })
    }
}
