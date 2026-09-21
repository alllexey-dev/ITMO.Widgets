package dev.alllexey.itmowidgets.feature.sport.presentation

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.CustomDataState
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.core.util.MergedDataState
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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf

/** Repositories whose refreshes can be held open, so a refresh is observable mid-flight. */
internal class FakeSportBookingRepository : SportBookingRepository {
    val confirmed = MutableSharedFlow<DataState<List<SportBooking>>>(replay = 1)
    val merged = MutableSharedFlow<MergedDataState<List<SportBooking>>>(replay = 1)
    var gate: CompletableDeferred<Unit> = CompletableDeferred(Unit)
    var refreshCount = 0

    override fun observeConfirmedSportBookings(): Flow<DataState<List<SportBooking>>> = confirmed
    override fun observeSportBookings(): Flow<MergedDataState<List<SportBooking>>> = merged
    override suspend fun refreshSportBookings() {
        refreshCount += 1
        gate.await()
    }
}

internal class FakeSportDataRepository : SportDataRepository {
    val score = MutableSharedFlow<DataState<SportScore>>(replay = 1)
    val attempts = MutableSharedFlow<DataState<SportAttempts>>(replay = 1)
    var gate: CompletableDeferred<Unit> = CompletableDeferred(Unit)

    override fun observeSportScore(): Flow<DataState<SportScore>> = score
    override suspend fun refreshSportScore() = gate.await()
    override fun observeSportAttempts(): Flow<DataState<SportAttempts>> = attempts
    override suspend fun refreshSportAttempts() = gate.await()
    override fun observeSportAutoSignLimits(): Flow<CustomDataState<SportAutoSignLimits>> =
        flowOf(CustomDataState.Success(SportAutoSignLimits(3, 2, OffsetDateTime.parse("2026-08-01T00:00:00+03:00"))))
    override suspend fun refreshSportAutoSignLimits() = Unit
    override fun observeSportQueueEntries(): Flow<CustomDataState<List<SportQueueEntry>>> = flowOf(CustomDataState.Success(emptyList()))
    override suspend fun refreshSportQueueEntries() = Unit
    override fun observeSportQueues(): Flow<CustomDataState<List<SportQueue>>> = flowOf(CustomDataState.Success(emptyList()))
    override suspend fun refreshSportQueues() = Unit
    override fun observeFriendsBookings(): Flow<CustomDataState<List<FriendSportBooking>>> = flowOf(CustomDataState.Success(emptyList()))
    override suspend fun refreshFriendsBookings() = Unit
}

internal class FakeSportScheduleRepository : SportScheduleRepository {
    val schedule = MutableSharedFlow<MergedDataState<List<SportLesson>>>(replay = 1)
    val filters = MutableSharedFlow<DataState<SportFilterCatalog>>(replay = 1)
    val timeSlots = MutableSharedFlow<DataState<List<SportTimeSlot>>>(replay = 1)
    var gate: CompletableDeferred<Unit> = CompletableDeferred(Unit)
    var scheduleRefreshCount = 0

    override fun observeSportSchedule(): Flow<MergedDataState<List<SportLesson>>> = schedule
    override suspend fun refreshSportSchedule() {
        scheduleRefreshCount += 1
        gate.await()
    }
    override fun observeSportCatalog(): Flow<DataState<List<SportLesson>>> = flowOf(DataState.Success(emptyList()))
    override fun observeSportFilters(): Flow<DataState<SportFilterCatalog>> = filters
    override suspend fun refreshSportFilters() = Unit
    override fun observeSportTimeSlots(): Flow<DataState<List<SportTimeSlot>>> = timeSlots
    override suspend fun refreshSportTimeSlots() = Unit
}

internal class FakeSportActionRepository : SportActionRepository {
    override suspend fun areCommunityServicesEnabled() = true
    override suspend fun signIn(lessonId: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun signOut(lessonId: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun createFreeSignEntry(lessonId: Long, forceSign: Boolean): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun cancelFreeSignEntry(entryId: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun createAutoSignEntry(prototypeLessonId: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun cancelAutoSignEntry(entryId: Long): AppResult<Unit> = AppResult.Success(Unit)
}

internal class FakeAcademicTimeProvider(private val today: LocalDate = LocalDate.of(2026, 9, 8)) : AcademicTimeProvider {
    override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")
    override fun today(): LocalDate = today
    override fun now(): OffsetDateTime = today.atTime(12, 0).atZone(zoneId).toOffsetDateTime()
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
    scheduleRefreshGateway = object : ScheduleRefreshGateway {
        override suspend fun refreshOwnSchedule(startDate: LocalDate, endDate: LocalDate): AppResult<Unit> = AppResult.Success(Unit)
    },
    sportBookingRepository = bookings,
    sportScheduleRepository = schedule,
    sportDataRepository = data,
    scheduleWidgetRefreshRequester = ScheduleWidgetRefreshRequester { },
    followUpScope = followUpScope,
)

internal fun emptyCatalog() = SportFilterCatalog(buildings = emptyList(), sections = emptyList(), sportTypes = emptyList(), teachers = emptyList())
