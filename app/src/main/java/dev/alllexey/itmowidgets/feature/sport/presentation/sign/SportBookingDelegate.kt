package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.coroutines.ApplicationScope
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.util.dataOrNull
import dev.alllexey.itmowidgets.core.util.errorOrNull
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportActionRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AutoSignAvailability(
    val limits: SportAutoSignLimits,
    val entries: List<SportQueueEntry>
)

class SportBookingDelegate @Inject constructor(
    private val actionRepository: SportActionRepository,
    private val scheduleRefreshGateway: ScheduleRefreshGateway,
    private val sportBookingRepository: SportBookingRepository,
    private val sportScheduleRepository: SportScheduleRepository,
    private val sportDataRepository: SportDataRepository,
    private val scheduleWidgetRefreshRequester: ScheduleWidgetRefreshRequester,
    @ApplicationScope private val followUpScope: CoroutineScope
) {

    suspend fun areCommunityServicesEnabled(): Boolean {
        return actionRepository.areCommunityServicesEnabled()
    }

    suspend fun signIn(lesson: SportLesson): AppResult<Unit> {
        return actionRepository.signIn(lesson.lessonId).refreshOnSuccess {
            refreshMyItmoBookings(lesson)
        }
    }

    suspend fun signOut(lesson: SportLesson): AppResult<Unit> {
        return actionRepository.signOut(lesson.lessonId).refreshOnSuccess {
            refreshMyItmoBookings(lesson)
        }
    }

    suspend fun cancel(booking: SportBooking): AppResult<Unit> {
        val result = when {
            booking.signed -> actionRepository.signOut(booking.lessonId)
            booking.signEntry is SportFreeSignEntry ->
                actionRepository.cancelFreeSignEntry(booking.signEntry.id)
            booking.signEntry is SportAutoSignEntry ->
                actionRepository.cancelAutoSignEntry(booking.signEntry.id)
            else -> AppResult.Success(Unit)
        }

        return result.refreshOnSuccess {
            if (booking.signed) {
                refreshMyItmoBookings(booking)
            } else {
                refreshCommunityData()
            }
        }
    }

    suspend fun createFreeSign(
        lessonId: Long,
        forceSign: Boolean
    ): AppResult<Unit> {
        return actionRepository.createFreeSignEntry(lessonId, forceSign)
            .refreshOnSuccess(::refreshCommunityData)
    }

    suspend fun cancelFreeSign(entryId: Long): AppResult<Unit> {
        return actionRepository.cancelFreeSignEntry(entryId)
            .refreshOnSuccess(::refreshCommunityData)
    }

    suspend fun createAutoSign(prototypeLessonId: Long): AppResult<Unit> {
        return actionRepository.createAutoSignEntry(prototypeLessonId)
            .refreshOnSuccess(::refreshCommunityData)
    }

    suspend fun cancelAutoSign(entryId: Long): AppResult<Unit> {
        return actionRepository.cancelAutoSignEntry(entryId)
            .refreshOnSuccess(::refreshCommunityData)
    }

    suspend fun loadAutoSignAvailability(): AppResult<AutoSignAvailability> {
        coroutineScope {
            awaitAll(
                async { sportDataRepository.refreshSportAutoSignLimits() },
                async { sportDataRepository.refreshSportQueueEntries() }
            )
        }

        val limitsState = sportDataRepository.observeSportAutoSignLimits().first()
        val entriesState = sportDataRepository.observeSportQueueEntries().first()
        val limits = limitsState.dataOrNull()
        val entries = entriesState.dataOrNull()

        return if (limits != null && entries != null) {
            AppResult.Success(AutoSignAvailability(limits, entries))
        } else {
            AppResult.Failure(
                limitsState.errorOrNull()
                    ?: entriesState.errorOrNull()
                    ?: AppError.Unknown()
            )
        }
    }

    private suspend fun refreshMyItmoBookings(lesson: SportLesson) {
        refreshMyItmoBookings(
            startDate = lesson.start.toLocalDate(),
            endDate = lesson.end.toLocalDate()
        )
    }

    private suspend fun refreshMyItmoBookings(booking: SportBooking) {
        refreshMyItmoBookings(
            startDate = booking.start.toLocalDate(),
            endDate = booking.end.toLocalDate()
        )
    }

    private suspend fun refreshMyItmoBookings(
        startDate: java.time.LocalDate,
        endDate: java.time.LocalDate
    ) {
        // The booking has succeeded. Enqueue before UI refreshes so their failure/cancellation
        // cannot leave installed widgets stale; the worker fetches its own fresh schedule.
        scheduleWidgetRefreshRequester.refreshScheduleWidgets()
        coroutineScope {
            awaitAll(
                async { sportBookingRepository.refreshSportBookings() },
                async {
                    scheduleRefreshGateway.refreshOwnSchedule(startDate, endDate)
                },
                async { sportScheduleRepository.refreshSportSchedule() }
            )
        }
        // MyITMO often answers the first fetch with the state from before the change. The
        // second fetch runs outside the caller: the result is already back and the screen
        // may be gone by then.
        followUpScope.launch {
            delay(FOLLOW_UP_DELAY)
            coroutineScope {
                awaitAll(
                    async { sportBookingRepository.refreshSportBookings() },
                    async { scheduleRefreshGateway.refreshOwnSchedule(startDate, endDate) }
                )
            }
        }
    }

    private suspend fun refreshCommunityData() {
        // Queue changes affect the optional widget projection, not the official schedule cache.
        scheduleWidgetRefreshRequester.refreshScheduleWidgets()
        coroutineScope {
            awaitAll(
                async { sportDataRepository.refreshSportAutoSignLimits() },
                async { sportDataRepository.refreshSportQueueEntries() },
                async { sportDataRepository.refreshSportQueues() },
                async { sportDataRepository.refreshFriendsBookings() }
            )
        }
    }

    private companion object {
        val FOLLOW_UP_DELAY: Duration = 3.seconds
    }

    private suspend fun AppResult<Unit>.refreshOnSuccess(
        refresh: suspend () -> Unit
    ): AppResult<Unit> {
        if (this is AppResult.Success) refresh()
        return this
    }
}
