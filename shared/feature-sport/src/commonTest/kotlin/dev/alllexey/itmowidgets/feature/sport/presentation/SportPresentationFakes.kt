package dev.alllexey.itmowidgets.feature.sport.presentation

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.schedule.DayLessonsGateway
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.testing.FakeScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.MySportSection
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportSectionDateState
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportSectionSlot
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
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSectionRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSignPreferencesRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportBookingDelegate
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.LocalDate

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
    val score = MutableSharedFlow<LoadState<SportScore>>(replay = 1)
    val attempts = MutableSharedFlow<LoadState<SportAttempts>>(replay = 1)
    var gate: CompletableDeferred<Unit> = CompletableDeferred(Unit)
    var limitsRefreshCount = 0
    var entriesRefreshCount = 0
    var limits = SportAutoSignLimits(3, 2, Instant.parse("2026-08-01T00:00:00+03:00"))
    var entries: List<SportQueueEntry> = emptyList()

    override fun observeSportScore(): Flow<LoadState<SportScore>> = score
    override suspend fun refreshSportScore() = gate.await()
    override fun observeSportAttempts(): Flow<LoadState<SportAttempts>> = attempts
    override suspend fun refreshSportAttempts() = gate.await()
    override fun observeSportAutoSignLimits(): Flow<LoadState<SportAutoSignLimits>> =
        flow { emit(LoadState.Content(limits)) }
    override suspend fun refreshSportAutoSignLimits() {
        limitsRefreshCount += 1
    }
    override fun observeSportQueueEntries(): Flow<LoadState<List<SportQueueEntry>>> =
        flow { emit(LoadState.Content(entries)) }
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

/** Answers every action with [result] once [gate] opens; sign-ins and free-sign requests are recorded. */
internal class FakeSportActionRepository : SportActionRepository {
    var result: AppResult<Unit> = AppResult.Success(Unit)
    var gate: CompletableDeferred<Unit> = CompletableDeferred(Unit)
    var servicesEnabled = true
    val signedInLessons = mutableListOf<Long>()
    /** Lesson id to `forceSign`. */
    val freeSignRequests = mutableListOf<Pair<Long, Boolean>>()

    override suspend fun areCommunityServicesEnabled() = servicesEnabled
    override suspend fun signIn(lessonId: Long): AppResult<Unit> {
        signedInLessons += lessonId
        gate.await()
        return result
    }
    override suspend fun signOut(lessonId: Long): AppResult<Unit> = result
    override suspend fun createFreeSignEntry(lessonId: Long, forceSign: Boolean): AppResult<Unit> {
        freeSignRequests += lessonId to forceSign
        return result
    }
    override suspend fun cancelFreeSignEntry(entryId: Long): AppResult<Unit> = result
    override suspend fun createAutoSignEntry(prototypeLessonId: Long): AppResult<Unit> = result
    override suspend fun cancelAutoSignEntry(entryId: Long): AppResult<Unit> = result
}

internal class FakeSportSignPreferences : SportSignPreferencesRepository {
    val display = MutableStateFlow(SportSignDisplayOptions())

    override fun observeDisplayOptions(): Flow<SportSignDisplayOptions> = display
}

/** The own timetable as the schedule cache has it. */
internal class FakeDayLessonsGateway(vararg days: LessonDay) : DayLessonsGateway {
    val days = MutableStateFlow(days.toList())

    override fun observe(from: LocalDate, to: LocalDate): Flow<List<LessonDay>> = days
    override suspend fun refresh(from: LocalDate, to: LocalDate): AppResult<Unit> = AppResult.Success(Unit)
}

/** Backend's sections in memory: a slot per lesson, one section per subscribed slot; [failure] refuses changes. */
internal class FakeSportSectionRepository : SportSectionRepository {
    val sections = MutableStateFlow<LoadState<List<MySportSection>>>(LoadState.Content(emptyList()))
    val slots = mutableMapOf<Long, SportSectionSlot>()
    val requests = mutableListOf<String>()
    var failure: AppError? = null
    var refreshCount = 0

