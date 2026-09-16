package dev.alllexey.itmowidgets.feature.sport.data.repository

import api.myitmo.MyItmoApi
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.core.util.MergedDataState
import dev.alllexey.itmowidgets.core.util.dataOrNull
import dev.alllexey.itmowidgets.core.util.errorOrNull
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
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

class SportScheduleRepositoryImpl @Inject constructor(
    sportDataRepository: SportDataRepository,
    private val myItmoApi: MyItmoApi,
    private val timeProvider: AcademicTimeProvider,
    private val sportLessonTemplateProvider: SportLessonTemplateProvider
) : SportScheduleRepository {

    private val scheduleFlow = MutableSharedFlow<DataState<Map<LocalDate, List<SportLesson>>>>(replay = 1)

    private val filtersFlow = MutableSharedFlow<DataState<SportFilterCatalog>>(replay = 1)

    private val timeSlotsFlow = MutableSharedFlow<DataState<List<SportTimeSlot>>>(replay = 1)

    private val combined = combine(
        scheduleFlow,
        sportDataRepository.observeSportQueueEntries(),
        sportDataRepository.observeSportQueues(),
        sportDataRepository.observeFriendsBookings()
    ) { schedulesState, queueEntries, queues, friendsBookings ->

        // schedules are required
        schedulesState.errorOrNull()?.let {
            return@combine MergedDataState.Error(it)
        }

        val schedules = schedulesState.dataOrNull().orEmpty()

        val freeSignEntries = queueEntries.dataOrNull().orEmpty()
            .mapNotNull { it as? SportFreeSignEntry }
            .associateBy { it.lessonId }
        val freeSignQueues = queues.dataOrNull().orEmpty()
            .mapNotNull { it as? SportFreeSignQueue }
            .associateBy { it.lessonId }
        val autoSignEntries = queueEntries.dataOrNull().orEmpty()
            .mapNotNull { it as? SportAutoSignEntry }
            .associateBy { it.prototypeLessonId }
        val autoSignQueues = queues.dataOrNull().orEmpty()
            .mapNotNull { it as? SportAutoSignQueue }
            .associateBy { it.lessonId }
        val friendsRealBookings = friendsBookings.dataOrNull().orEmpty()
            .filter { it.entry == null }
            .groupBy { it.lessonId }
        val friendsFreeSignBookings = friendsBookings.dataOrNull().orEmpty()
            .filter { it.entry is SportFreeSignEntry }
            .groupBy { it.lessonId }
        val friendsAutoSignBookings = friendsBookings.dataOrNull().orEmpty()
            .filter { it.entry is SportAutoSignEntry }
            .groupBy { it.lessonId }
        val friendsAutoSignBookingsByReal = friendsBookings.dataOrNull().orEmpty()
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
                prototype to realLessonsByStart[prototype.start.plusDays(14)]?.firstOrNull {
                    it.sectionId == prototype.sectionId
                            && it.teacherIsu == prototype.teacherIsu
                            && it.sectionLevel == prototype.sectionLevel
                            && it.lessonLevel == prototype.lessonLevel
                            && it.typeId == prototype.typeId
                            && it.timeSlotId == prototype.timeSlotId
                }
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
                        start = prototype.start.plusDays(14),
                        end = prototype.end.plusDays(14),
                        signEntry = autoSignEntry,
                        signQueue = autoSignQueues[prototype.lessonId],
                        friendsBookings = friendsAutoSignBookings[prototype.lessonId].orEmpty(),
                        unavailableReasons = reasons,
                        canSignIn = reasons.isEmpty()
                    )
                }
            }

        val lessons = realLessonsById.values + futureLessons
        MergedDataState.of(lessons, errors.firstOrNull())
    }

    override fun observeSportSchedule() = combined

    override fun observeSportCatalog(): Flow<DataState<List<SportLesson>>> = scheduleFlow.map { state ->
        when (state) {
            is DataState.Success -> DataState.Success(state.data.values.flatten().filter { it.isFreeAttendance() })
            is DataState.Error -> state
        }
    }

    override fun observeSportFilters() = filtersFlow

    override fun observeSportTimeSlots() = timeSlotsFlow

    override suspend fun refreshSportSchedule() {
        sportLessonTemplateProvider.getSchedule()?.let { templates ->
            scheduleFlow.emit(DataState.Success(templates))
            return
        }

        try {
            val result = withContext(Dispatchers.IO) {
                val from = timeProvider.today()
                val to = from.plusDays(21)

                val response = myItmoApi
                    .getSportSchedule(from, to, null, null, null)
                    .execute()
                val now = timeProvider.now()

                response.body()?.result?.associate {
                    it.date to it.lessons.orEmpty().map { lesson ->
                        lesson.toModel(now)
                    }
                }
            }

            if (result != null) {
                scheduleFlow.emit(DataState.Success(result))
            } else {
                throw RuntimeException("SportSchedule response is null")
            }

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            scheduleFlow.emit(DataState.Error(error.toAppError()))
        }
    }

    override suspend fun refreshSportFilters() {
        try {
            val result = withContext(Dispatchers.IO) {
                val response = myItmoApi
                    .sportFilters
                    .execute()

                response.body()?.result?.toModel()
            }

            if (result != null) {
                filtersFlow.emit(DataState.Success(result))
            } else {
                throw RuntimeException("SportFilters response is null")
            }

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            filtersFlow.emit(DataState.Error(error.toAppError()))
        }
    }

    override suspend fun refreshSportTimeSlots() {
        try {
            val result = withContext(Dispatchers.IO) {
                val response = myItmoApi
                    .sportTimeSlots
                    .execute()

                response.body()?.result?.map { it.toModel() }
            }

            if (result != null) {
                timeSlotsFlow.emit(DataState.Success(result))
            } else {
                throw RuntimeException("SportTimeSlots response is null")
            }

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            timeSlotsFlow.emit(DataState.Error(error.toAppError()))
        }
    }
}
