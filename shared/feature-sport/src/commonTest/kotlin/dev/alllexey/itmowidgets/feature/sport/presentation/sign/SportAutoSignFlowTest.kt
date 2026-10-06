package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueLesson
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportActionRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.bookingDelegate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime

class SportAutoSignFlowTest {

    private val data = FakeSportDataRepository()
    private val actions = FakeSportActionRepository()
    private val time = FixedAcademicTime(LocalDateTime(2026, 9, 8, 12, 0))

    private fun TestScope.flow() = SportAutoSignFlow(
        bookingDelegate(FakeSportBookingRepository(), FakeSportScheduleRepository(), data, this, actions),
        time
    )

    /** A predicted lesson two weeks after the fixture's Tuesday. */
    private val predicted: SportLesson = SportCardFixtures.lesson(7).let {
        it.copy(isLessonReal = false, start = it.start + 14.days, end = it.end + 14.days)
    }

    @Test
    fun withCustomServicesOffTheTapExplainsWhy() = runTest {
        actions.servicesEnabled = false

        assertEquals(SportAutoSignDecision.ServicesDisabled, flow().onClick(SportCardFixtures.lesson(1)))
    }

    @Test
    fun anActiveFreeSignEntryOffersToLeaveItsQueue() = runTest {
        val lesson = SportCardFixtures.lesson(1).copy(signEntry = SportCardFixtures.entry())

        assertEquals(
            SportAutoSignDecision.LeaveQueue(position = 3, total = 12, command = SportSignCommand.CancelFreeSign(1)),
            flow().onClick(lesson)
        )
    }

    @Test
    fun anActiveAutoSignEntryOffersToLeaveItsQueue() = runTest {
        val lesson = predicted.copy(signEntry = autoEntry(id = 4, target = predicted.start))

        assertEquals(
            SportAutoSignDecision.LeaveQueue(position = 2, total = 5, command = SportSignCommand.CancelAutoSign(4)),
            flow().onClick(lesson)
        )
    }

    @Test
    fun aRealLessonConfirmsAFreeSignAndTheSwitchReachesBackend() = runTest {
        val flow = flow()

        val decision = flow.onClick(SportCardFixtures.lesson(1))
        assertEquals(SportAutoSignDecision.ConfirmFreeSign(SportSignCommand.CreateFreeSign(1)), decision)

        val result = flow.execute((decision as SportAutoSignDecision.ConfirmFreeSign).command, forceSign = true)
        assertEquals(AppResult.Success(Unit), result)
        assertEquals(listOf(1L to true), actions.freeSignRequests)
    }

    @Test
    fun aPredictedLessonWithinTheLimitConfirmsAnAutoSign() = runTest {
        assertEquals(
            SportAutoSignDecision.ConfirmAutoSign(SportSignCommand.CreateAutoSign(7)),
            flow().onClick(predicted)
        )
    }

    @Test
    fun aDayThatAlreadyHasAnAutoSignNamesIt() = runTest {
        data.entries = listOf(autoEntry(id = 4, target = predicted.start + 3.hours))

        assertEquals(
            SportAutoSignDecision.DayTaken(
                section = SectionName("Фитнес (функциональная тренировка)").shorten(),
                teacher = "Тестовый преподаватель с длинным именем"
            ),
            flow().onClick(predicted)
        )
    }

    @Test
    fun aSpentLimitNamesTheNextDateInTheAcademicZone() = runTest {
        data.limits = SportAutoSignLimits(limit = 2, available = 0, nextAvailableAt = Instant.parse("2026-09-12T06:00:00Z"))

        assertEquals(SportAutoSignDecision.LimitReached("12 сент. 2026 г., 09:00:00"), flow().onClick(predicted))
    }

    private fun autoEntry(id: Long, target: Instant) = SportAutoSignEntry(
        id = id, prototypeLessonId = 7, realLessonId = null, position = 2, total = 5, isCancelled = false,
        status = SportQueueEntryStatus.WAITING, createdAt = target - 3.days, firstNotifiedAt = null,
        lastNotifiedAt = null, cancelledAt = null, satisfiedAt = null, expiredAt = null,
        notificationAttempts = 0, maxNotificationAttempts = 5,
        targetLesson = SportQueueLesson(
            id = 7, sectionId = 1, sectionName = "Фитнес (функциональная тренировка)", sectionLevel = 1, level = 1,
            typeId = 2, buildingId = 1, roomName = "Зал", start = target, end = target + 90.minutes,
            timeSlotId = 1, teacherIsu = 100, teacherFio = "Тестовый преподаватель с длинным именем"
        ),
        realLesson = null
    )
}