    override fun observeSections(): Flow<LoadState<List<MySportSection>>> = sections
    override suspend fun refreshSections() {
        refreshCount += 1
    }
    override suspend fun slots(lessonIds: List<Long>): AppResult<List<SportSectionSlot>> =
        AppResult.Success(lessonIds.mapNotNull { slots[it] })

    override suspend fun create(lessonId: Long): AppResult<MySportSection> {
        requests += "create $lessonId"
        failure?.let { return AppResult.Failure(it) }
        val slot = slots.getValue(lessonId)
        val id = 100 + lessonId
        slots[lessonId] = slot.copy(subscribers = slot.subscribers + 1, subscriptionId = id, place = slot.subscribers + 1)
        val section = MySportSection(id, queueLesson(lessonId), emptyList(), place = slot.subscribers + 1, subscribers = slot.subscribers + 1)
        sections.value = LoadState.Content(sections.value.valueOrNull().orEmpty() + section)
        return AppResult.Success(section)
    }

    override suspend fun setSkipped(sectionId: Long, start: Instant, skipped: Boolean): AppResult<MySportSection> {
        requests += "date $sectionId $start ${if (skipped) "off" else "on"}"
        failure?.let { return AppResult.Failure(it) }
        val current = sections.value.valueOrNull().orEmpty()
        val section = current.single { it.id == sectionId }.let { section ->
            section.copy(
                dates = section.dates.map { date ->
                    if (date.start != start) date else date.copy(state = if (skipped) SportSectionDateState.SKIPPED else SportSectionDateState.QUEUED)
                },
            )
        }
        sections.value = LoadState.Content(current.map { if (it.id == sectionId) section else it })
        return AppResult.Success(section)
    }

    override suspend fun stop(sectionId: Long): AppResult<Unit> {
        requests += "stop $sectionId"
        failure?.let { return AppResult.Failure(it) }
        sections.value = LoadState.Content(sections.value.valueOrNull().orEmpty().filter { it.id != sectionId })
        slots.replaceAll { _, slot ->
            if (slot.subscriptionId == sectionId) slot.copy(subscribers = slot.subscribers - 1, subscriptionId = null, place = null) else slot
        }
        return AppResult.Success(Unit)
    }

    /** Wednesday's volleyball of [lessonId] at 18:30, the fixture lessons' slot. */
    fun queueLesson(lessonId: Long) = SportQueueLesson(
        id = lessonId, sectionId = 41, sectionName = "Волейбол", sectionLevel = 1, level = 1, typeId = 2, buildingId = 13,
        roomName = "спортивный зал", start = Instant.parse("2026-09-09T18:30:00+03:00"), end = Instant.parse("2026-09-09T20:00:00+03:00"),
        timeSlotId = 3, teacherIsu = 200001, teacherFio = "Тихонова Марина Юрьевна",
    )
}

internal fun bookingDelegate(
    bookings: SportBookingRepository,
    schedule: SportScheduleRepository,
    data: SportDataRepository,
    followUpScope: CoroutineScope,
    actions: SportActionRepository = FakeSportActionRepository(),
) = SportBookingDelegate(
    actionRepository = actions,
    scheduleRefreshGateway = FakeScheduleRefreshGateway(),
    sportBookingRepository = bookings,
    sportScheduleRepository = schedule,
    sportDataRepository = data,
    scheduleWidgetRefreshRequester = ScheduleWidgetRefreshRequester { },
    timeProvider = FixedAcademicTime(),
    followUpScope = followUpScope,
)

internal fun emptyCatalog() = SportFilterCatalog(buildings = emptyList(), sections = emptyList(), sportTypes = emptyList(), teachers = emptyList())

/**
 * The application scope of a test: on the test's scheduler, so `advanceUntilIdle` runs its work (it skips
 * `backgroundScope`'s), but outside the test's job, so its endless collectors do not keep `runTest` waiting.
 * It ends with the test.
 */
internal fun TestScope.applicationScope(): CoroutineScope {
    val job = SupervisorJob()
    backgroundScope.coroutineContext.job.invokeOnCompletion { job.cancel() }
    return CoroutineScope(coroutineContext + job)
}
