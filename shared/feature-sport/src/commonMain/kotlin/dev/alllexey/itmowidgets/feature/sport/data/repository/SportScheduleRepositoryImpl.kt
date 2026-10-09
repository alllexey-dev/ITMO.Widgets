package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmoapi.core.requireResult
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.errorOrNull
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.feature.sport.data.demo.DemoSport
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.data.mapper.toModel
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportLessonTemplateProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFreeSignQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus.Companion.notifiableStatuses
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportTimeSlot
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.feature.sport.domain.model.predictedEnd
import dev.alllexey.itmowidgets.feature.sport.domain.model.predictedStart
import dev.alllexey.itmowidgets.feature.sport.domain.model.repeats
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class SportScheduleRepositoryImpl(
    sportDataRepository: SportDataRepository,
    private val myItmo: MyItmoClient,
    private val timeProvider: AcademicTimeProvider,
    private val sportLessonTemplateProvider: SportLessonTemplateProvider,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : SportScheduleRepository {

    private val scheduleFlow = MutableSharedFlow<AppResult<Map<LocalDate, List<SportLesson>>>>(replay = 1)

    private val filtersFlow = MutableSharedFlow<AppResult<SportFilterCatalog>>(replay = 1)

    private val timeSlotsFlow = MutableSharedFlow<AppResult<List<SportTimeSlot>>>(replay = 1)

    private val combined = combine(
        scheduleFlow,
        sportDataRepository.observeSportQueueEntries(),
        sportDataRepository.observeSportQueues(),
        sportDataRepository.observeFriendsBookings()
    ) { schedulesState, queueEntries, queues, friendsBookings ->

        // schedules are required
        schedulesState.errorOrNull()?.let {
            return@combine LoadState.Error(it)
        }

        val schedules = schedulesState.valueOrNull().orEmpty()

        val freeSignEntries = queueEntries.valueOrNull().orEmpty()
            .mapNotNull { it as? SportFreeSignEntry }
            .associateBy { it.lessonId }
        val freeSignQueues = queues.valueOrNull().orEmpty()
            .mapNotNull { it as? SportFreeSignQueue }
            .associateBy { it.lessonId }
        val autoSignEntries = queueEntries.valueOrNull().orEmpty()
            .mapNotNull { it as? SportAutoSignEntry }
            .associateBy { it.prototypeLessonId }
        val autoSignQueues = queues.valueOrNull().orEmpty()
            .mapNotNull { it as? SportAutoSignQueue }
            .associateBy { it.lessonId }
        val friendsRealBookings = friendsBookings.valueOrNull().orEmpty()
            .filter { it.entry == null }
            .groupBy { it.lessonId }
        val friendsFreeSignBookings = friendsBookings.valueOrNull().orEmpty()
            .filter { it.entry is SportFreeSignEntry }
            .groupBy { it.lessonId }
        val friendsAutoSignBookings = friendsBookings.valueOrNull().orEmpty()
            .filter { it.entry is SportAutoSignEntry }
            .groupBy { it.lessonId }
        val friendsAutoSignBookingsByReal = friendsBookings.valueOrNull().orEmpty()
            .filter { it.entry is SportAutoSignEntry }
            .groupBy { (it.entry as SportAutoSignEntry).realLessonId }

        val errors = listOfNotNull(
            queueEntries.errorOrNull(),
            queues.errorOrNull(),
            friendsBookings.errorOrNull()
        )

        val realLessons = schedules.flatMap { it.value }
            // we show only free attendance sports
            .filter { it.isFreeAttendance() }
            .map {
                it.copy(
                    // add free sign state (if available)
                    signEntry = freeSignEntries[it.lessonId],
                    signQueue = freeSignQueues[it.lessonId],
                    // add friend bookings
                    friendsBookings = friendsRealBookings[it.lessonId].orEmpty() + friendsFreeSignBookings[it.lessonId].orEmpty() + friendsAutoSignBookingsByReal[it.lessonId].orEmpty()
                )
            }

        val realLessonsById = realLessons.associateBy { it.lessonId }.toMutableMap()
        val realLessonsByStart = realLessons.groupBy { it.start }
        val futureLessons = realLessons
            // map prototypes to already present lessons by key parameters
            .map { prototype ->
                prototype to realLessonsByStart[prototype.predictedStart()]?.firstOrNull { it.repeats(prototype) }
            }
            .mapNotNull { (prototype, real) ->
                val autoSignEntry =
                    autoSignEntries[prototype.lessonId]?.takeIf { it.status in notifiableStatuses }
                if (real != null) {
                    if (autoSignEntry != null) {
                        // user is still not auto-signed, show him his status
                        realLessonsById[real.lessonId] = real.copy(
                            signEntry = autoSignEntry,
                            signQueue = autoSignQueues[prototype.lessonId]
                        )
                    }
                    // do not create prototype for this lesson
                    null
                } else {
                    // there is no real lesson, create prototype copy
                    val reasons = prototype.unavailableReasons
                        .filterNot { it in listOf(UnavailableReason.Full, UnavailableReason.AlreadyEnrolled,
                            UnavailableReason.DailyLimitReached, UnavailableReason.WeeklyLimitReached,
                            UnavailableReason.LessonInPast, UnavailableReason.TimeConflict) }
                    prototype.copy(
                        isLessonReal = false,
                        // the user's booking of the prototype is not a booking of its repeat
                        signed = false,
                        start = prototype.predictedStart(),
                        end = prototype.predictedEnd(),
                        signEntry = autoSignEntry,
                        signQueue = autoSignQueues[prototype.lessonId],
                        friendsBookings = friendsAutoSignBookings[prototype.lessonId].orEmpty(),
                        unavailableReasons = reasons,
                        canSignIn = reasons.isEmpty()
                    )
                }
            }

        val lessons = realLessonsById.values + futureLessons
        LoadState.Content(lessons, errors.firstOrNull())
    }

    override fun observeSportSchedule() = combined

    override fun observeSportCatalog(): Flow<AppResult<List<SportLesson>>> = scheduleFlow.map { state ->
        when (state) {
            is AppResult.Success -> AppResult.Success(state.value.values.flatten().filter { it.isFreeAttendance() })
            is AppResult.Failure -> state
        }
    }

    override fun observeSportFilters() = filtersFlow

    override fun observeSportTimeSlots() = timeSlotsFlow

    override suspend fun refreshSportSchedule() {
        if (demo.isActive()) {
            scheduleFlow.emit(AppResult.Success(DemoSport.schedule(timeProvider)))
            return
        }
        sportLessonTemplateProvider.getSchedule()?.let { templates ->
            scheduleFlow.emit(AppResult.Success(templates))
            return
        }

        try {
            val result = withContext(dispatchers.io) {
                val from = timeProvider.today()
                val to = from.plus(DatePeriod(days = SCHEDULE_DAYS))
                val days = myItmo.sport.getSportSchedule(from, to).requireResult()
                val now = timeProvider.now()
                days.associate { day -> day.date to day.lessons.orEmpty().map { lesson -> lesson.toModel(now) } }
            }
            scheduleFlow.emit(AppResult.Success(result))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            scheduleFlow.emit(AppResult.Failure(error.toSportAppError()))
        }
    }

    override suspend fun refreshSportFilters() {
        if (demo.isActive()) {
            filtersFlow.emit(AppResult.Success(DemoSport.filters()))
            return
        }
        try {
            val result = withContext(dispatchers.io) { myItmo.sport.getSportFilters().requireResult().toModel() }
            filtersFlow.emit(AppResult.Success(result))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            filtersFlow.emit(AppResult.Failure(error.toSportAppError()))
        }
    }

    override suspend fun refreshSportTimeSlots() {
        if (demo.isActive()) {
            timeSlotsFlow.emit(AppResult.Success(DemoSport.timeSlots()))
            return
        }
        try {
            val result = withContext(dispatchers.io) { myItmo.sport.getSportTimeSlots().requireResult().map { it.toModel() } }
            timeSlotsFlow.emit(AppResult.Success(result))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            timeSlotsFlow.emit(AppResult.Failure(error.toSportAppError()))
        }
    }

    private companion object {
        /** Today and the three weeks after it, both ends inclusive. */
        const val SCHEDULE_DAYS = 21
    }
}
