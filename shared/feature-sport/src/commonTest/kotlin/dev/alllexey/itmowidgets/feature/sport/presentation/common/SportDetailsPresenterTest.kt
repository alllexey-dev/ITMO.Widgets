package dev.alllexey.itmowidgets.feature.sport.presentation.common

import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The details sheet's cases at state level; the View test of the sheet keeps only what needs a window. */
class SportDetailsPresenterTest {

    /** The preview's clock: the day before the fixture lesson, at noon in Moscow. */
    private val now = Instant.parse("2026-09-07T12:00:00+03:00")
    private val lesson = SportCardFixtures.lesson()

    private fun state(
        item: SportCommon,
        at: Instant = now,
        actionsEnabled: Boolean = true,
        busy: Boolean = false,
        submitted: Boolean = false
    ) = SportDetailsPresenter.present(item.toDetailsArgs(), at, actionsEnabled, busy, submitted)

    @Test fun bookingConditionsSeparateWarningsWaitingAndRestrictions() {
        val cases = listOf(
            lesson.copy(intersection = true) to
                listOf(SportDetailsCondition.Allowed, SportDetailsCondition.ScheduleOverlap),
            lesson.copy(available = 0, canSignIn = false) to
                listOf(SportDetailsCondition.Waiting(predicted = false)),
            lesson.copy(canSignIn = false, unavailableReasons = listOf(UnavailableReason.HealthGroupMismatch)) to
                listOf(SportDetailsCondition.Restricted(listOf(SportBookingRestriction(SportBookingObstacle.HEALTH)))),
            lesson.copy(isLessonReal = false, canSignIn = false) to
                listOf(SportDetailsCondition.Waiting(predicted = true)),
            lesson.copy(canSignIn = false) to
                listOf(SportDetailsCondition.Uncertain),
            lesson.copy(typeId = 5, available = 0, canSignIn = false, unavailableReasons = listOf(UnavailableReason.DebtOnly)) to
                listOf(SportDetailsCondition.Restricted(listOf(SportBookingRestriction(SportBookingObstacle.DEBT_ONLY)))),
            lesson.copy(canSignIn = false, unavailableReasons = listOf(UnavailableReason.Other("Явный запрет MyITMO"))) to
                listOf(SportDetailsCondition.Restricted(
                    listOf(SportBookingRestriction(SportBookingObstacle.DENIED, "Явный запрет MyITMO"))
                ))
        )
        cases.forEachIndexed { index, (item, expected) ->
            assertEquals(expected, state(item).conditions, "case $index")
        }
    }

    @Test fun aRestrictedQueueStillOffersItsOwnCancellation() {
        val blockedQueue = lesson.copy(available = 0, canSignIn = false,
            unavailableReasons = listOf(UnavailableReason.HealthGroupMismatch, UnavailableReason.Full),
            signEntry = SportCardFixtures.entry())
        assertEquals(SportDetailsAction(SportBookingAction.CANCEL_AUTO, enabled = true), state(blockedQueue).action)
        assertEquals(
            listOf(SportDetailsCondition.Restricted(listOf(SportBookingRestriction(SportBookingObstacle.HEALTH)))),
            state(blockedQueue).conditions
        )
    }

    @Test fun theLateQueueWarningStartsAnHourBeforeAFullRealLesson() {
        val full = lesson.copy(available = 0, canSignIn = false)
        val waiting = SportDetailsCondition.Waiting(predicted = false)
        assertEquals(listOf(waiting), state(full, at = full.start - 61.minutes).conditions)
        assertEquals(listOf(waiting, SportDetailsCondition.LateAuto), state(full, at = full.start - 1.hours).conditions)
        assertEquals(listOf(waiting, SportDetailsCondition.LateAuto), state(full, at = full.start - 1.minutes).conditions)
        val predicted = full.copy(isLessonReal = false)
        assertEquals(listOf(SportDetailsCondition.Waiting(predicted = true)),
            state(predicted, at = full.start - 1.minutes).conditions)
    }

    @Test fun aStartedLessonAndASignedOneExplainNoOffer() {
        assertEquals(listOf(SportDetailsCondition.Started), state(lesson, at = lesson.start).conditions)
        assertEquals(emptyList<SportDetailsCondition>(), state(lesson.copy(signed = true)).conditions)
        assertEquals(listOf(SportDetailsCondition.ScheduleOverlap),
            state(lesson.copy(signed = true, intersection = true)).conditions)
        assertEquals(emptyList<SportDetailsCondition>(), state(SportCardFixtures.booking()).conditions)
    }

    @Test fun aRestrictedPredictionNamesItsRulesBesideTheMatchingNote() {
        val predicted = lesson.copy(isLessonReal = false, canSignIn = false,
            unavailableReasons = listOf(UnavailableReason.HealthGroupMismatch))
        assertEquals(listOf(
            SportDetailsCondition.Restricted(listOf(SportBookingRestriction(SportBookingObstacle.HEALTH))),
            SportDetailsCondition.PredictionMatching(withRules = true)
        ), state(predicted).conditions)
        val predictedBooking = SportCardFixtures.booking(-1).copy(isLessonReal = false, signed = false)
        assertEquals(listOf(SportDetailsCondition.PredictionMatching(withRules = false)), state(predictedBooking).conditions)
    }

    @Test fun detailsRespectReadOnlyBusyAndAChangedDeadline() {
        assertEquals(SportDetailsAction(SportBookingAction.SIGN, enabled = true), state(lesson).action)
        assertNull(state(lesson, actionsEnabled = false).action, "read-only")
        assertEquals(SportDetailsAction(SportBookingAction.SIGN, enabled = false), state(lesson, busy = true).action)
        assertEquals(SportDetailsAction(SportBookingAction.SIGN, enabled = false), state(lesson, submitted = true).action)
        assertNull(state(lesson, at = lesson.start).action, "the start closes the offer")
        assertEquals(SportBookingAction.NONE, lesson.toDetailsArgs().bookingAction(lesson.start))
    }

