package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.testing.FakeScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportActionRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportScheduleRepository
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SportBookingDelegateTest {

    private val scheduler = TestCoroutineScheduler()
    private val followUpScope = TestScope(StandardTestDispatcher(scheduler))
    private val actionRepository = FakeSportActionRepository()
    private val scheduleRefreshGateway = FakeScheduleRefreshGateway()
    private val bookingRepository = FakeSportBookingRepository()
    private val sportScheduleRepository = FakeSportScheduleRepository()
    private val dataRepository = FakeSportDataRepository()
    private var widgetRefreshCount = 0
    private val delegate = SportBookingDelegate(
        actionRepository = actionRepository,
        scheduleRefreshGateway = scheduleRefreshGateway,
        sportBookingRepository = bookingRepository,
        sportScheduleRepository = sportScheduleRepository,
        sportDataRepository = dataRepository,
        scheduleWidgetRefreshRequester = ScheduleWidgetRefreshRequester { widgetRefreshCount++ },
        timeProvider = FixedAcademicTime(),
        followUpScope = followUpScope
    )

    @AfterTest
    fun tearDown() {
        followUpScope.cancel()
    }

    @Test
    fun aSignUpFetchesTheScheduleBookingsAndCatalogueAgainAfterTheFollowUpDelayOutsideTheCaller() =
        runTest(scheduler) {
            delegate.signIn(lesson())
            assertEquals(1, scheduleRefreshGateway.requests.size)
            assertEquals(1, bookingRepository.refreshCount)

            followUpScope.advanceTimeBy(999)
            followUpScope.runCurrent()
            assertEquals(1, scheduleRefreshGateway.requests.size)

            followUpScope.advanceTimeBy(1)
            followUpScope.runCurrent()
            assertEquals(2, scheduleRefreshGateway.requests.size)
            assertEquals(2, bookingRepository.refreshCount)
            // The catalogue's free places lag like the bookings; only the widgets are not asked twice.
            assertEquals(2, sportScheduleRepository.scheduleRefreshCount)
            assertEquals(1, widgetRefreshCount)
        }

    @Test
    fun successfulSignInRefreshesEveryAffectedSchedule() = runTest {
        val result = delegate.signIn(lesson())

        assertTrue(result is AppResult.Success)
        assertEquals(listOf(1L), actionRepository.signedInLessons)
        assertEquals(1, bookingRepository.refreshCount)
        assertEquals(1, scheduleRefreshGateway.requests.size)
        assertEquals(1, sportScheduleRepository.scheduleRefreshCount)
        assertEquals(1, widgetRefreshCount)
    }

    @Test
    fun theScheduleRefreshAsksTheLessonSAcademicDatesNotItsUTCOnes() = runTest {
        delegate.signIn(lesson(start = Instant.parse("2026-07-21T21:30:00Z")))

        val day = LocalDate(2026, 7, 22)
        assertEquals(listOf(day to day), scheduleRefreshGateway.requests)
    }

    @Test
    fun failedSignInDoesNotRefreshStaleSources() = runTest {
        actionRepository.result = AppResult.Failure(AppError.Network)

        val result = delegate.signIn(lesson())

        assertEquals(AppResult.Failure(AppError.Network), result)
        assertEquals(0, bookingRepository.refreshCount)
        assertEquals(0, scheduleRefreshGateway.requests.size)
        assertEquals(0, sportScheduleRepository.scheduleRefreshCount)
        assertEquals(0, widgetRefreshCount)
    }

    @Test
    fun successfulSignOutRefreshesScheduleWidgets() = runTest {
        assertTrue(delegate.signOut(lesson()) is AppResult.Success)
        assertEquals(1, widgetRefreshCount)
        assertEquals(1, scheduleRefreshGateway.requests.size)
    }

    @Test
    fun cancelFromMySportRefreshesScheduleWidgets() = runTest {
        assertTrue(delegate.cancel(booking()) is AppResult.Success)
        assertEquals(1, widgetRefreshCount)
        assertEquals(1, scheduleRefreshGateway.requests.size)
    }

    @Test
    fun failedSignOutAndBookingCancellationLeaveWidgetsUnchanged() = runTest {
        actionRepository.result = AppResult.Failure(AppError.Network)
        assertTrue(delegate.signOut(lesson()) is AppResult.Failure)
        assertTrue(delegate.cancel(booking()) is AppResult.Failure)
        assertEquals(0, widgetRefreshCount)
        assertEquals(0, scheduleRefreshGateway.requests.size)
    }

    @Test
    fun queueSubscriptionsRefreshWidgetProjectionButNeverTheOfficialScheduleCache() = runTest {
        delegate.createFreeSign(1, false)
        delegate.cancelFreeSign(1)
        delegate.createAutoSign(1)
        delegate.cancelAutoSign(1)
        delegate.cancel(booking().copy(signed = false))
        assertEquals(5, widgetRefreshCount)
        assertEquals(0, scheduleRefreshGateway.requests.size)
    }

    @Test
    fun failedQueueMutationsNeverRefreshWidgetProjection() = runTest {
        actionRepository.result = AppResult.Failure(AppError.Network)
        assertTrue(delegate.createFreeSign(1, false) is AppResult.Failure)
        assertTrue(delegate.cancelFreeSign(1) is AppResult.Failure)
        assertTrue(delegate.createAutoSign(1) is AppResult.Failure)
        assertTrue(delegate.cancelAutoSign(1) is AppResult.Failure)
        assertEquals(0, widgetRefreshCount)
        assertEquals(0, dataRepository.entriesRefreshCount)
        assertEquals(0, scheduleRefreshGateway.requests.size)
    }

    @Test
    fun successfulBookingEnqueuesWidgetUpdateEvenWhenScreenScheduleRefreshFails() = runTest {
        scheduleRefreshGateway.result = AppResult.Failure(AppError.Network)
        assertTrue(delegate.signIn(lesson()) is AppResult.Success)
        assertEquals(1, widgetRefreshCount)
    }

    @Test
    fun backToBackBookingAndCancellationEachRequestAFreshWidgetSnapshot() = runTest {
        delegate.signIn(lesson())
        delegate.signOut(lesson())
        assertEquals(2, widgetRefreshCount)
    }

    private fun booking(): SportBooking = lesson().let {
        SportBooking(
            isLessonReal = it.isLessonReal, lessonId = it.lessonId, sectionName = it.sectionName,
            start = it.start, end = it.end, roomName = it.roomName, teacherFio = it.teacherFio,
            teacherIsu = it.teacherIsu, sectionLevel = it.sectionLevel, lessonLevel = it.lessonLevel,
            signed = true, signEntry = null, friendsBookings = emptyList()
        )
    }

    @Test
    fun autoSignAvailabilityIsReadAfterBothSourcesRefresh() = runTest {
        val result = delegate.loadAutoSignAvailability()

        assertTrue(result is AppResult.Success)
        assertEquals(1, dataRepository.limitsRefreshCount)
        assertEquals(1, dataRepository.entriesRefreshCount)
        assertEquals(2, (result as AppResult.Success).value.limits.available)
    }

    private fun lesson(start: Instant = Instant.parse("2026-07-22T10:00:00+03:00")): SportLesson {
        return SportLesson(
            isLessonReal = true,
            lessonId = 1,
            start = start,
            end = start + 1.hours,
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
}
