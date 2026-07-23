package dev.alllexey.itmowidgets.feature.sport.sign

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.util.CustomDataState
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.core.util.MergedDataState
import dev.alllexey.itmowidgets.domain.model.schedule.DaySchedule
import dev.alllexey.itmowidgets.domain.model.sport.FriendSportBooking
import dev.alllexey.itmowidgets.domain.model.sport.SectionName
import dev.alllexey.itmowidgets.domain.model.sport.SportAttempts
import dev.alllexey.itmowidgets.domain.model.sport.SportAutoSignLimits
import dev.alllexey.itmowidgets.domain.model.sport.SportBooking
import dev.alllexey.itmowidgets.domain.model.sport.SportFilterCatalog
import dev.alllexey.itmowidgets.domain.model.sport.SportLesson
import dev.alllexey.itmowidgets.domain.model.sport.SportQueue
import dev.alllexey.itmowidgets.domain.model.sport.SportQueueEntry
import dev.alllexey.itmowidgets.domain.model.sport.SportScore
import dev.alllexey.itmowidgets.domain.model.sport.SportTimeSlot
import dev.alllexey.itmowidgets.domain.repository.ScheduleRepository
import dev.alllexey.itmowidgets.domain.repository.SportActionRepository
import dev.alllexey.itmowidgets.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.domain.repository.SportScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class SportBookingDelegateTest {

    private val actionRepository = FakeActionRepository()
    private val scheduleRepository = FakeScheduleRepository()
    private val bookingRepository = FakeSportBookingRepository()
    private val sportScheduleRepository = FakeSportScheduleRepository()
    private val dataRepository = FakeSportDataRepository()
    private val delegate = SportBookingDelegate(
        actionRepository = actionRepository,
        scheduleRepository = scheduleRepository,
        sportBookingRepository = bookingRepository,
        sportScheduleRepository = sportScheduleRepository,
        sportDataRepository = dataRepository
    )

    @Test
    fun `successful sign in refreshes every affected schedule`() = runTest {
        val result = delegate.signIn(lesson())

        assertTrue(result is AppResult.Success)
        assertEquals(listOf(1L), actionRepository.signedInLessons)
        assertEquals(1, bookingRepository.refreshCount)
        assertEquals(1, scheduleRepository.refreshCount)
        assertEquals(1, sportScheduleRepository.scheduleRefreshCount)
    }

    @Test
    fun `failed sign in does not refresh stale sources`() = runTest {
        actionRepository.result = AppResult.Failure(AppError.Network)

        val result = delegate.signIn(lesson())

        assertEquals(AppResult.Failure(AppError.Network), result)
        assertEquals(0, bookingRepository.refreshCount)
        assertEquals(0, scheduleRepository.refreshCount)
        assertEquals(0, sportScheduleRepository.scheduleRefreshCount)
    }

    @Test
    fun `auto sign availability is read after both sources refresh`() = runTest {
        val result = delegate.loadAutoSignAvailability()

        assertTrue(result is AppResult.Success)
        assertEquals(1, dataRepository.limitsRefreshCount)
        assertEquals(1, dataRepository.entriesRefreshCount)
        assertEquals(2, (result as AppResult.Success).value.limits.available)
    }

    private fun lesson(): SportLesson {
        val start = OffsetDateTime.parse("2026-07-22T10:00:00+03:00")
        return SportLesson(
            isLessonReal = true,
            lessonId = 1,
            start = start,
            end = start.plusHours(1),
            sectionId = 1,
            sectionName = SectionName("Плавание"),
            sectionLevel = 1,
            lessonGroupId = 1,
            lessonLevel = 1,
            typeId = 2,
            buildingId = 10,
            roomId = 1,
            roomName = "Аудитория",
            limit = 10,
            available = 5,
            comment = null,
            timeSlotId = 1,
            timeSlotStart = "10:00",
            timeSlotEnd = "11:00",
            intersection = false,
            canSignIn = true,
            unavailableReasons = emptyList(),
            signed = false,
            teacherIsu = 100,
            teacherFio = "Преподаватель",
            signEntry = null,
            signQueue = null,
            friendsBookings = emptyList()
        )
    }

    private class FakeActionRepository : SportActionRepository {
        var result: AppResult<Unit> = AppResult.Success(Unit)
        val signedInLessons = mutableListOf<Long>()

        override fun areCommunityServicesEnabled(): Boolean = true

        override suspend fun signIn(lessonId: Long): AppResult<Unit> {
            signedInLessons += lessonId
            return result
        }

        override suspend fun signOut(lessonId: Long): AppResult<Unit> = result

        override suspend fun createFreeSignEntry(
            lessonId: Long,
            forceSign: Boolean
        ): AppResult<Unit> = result

        override suspend fun cancelFreeSignEntry(entryId: Long): AppResult<Unit> = result

        override suspend fun createAutoSignEntry(
            prototypeLessonId: Long
        ): AppResult<Unit> = result

        override suspend fun cancelAutoSignEntry(entryId: Long): AppResult<Unit> = result
    }

    private class FakeScheduleRepository : ScheduleRepository {
        var refreshCount = 0

        override fun observeScheduleForRange(
            userIsu: Int?,
            startDate: LocalDate,
            endDate: LocalDate
        ): Flow<List<DaySchedule>> = flowOf(emptyList())

        override suspend fun refreshSchedule(
            userIsu: Int?,
            startDate: LocalDate,
            endDate: LocalDate
        ): AppResult<Unit> {
            refreshCount += 1
            return AppResult.Success(Unit)
        }

        override fun clearCaches() = Unit
    }

    private class FakeSportBookingRepository : SportBookingRepository {
        var refreshCount = 0

        override fun observeSportBookings(): Flow<MergedDataState<List<SportBooking>>> {
            return flowOf(MergedDataState.Success(emptyList()))
        }

        override suspend fun refreshSportBookings() {
            refreshCount += 1
        }
    }

    private class FakeSportScheduleRepository : SportScheduleRepository {
        var scheduleRefreshCount = 0

        override fun observeSportSchedule(): Flow<MergedDataState<List<SportLesson>>> {
            return flowOf(MergedDataState.Success(emptyList()))
        }

        override suspend fun refreshSportSchedule() {
            scheduleRefreshCount += 1
        }

        override fun observeSportFilters(): Flow<DataState<SportFilterCatalog>> {
            return flowOf(
                DataState.Success(
                    SportFilterCatalog(
                        buildings = emptyList(),
                        sections = emptyList(),
                        sportTypes = emptyList(),
                        teachers = emptyList()
                    )
                )
            )
        }

        override suspend fun refreshSportFilters() = Unit

        override fun observeSportTimeSlots(): Flow<DataState<List<SportTimeSlot>>> {
            return flowOf(DataState.Success(emptyList()))
        }

        override suspend fun refreshSportTimeSlots() = Unit
    }

    private class FakeSportDataRepository : SportDataRepository {
        var limitsRefreshCount = 0
        var entriesRefreshCount = 0
        private val limits = SportAutoSignLimits(
            limit = 3,
            available = 2,
            nextAvailableAt = OffsetDateTime.parse("2026-08-01T00:00:00+03:00")
        )

        override fun observeSportScore(): Flow<DataState<SportScore>> {
            return flowOf(DataState.Error(IllegalStateException()))
        }

        override suspend fun refreshSportScore() = Unit

        override fun observeSportAttempts(): Flow<DataState<SportAttempts>> {
            return flowOf(DataState.Error(IllegalStateException()))
        }

        override suspend fun refreshSportAttempts() = Unit

        override fun observeSportAutoSignLimits(): Flow<CustomDataState<SportAutoSignLimits>> {
            return flowOf(CustomDataState.Success(limits))
        }

        override suspend fun refreshSportAutoSignLimits() {
            limitsRefreshCount += 1
        }

        override fun observeSportQueueEntries(): Flow<CustomDataState<List<SportQueueEntry>>> {
            return flowOf(CustomDataState.Success(emptyList()))
        }

        override suspend fun refreshSportQueueEntries() {
            entriesRefreshCount += 1
        }

        override fun observeSportQueues(): Flow<CustomDataState<List<SportQueue>>> {
            return flowOf(CustomDataState.Success(emptyList()))
        }

        override suspend fun refreshSportQueues() = Unit

        override fun observeFriendsBookings(): Flow<CustomDataState<List<FriendSportBooking>>> {
            return flowOf(CustomDataState.Success(emptyList()))
        }

        override suspend fun refreshFriendsBookings() = Unit
    }
}