    @Test fun everyOfferOfTheSheetMapsToItsAction() {
        val cases = listOf(
            lesson to SportBookingAction.SIGN,
            lesson.copy(signed = true) to SportBookingAction.CANCEL,
            lesson.copy(available = 0, canSignIn = false, unavailableReasons = listOf(UnavailableReason.Full)) to SportBookingAction.AUTO,
            lesson.copy(available = 0, canSignIn = false, signEntry = SportCardFixtures.entry()) to SportBookingAction.CANCEL_AUTO,
            SportCardFixtures.booking() to SportBookingAction.CANCEL,
            SportCardFixtures.booking().copy(signed = false, signEntry = SportCardFixtures.entry()) to SportBookingAction.CANCEL_AUTO
        )
        cases.forEachIndexed { index, (item, action) ->
            assertEquals(SportDetailsAction(action, enabled = true), state(item).action, "case $index")
        }
        assertNull(state(SportCardFixtures.booking().copy(signed = false)).action)
    }

    @Test fun shareActionForUpcomingLessonsBookingsAndPredictions() {
        val titled = lesson.copy(sectionName = SectionName("Фитнес (функциональная тренировка с элементами кроссфита и растяжкой)"))
        val past = titled.copy(start = titled.start - 2.days, end = titled.end - 2.days)
        val cases = listOf<Pair<SportCommon, SportShareTarget?>>(
            titled to SportShareTarget.Lesson(1),
            SportCardFixtures.booking() to SportShareTarget.Lesson(1),
            titled.copy(isLessonReal = false) to SportShareTarget.Prediction(1),
            SportCardFixtures.booking(-1).copy(isLessonReal = false, signed = false) to SportShareTarget.Prediction(1),
            past to null,
            titled.copy(lessonId = -5) to null
        )
        cases.forEachIndexed { index, (item, target) ->
            assertEquals(target, state(item).share, "case $index")
        }
        assertNull(state(lesson, at = lesson.end).share, "an ended lesson")
        assertEquals(SportShareTarget.Lesson(1), state(lesson, at = lesson.end - 1.minutes).share)
    }

    @Test fun predictionDetailsKeepTheLocationWithoutAHistoricalFootnote() {
        val predicted = lesson.copy(isLessonReal = false, canSignIn = false)
        val state = state(predicted)
        assertEquals(listOf(SportDetailsCondition.Waiting(predicted = true)), state.conditions)
        assertNull(state.registration.occupancy, "a prediction invents no places")
        assertNull(state.registration.queue)
        assertNull(state.registration.status)
        assertFalse(state.registration.visible)
        assertEquals(predicted.roomName, predicted.toDetailsArgs().roomName)
    }

    @Test fun freePlacesFollowTheOccupancyForEveryRussianPluralForm() {
        listOf(1, 2, 4, 5, 11, 21, 0).forEach { available ->
            val occupancy = state(lesson.copy(available = available, limit = 40)).registration.occupancy
            assertEquals(SportOccupancy(available, 40), occupancy)
            assertEquals(40 - available, occupancy?.occupied)
        }
        assertTrue(state(lesson).registration.visible)
        assertNull(state(SportCardFixtures.booking()).registration.occupancy)
    }

    @Test fun aWaitingQueueShowsItsPositionAttemptsAndHistoryInOrder() {
        val entry = SportCardFixtures.entry().copy(satisfiedAt = lesson.start - 1.hours)
        val queue = state(lesson.copy(available = 0, canSignIn = false, signEntry = entry)).registration
        assertEquals(SportRegistrationStatus.WAITING, queue.status)
        assertEquals(SportQueueDetails(
            autoSign = false,
            waiting = true,
            position = 3,
            total = 12,
            notificationAttempts = 2,
            maxNotificationAttempts = 5,
            history = listOf(
                SportQueueFact(SportQueueFactKind.CREATED, entry.createdAt),
                SportQueueFact(SportQueueFactKind.LAST_REQUEST, entry.lastNotifiedAt!!),
                SportQueueFact(SportQueueFactKind.COMPLETED, lesson.start - 1.hours)
            )
        ), queue.queue)
        assertTrue(queue.queue!!.positionVisible)

        val expired = state(SportCardFixtures.booking().copy(signed = false,
            signEntry = SportCardFixtures.entry(SportQueueEntryStatus.EXPIRED))).registration.queue!!
        assertFalse(expired.waiting)
        assertFalse(expired.positionVisible)
    }

    @Test fun friendsShowSignedQueuedAndNotSignedStatuses() {
        val friends = listOf(
            FriendSportBooking(UserSummary(900001, "Первый", null, emptyList(), UserSharing(true, true)), 1, null),
            FriendSportBooking(UserSummary(900002, "Второй", "https://example.invalid/a.png", emptyList(), UserSharing(true, true)), 1,
                SportCardFixtures.entry()),
            FriendSportBooking(UserSummary(900003, "Третий", null, emptyList(), UserSharing(true, true)), 1,
                SportCardFixtures.entry(SportQueueEntryStatus.EXPIRED))
        )
        assertEquals(listOf(
            SportFriendStatus(900001, "Первый", null, SportFriendRegistration.Signed),
            SportFriendStatus(900002, "Второй", "https://example.invalid/a.png", SportFriendRegistration.Queued(3, 12)),
            SportFriendStatus(900003, "Третий", null, SportFriendRegistration.NotSigned)
        ), state(lesson.copy(friendsBookings = friends)).friends)
    }
}
