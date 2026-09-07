package dev.alllexey.itmowidgets.feature.sport.cards

import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingObstacle
import dev.alllexey.itmowidgets.feature.sport.presentation.common.bookingConditions
import org.junit.Assert.*
import org.junit.Test

class SportBookingConditionsTest {
    private val lesson = SportCardFixtures.lesson()
    private val now = lesson.start.minusDays(1)

    @Test fun `academic schedule overlap warns but does not block official permission`() {
        val result = lesson.copy(intersection = true).bookingConditions().evaluate(now)
        assertTrue(result.manual)
        assertFalse(result.mayWait)
        assertEquals(SportBookingAction.SIGN, result.action)
    }

    @Test fun `no places and unpublished predictions can be waited for`() {
        val full = lesson.copy(available = 0, canSignIn = false)
        assertTrue(full.bookingConditions().evaluate(now).mayWait)
        assertEquals(SportBookingAction.AUTO, full.bookingConditions().evaluate(now).action)
        val prediction = lesson.copy(isLessonReal = false, canSignIn = false, available = 0)
        assertTrue(prediction.bookingConditions().evaluate(now).mayWait)
        assertFalse(prediction.bookingConditions().evaluate(now).manual)
    }

    @Test fun `all official eligibility restrictions prevent offering new auto sign even with full first or last`() {
        val blockers = listOf(UnavailableReason.TimeConflict, UnavailableReason.DailyLimitReached,
            UnavailableReason.WeeklyLimitReached, UnavailableReason.CreditAchieved, UnavailableReason.SelectionFailed,
            UnavailableReason.ExternatOnly, UnavailableReason.DebtOnly, UnavailableReason.HealthGroupMismatch, UnavailableReason.LessonInPast,
            UnavailableReason.Other(" Новое ограничение "))
        blockers.forEach { reason ->
            listOf(listOf(UnavailableReason.Full, reason), listOf(reason, UnavailableReason.Full)).forEach { reasons ->
                listOf(true, false).forEach { real ->
                    val result = lesson.copy(isLessonReal = real, available = 0, canSignIn = false,
                        unavailableReasons = reasons).bookingConditions().evaluate(now)
                    assertEquals(reason.shortDescription, SportBookingAction.NONE, result.action)
                    assertFalse(result.manual)
                    assertFalse(result.mayWait)
                    assertEquals(1, result.restrictions.size)
                }
            }
        }
    }

    @Test fun `false permission without reason and contradictory allowed flag are conservative`() {
        val unknown = lesson.copy(canSignIn = false).bookingConditions().evaluate(now)
        assertEquals(SportBookingObstacle.UNKNOWN, unknown.restrictions.single().kind)
        assertEquals(SportBookingAction.NONE, unknown.action)
        val contradictory = lesson.copy(unavailableReasons = listOf(UnavailableReason.HealthGroupMismatch))
            .bookingConditions().evaluate(now)
        assertEquals(SportBookingAction.NONE, contradictory.action)
    }

    @Test fun `starting instant blocks stale snapshot but does not trap existing queue`() {
        assertEquals(SportBookingAction.SIGN, lesson.bookingConditions().evaluate(lesson.start.minusNanos(1)).action)
        assertEquals(SportBookingAction.NONE, lesson.bookingConditions().evaluate(lesson.start).action)
        assertEquals(SportBookingAction.NONE, lesson.bookingConditions().evaluate(lesson.start.plusMinutes(1)).action)
        val queued = lesson.copy(signEntry = SportCardFixtures.entry(), canSignIn = false,
            unavailableReasons = listOf(UnavailableReason.HealthGroupMismatch))
        assertEquals(SportBookingAction.CANCEL_AUTO, queued.bookingConditions().evaluate(lesson.start).action)
        assertEquals(SportBookingAction.CANCEL, lesson.copy(signed = true).bookingConditions().evaluate(lesson.start).action)
    }

    @Test fun `expired and cancelled queues do not render a cancel action`() {
        listOf(SportCardFixtures.entry(SportQueueEntryStatus.EXPIRED),
            SportCardFixtures.entry().copy(isCancelled = true)).forEach { entry ->
            assertEquals(SportBookingAction.AUTO, lesson.copy(available = 0, canSignIn = false,
                signEntry = entry).bookingConditions().evaluate(now).action)
        }
    }

    @Test fun `unknown server text is trimmed and repeated blockers collapse`() {
        val result = lesson.copy(unavailableReasons = listOf(UnavailableReason.Other("  Причина  "),
            UnavailableReason.Other("  Причина  "))).bookingConditions().evaluate(now)
        assertEquals("Причина", result.restrictions.single().detail)
    }
    @Test fun `unrecognized explicit denial is not missing availability information`() {
        val result = lesson.copy(canSignIn = false,
            unavailableReasons = listOf(UnavailableReason.Other("Нужен специальный допуск")))
            .bookingConditions().evaluate(now)
        assertEquals(SportBookingObstacle.DENIED, result.restrictions.single().kind)
        assertEquals(SportBookingAction.NONE, result.action)
    }

    @Test fun `observed debt only reason is normalized at the mapper boundary`() {
        val reasons = UnavailableReason.getSortedUnavailableReasons(false, lesson.start, 0,
            listOf("  Занятие для студентов с задолженностью  "), now)
        assertEquals(listOf(UnavailableReason.Full, UnavailableReason.DebtOnly), reasons)
        val result = lesson.copy(canSignIn = false, unavailableReasons = reasons).bookingConditions().evaluate(now)
        assertEquals(SportBookingObstacle.DEBT_ONLY, result.restrictions.single().kind)
        assertEquals(SportBookingAction.NONE, result.action)
    }

    @Test fun `debt class type does not invent personal ineligibility without API denial`() {
        assertTrue(lesson.copy(typeId = 5).bookingConditions().evaluate(now).manual)
    }

}
