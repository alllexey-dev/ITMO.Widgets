package dev.alllexey.itmowidgets.feature.sport.cards

import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingObstacle
import dev.alllexey.itmowidgets.feature.sport.presentation.common.bookingConditions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

class SportBookingConditionsTest {
    private val lesson = SportCardFixtures.lesson()
    private val now = lesson.start - 1.days

    @Test fun academicScheduleOverlapWarnsButDoesNotBlockOfficialPermission() {
        val result = lesson.copy(intersection = true).bookingConditions().evaluate(now)
        assertTrue(result.manual)
        assertFalse(result.mayWait)
        assertEquals(SportBookingAction.SIGN, result.action)
    }

    @Test fun noPlacesAndUnpublishedPredictionsCanBeWaitedFor() {
        val full = lesson.copy(available = 0, canSignIn = false)
        assertTrue(full.bookingConditions().evaluate(now).mayWait)
        assertEquals(SportBookingAction.AUTO, full.bookingConditions().evaluate(now).action)
        val prediction = lesson.copy(isLessonReal = false, canSignIn = false, available = 0)
        assertTrue(prediction.bookingConditions().evaluate(now).mayWait)
        assertFalse(prediction.bookingConditions().evaluate(now).manual)
    }

    @Test fun allOfficialEligibilityRestrictionsPreventOfferingNewAutoSignEvenWithFullFirstOrLast() {
        val blockers = listOf(UnavailableReason.TimeConflict, UnavailableReason.DailyLimitReached,
            UnavailableReason.WeeklyLimitReached, UnavailableReason.CreditAchieved, UnavailableReason.SelectionFailed,
            UnavailableReason.ExternatOnly, UnavailableReason.DebtOnly, UnavailableReason.HealthGroupMismatch, UnavailableReason.LessonInPast,
            UnavailableReason.Other(" Новое ограничение "))
        blockers.forEach { reason ->
            listOf(listOf(UnavailableReason.Full, reason), listOf(reason, UnavailableReason.Full)).forEach { reasons ->
                listOf(true, false).forEach { real ->
                    val result = lesson.copy(isLessonReal = real, available = 0, canSignIn = false,
                        unavailableReasons = reasons).bookingConditions().evaluate(now)
                    assertEquals(SportBookingAction.NONE, result.action, reason::class.simpleName)
                    assertFalse(result.manual)
                    assertFalse(result.mayWait)
                    assertEquals(1, result.restrictions.size)
                }
            }
        }
    }

    @Test fun falsePermissionWithoutReasonAndContradictoryAllowedFlagAreConservative() {
        val unknown = lesson.copy(canSignIn = false).bookingConditions().evaluate(now)
        assertEquals(SportBookingObstacle.UNKNOWN, unknown.restrictions.single().kind)
        assertEquals(SportBookingAction.NONE, unknown.action)
        val contradictory = lesson.copy(unavailableReasons = listOf(UnavailableReason.HealthGroupMismatch))
            .bookingConditions().evaluate(now)
        assertEquals(SportBookingAction.NONE, contradictory.action)
    }

    @Test fun startingInstantBlocksStaleSnapshotButDoesNotTrapExistingQueue() {
        assertEquals(SportBookingAction.SIGN, lesson.bookingConditions().evaluate(lesson.start - 1.nanoseconds).action)
        assertEquals(SportBookingAction.NONE, lesson.bookingConditions().evaluate(lesson.start).action)
        assertEquals(SportBookingAction.NONE, lesson.bookingConditions().evaluate(lesson.start + 1.minutes).action)
        val queued = lesson.copy(signEntry = SportCardFixtures.entry(), canSignIn = false,
            unavailableReasons = listOf(UnavailableReason.HealthGroupMismatch))
        assertEquals(SportBookingAction.CANCEL_AUTO, queued.bookingConditions().evaluate(lesson.start).action)
        assertEquals(SportBookingAction.CANCEL, lesson.copy(signed = true).bookingConditions().evaluate(lesson.start).action)
    }

    @Test fun expiredAndCancelledQueuesDoNotRenderACancelAction() {
        listOf(SportCardFixtures.entry(SportQueueEntryStatus.EXPIRED),
            SportCardFixtures.entry().copy(isCancelled = true)).forEach { entry ->
            assertEquals(SportBookingAction.AUTO, lesson.copy(available = 0, canSignIn = false,
                signEntry = entry).bookingConditions().evaluate(now).action)
        }
    }

    @Test fun unknownServerTextIsTrimmedAndRepeatedBlockersCollapse() {
        val result = lesson.copy(unavailableReasons = listOf(UnavailableReason.Other("  Причина  "),
            UnavailableReason.Other("  Причина  "))).bookingConditions().evaluate(now)
        assertEquals("Причина", result.restrictions.single().detail)
    }
    @Test fun unrecognizedExplicitDenialIsNotMissingAvailabilityInformation() {
        val result = lesson.copy(canSignIn = false,
            unavailableReasons = listOf(UnavailableReason.Other("Нужен специальный допуск")))
            .bookingConditions().evaluate(now)
        assertEquals(SportBookingObstacle.DENIED, result.restrictions.single().kind)
        assertEquals(SportBookingAction.NONE, result.action)
    }

    @Test fun observedDebtOnlyReasonIsNormalizedAtTheMapperBoundary() {
        val reasons = UnavailableReason.getSortedUnavailableReasons(false, lesson.start, 0,
            listOf("  Занятие для студентов с задолженностью  "), now)
        assertEquals(listOf(UnavailableReason.Full, UnavailableReason.DebtOnly), reasons)
        val result = lesson.copy(canSignIn = false, unavailableReasons = reasons).bookingConditions().evaluate(now)
        assertEquals(SportBookingObstacle.DEBT_ONLY, result.restrictions.single().kind)
        assertEquals(SportBookingAction.NONE, result.action)
    }

    @Test fun debtClassTypeDoesNotInventPersonalIneligibilityWithoutAPIDenial() {
        assertTrue(lesson.copy(typeId = 5).bookingConditions().evaluate(now).manual)
    }

}
