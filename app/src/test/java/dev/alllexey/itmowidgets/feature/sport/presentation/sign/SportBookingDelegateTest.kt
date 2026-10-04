package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.testing.FakeScheduleRefreshGateway
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportActionRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportScheduleRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

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
        followUpScope = followUpScope
    )

    @After
    fun tearDown() {
        followUpScope.cancel()
    }

    @Test
    fun `a sign-up fetches the schedule, bookings and catalogue again after the follow-up delay, outside the caller`() =
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
    fun `successful sign in refreshes every affected schedule`() = runTest {
        val result = delegate.signIn(lesson())

        assertTrue(result is AppResult.Success)
        assertEquals(listOf(1L), actionRepository.signedInLessons)
        assertEquals(1, bookingRepository.refreshCount)
        assertEquals(1, scheduleRefreshGateway.requests.size)
        assertEquals(1, sportScheduleRepository.scheduleRefreshCount)
        assertEquals(1, widgetRefreshCount)
    }

    @Test
    fun `failed sign in does not refresh stale sources`() = runTest {
        actionRepository.result = AppResult.Failure(AppError.Network)

        val result = delegate.signIn(lesson())

        assertEquals(AppResult.Failure(AppError.Network), result)
        assertEquals(0, bookingRepository.refreshCount)
        assertEquals(0, scheduleRefreshGateway.requests.size)
        assertEquals(0, sportScheduleRepository.scheduleRefreshCount)
        assertEquals(0, widgetRefreshCount)
    }

    @Test
    fun `successful sign out refreshes schedule widgets`() = runTest {
        assertTrue(delegate.signOut(lesson()) is AppResult.Success)
        assertEquals(1, widgetRefreshCount)
        assertEquals(1, scheduleRefreshGateway.requests.size)
    }

    @Test
    fun `cancel from my sport refreshes schedule widgets`() = runTest {
        assertTrue(delegate.cancel(booking()) is AppResult.Success)
        assertEquals(1, widgetRefreshCount)
        assertEquals(1, scheduleRefreshGateway.requests.size)
    }

    @Test
    fun `failed sign out and booking cancellation leave widgets unchanged`() = runTest {
        actionRepository.result = AppResult.Failure(AppError.Network)
        assertTrue(delegate.signOut(lesson()) is AppResult.Failure)
        assertTrue(delegate.cancel(booking()) is AppResult.Failure)
        assertEquals(0, widgetRefreshCount)
        assertEquals(0, scheduleRefreshGateway.requests.size)
    }

    @Test
    fun `queue subscriptions refresh widget projection but never the official schedule cache`() = runTest {
        delegate.createFreeSign(1, false)
        delegate.cancelFreeSign(1)
        delegate.createAutoSign(1)
        delegate.cancelAutoSign(1)
        delegate.cancel(booking().copy(signed = false))
        assertEquals(5, widgetRefreshCount)
        assertEquals(0, scheduleRefreshGateway.requests.size)
    }

    @Test
    fun `failed queue mutations never refresh widget projection`() = runTest {
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
    fun `successful booking enqueues widget update even when screen schedule refresh fails`() = runTest {
        scheduleRefreshGateway.result = AppResult.Failure(AppError.Network)
        assertTrue(delegate.signIn(lesson()) is AppResult.Success)
        assertEquals(1, widgetRefreshCount)
    }

    @Test
    fun `back to back booking and cancellation each request a fresh widget snapshot`() = runTest {
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
}
