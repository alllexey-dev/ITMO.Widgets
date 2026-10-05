package dev.alllexey.itmowidgets.feature.sport.presentation

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.testing.FakeScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportSignDisplayOptions
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportTimeSlot
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportActionRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSignPreferencesRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportBookingDelegate
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** Repositories whose refreshes can be held open, so a refresh is observable mid-flight. */
internal class FakeSportBookingRepository : SportBookingRepository {
    val confirmed = MutableSharedFlow<AppResult<List<SportBooking>>>(replay = 1)
    val merged = MutableSharedFlow<LoadState<List<SportBooking>>>(replay = 1)
    var gate: CompletableDeferred<Unit> = CompletableDeferred(Unit)
    var refreshCount = 0

    override fun observeConfirmedSportBookings(): Flow<AppResult<List<SportBooking>>> = confirmed
    override fun observeSportBookings(): Flow<LoadState<List<SportBooking>>> = merged
    override suspend fun refreshSportBookings() {
        refreshCount += 1
        gate.await()
    }
}

internal class FakeSportDataRepository : SportDataRepository {
    val score = MutableSharedFlow<AppResult<SportScore>>(replay = 1)
    val attempts = MutableSharedFlow<AppResult<SportAttempts>>(replay = 1)
    var gate: CompletableDeferred<Unit> = CompletableDeferred(Unit)
    var limitsRefreshCount = 0
    var entriesRefreshCount = 0
    var limits = SportAutoSignLimits(3, 2, Instant.parse("2026-08-01T00:00:00+03:00"))

    override fun observeSportScore(): Flow<AppResult<SportScore>> = score
    override suspend fun refreshSportScore() = gate.await()
    override fun observeSportAttempts(): Flow<AppResult<SportAttempts>> = attempts
    override suspend fun refreshSportAttempts() = gate.await()
    override fun observeSportAutoSignLimits(): Flow<LoadState<SportAutoSignLimits>> =
        flow { emit(LoadState.Content(limits)) }
    override suspend fun refreshSportAutoSignLimits() {
        limitsRefreshCount += 1
    }
    override fun observeSportQueueEntries(): Flow<LoadState<List<SportQueueEntry>>> = flowOf(LoadState.Content(emptyList()))
    override suspend fun refreshSportQueueEntries() {
        entriesRefreshCount += 1
    }
    override fun observeSportQueues(): Flow<LoadState<List<SportQueue>>> = flowOf(LoadState.Content(emptyList()))
    override suspend fun refreshSportQueues() = Unit
    override fun observeFriendsBookings(): Flow<LoadState<List<FriendSportBooking>>> = flowOf(LoadState.Content(emptyList()))
    override suspend fun refreshFriendsBookings() = Unit
}

internal class FakeSportScheduleRepository : SportScheduleRepository {
    val schedule = MutableSharedFlow<LoadState<List<SportLesson>>>(replay = 1)
    val filters = MutableSharedFlow<AppResult<SportFilterCatalog>>(replay = 1)
    val timeSlots = MutableSharedFlow<AppResult<List<SportTimeSlot>>>(replay = 1)
    var gate: CompletableDeferred<Unit> = CompletableDeferred(Unit)
    var scheduleRefreshCount = 0

    override fun observeSportSchedule(): Flow<LoadState<List<SportLesson>>> = schedule
    override suspend fun refreshSportSchedule() {
        scheduleRefreshCount += 1
        gate.await()
    }
    override fun observeSportCatalog(): Flow<AppResult<List<SportLesson>>> = flowOf(AppResult.Success(emptyList()))
    override fun observeSportFilters(): Flow<AppResult<SportFilterCatalog>> = filters
    override suspend fun refreshSportFilters() = Unit
    override fun observeSportTimeSlots(): Flow<AppResult<List<SportTimeSlot>>> = timeSlots
    override suspend fun refreshSportTimeSlots() = Unit
}

/** Answers every action with [result]; sign-ins are recorded in [signedInLessons]. */
internal class FakeSportActionRepository : SportActionRepository {
    var result: AppResult<Unit> = AppResult.Success(Unit)
    val signedInLessons = mutableListOf<Long>()

    override suspend fun areCommunityServicesEnabled() = true
    override suspend fun signIn(lessonId: Long): AppResult<Unit> {
        signedInLessons += lessonId
        return result
    }
    override suspend fun signOut(lessonId: Long): AppResult<Unit> = result
    override suspend fun createFreeSignEntry(lessonId: Long, forceSign: Boolean): AppResult<Unit> = result
    override suspend fun cancelFreeSignEntry(entryId: Long): AppResult<Unit> = result
    override suspend fun createAutoSignEntry(prototypeLessonId: Long): AppResult<Unit> = result
    override suspend fun cancelAutoSignEntry(entryId: Long): AppResult<Unit> = result
}

internal object FakeSportSignPreferences : SportSignPreferencesRepository {
    override fun observeDisplayOptions(): Flow<SportSignDisplayOptions> = flowOf(SportSignDisplayOptions())
}

internal fun bookingDelegate(
    bookings: SportBookingRepository,
    schedule: SportScheduleRepository,
    data: SportDataRepository,
    followUpScope: CoroutineScope,
) = SportBookingDelegate(
    actionRepository = FakeSportActionRepository(),
    scheduleRefreshGateway = FakeScheduleRefreshGateway(),
    sportBookingRepository = bookings,
    sportScheduleRepository = schedule,
    sportDataRepository = data,
    scheduleWidgetRefreshRequester = ScheduleWidgetRefreshRequester { },
    timeProvider = FixedAcademicTime(),
    followUpScope = followUpScope,
)

internal fun emptyCatalog() = SportFilterCatalog(buildings = emptyList(), sections = emptyList(), sportTypes = emptyList(), teachers = emptyList())
