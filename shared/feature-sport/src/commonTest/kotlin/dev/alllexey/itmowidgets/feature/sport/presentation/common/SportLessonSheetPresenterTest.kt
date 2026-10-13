package dev.alllexey.itmowidgets.feature.sport.presentation.common

import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.shortPersonName
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.shared.feature.sport.SportPlurals
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** The sport lesson sheet's cases at state level: the places, the clash, the one action, the conditions, the people. */
class SportLessonSheetPresenterTest {

    /** The day before the fixture lesson, at noon in Moscow. */
    private val now = Instant.parse("2026-09-07T12:00:00+03:00")
    private val zone = TimeZone.of("Europe/Moscow")
    private val lesson = SportCardFixtures.lesson()

    private fun sheet(
        item: SportCommon,
        at: Instant = now,
        actionsEnabled: Boolean = true,
        busy: Boolean = false,
        submitted: Boolean = false,
    ) = SportLessonSheetPresenter.present(item.toDetailsArgs(), at, zone, actionsEnabled, busy, submitted)

    private fun res(key: String, vararg arguments: Any) = UiText.Res(key, arguments.toList())

    @Test fun theDayAndTheTimeReadAsOneLine() {
        assertEquals(UiText.Dynamic("Вторник, 8 сентября, 18:30–20:00"), sheet(lesson).date)
        assertEquals(res(SportStrings.sport_kind_free_short), sheet(lesson).kind)
    }

    @Test fun thePlacesAreLargeWithTheirRussianWordAndTheTakenOnes() {
        val seats = sheet(lesson.copy(available = 7, limit = 20)).seats!!
        assertEquals(7, seats.free)
        assertEquals(UiText.Plural(SportPlurals.sport_seats_free, 7), seats.label)
        assertEquals(res(SportStrings.sport_seats_taken, 13, 20), seats.detail)
        assertEquals(res(SportStrings.sport_seats_none), sheet(lesson.copy(available = 0)).seats!!.label)
        assertNull(sheet(lesson.copy(isLessonReal = false)).seats, "a prediction invents no places")
        assertNull(sheet(SportCardFixtures.booking()).seats, "a booking has no catalog places")
    }

    @Test fun aClashIsAWarningWithTheLessonAndBookingStaysEnabled() {
        val clashing = lesson.copy(intersection = true).toDetailsArgs().copy(clashSubject = "Алгоритмы", clashStart = "08:20")
        val sheet = SportLessonSheetPresenter.present(clashing, now, zone)
        assertEquals(res(SportStrings.sport_sheet_clash, "Алгоритмы", "08:20"), sheet.clash)
        assertEquals(SportSheetButton(SportBookingAction.SIGN, res(SportStrings.sport_action_sign), enabled = true, destructive = false), sheet.action)
        assertTrue(SportCheck(SportCheckState.WAIT, res(SportStrings.sport_check_clash_allowed)) in sheet.conditions)
        assertEquals(res(SportStrings.sport_sheet_clash_unknown), sheet(lesson.copy(intersection = true)).clash)
        assertNull(sheet(lesson).clash)
    }

    @Test fun everyOfferOfTheSheetMapsToItsOneAction() {
        val cases = listOf(
            lesson to SportBookingAction.SIGN,
            lesson.copy(signed = true) to SportBookingAction.CANCEL,
            lesson.copy(available = 0, canSignIn = false, unavailableReasons = listOf(UnavailableReason.Full)) to SportBookingAction.AUTO,
            lesson.copy(available = 0, canSignIn = false, signEntry = SportCardFixtures.entry()) to SportBookingAction.CANCEL_AUTO,
            SportCardFixtures.booking() to SportBookingAction.CANCEL,
            SportCardFixtures.booking().copy(signed = false, signEntry = SportCardFixtures.entry()) to SportBookingAction.CANCEL_AUTO,
        )
        cases.forEachIndexed { index, (item, action) ->
            assertEquals(action, sheet(item).action?.action, "case $index")
        }
        assertNull(sheet(SportCardFixtures.booking().copy(signed = false)).action)
        assertEquals(true, sheet(lesson.copy(signed = true)).action?.destructive)
    }

    @Test fun theActionRespectsReadOnlyBusyASentActionAndTheStart() {
        assertNull(sheet(lesson, actionsEnabled = false).action, "read-only")
        assertEquals(false, sheet(lesson, busy = true).action?.enabled)
        assertEquals(false, sheet(lesson, submitted = true).action?.enabled)
        assertNull(sheet(lesson, at = lesson.start).action, "the start closes the offer")
    }

    @Test fun aQueueSaysHowManyWaitAndWhenItCloses() {
        val full = lesson.copy(available = 0, canSignIn = false, unavailableReasons = listOf(UnavailableReason.Full))
        val sheet = sheet(full)
        assertEquals(res(SportStrings.sport_sheet_queue_note), sheet.actionNote)
        assertTrue(SportCheck(SportCheckState.WAIT, res(SportStrings.sport_check_queue_closes, "17:30")) in sheet.conditions)
        assertTrue(SportCheck(SportCheckState.WAIT, res(SportStrings.sport_check_queue_late)) in sheet(full, at = full.start - 30.minutes).conditions)
        assertEquals(res(SportStrings.sport_sheet_future_note), sheet(full.copy(isLessonReal = false)).actionNote)
    }

    @Test fun restrictionsAreUnmetConditionsAndASignedLessonExplainsNothing() {
        val health = lesson.copy(canSignIn = false, unavailableReasons = listOf(UnavailableReason.HealthGroupMismatch))
        assertEquals(SportCheck(SportCheckState.NO, res(SportStrings.sport_rule_health)), sheet(health).conditions.first())
        val denied = lesson.copy(canSignIn = false, unavailableReasons = listOf(UnavailableReason.Other("Только для сборной")))
        assertEquals(SportCheck(SportCheckState.NO, UiText.Dynamic("Только для сборной")), sheet(denied).conditions.first())
        assertEquals(emptyList(), sheet(lesson.copy(signed = true)).conditions)
        assertEquals(listOf(SportCheck(SportCheckState.OK, res(SportStrings.sport_check_no_clash))), sheet(lesson).conditions)
    }

    @Test fun theStatusIsBookedOrThePlaceInTheQueue() {
        assertEquals(SportStatusUi(res(SportStrings.sport_pill_booked), booked = true), sheet(lesson.copy(signed = true)).status)
        assertEquals(
            SportStatusUi(res(SportStrings.sport_queue_position, 3, 12), booked = false),
            sheet(lesson.copy(available = 0, canSignIn = false, signEntry = SportCardFixtures.entry())).status,
        )
        assertNull(sheet(SportCardFixtures.booking().copy(signed = false, signEntry = SportCardFixtures.entry(SportQueueEntryStatus.EXPIRED))).status)
    }

    @Test fun shareForUpcomingLessonsBookingsAndPredictions() {
        val titled = lesson.copy(sectionName = SectionName("Фитнес (функциональная тренировка с элементами кроссфита и растяжкой)"))
        val past = titled.copy(start = titled.start - 2.days, end = titled.end - 2.days)
        val cases = listOf<Pair<SportCommon, SportShareTarget?>>(
            titled to SportShareTarget.Lesson(1),
            SportCardFixtures.booking() to SportShareTarget.Lesson(1),
            titled.copy(isLessonReal = false) to SportShareTarget.Prediction(1),
            SportCardFixtures.booking(-1).copy(isLessonReal = false, signed = false) to SportShareTarget.Prediction(1),
            past to null,
            titled.copy(lessonId = -5) to null,
        )
        cases.forEachIndexed { index, (item, target) -> assertEquals(target, sheet(item).share, "case $index") }
        assertNull(sheet(lesson, at = lesson.end).share, "an ended lesson")
    }

    @Test fun theTeacherThePlaceTheCommentAndFriendsWithTheirStatus() {
        val friends = listOf(
            FriendSportBooking(UserSummary(900001, "Первый", null, emptyList(), UserSharing(true, true)), 1, null),
            FriendSportBooking(UserSummary(900002, "Второй", null, emptyList(), UserSharing(true, true)), 1, SportCardFixtures.entry()),
        )
        val sheet = sheet(lesson.copy(friendsBookings = friends, roomName = "Кронверкский пр., 49, спортивный зал"))
        assertEquals(SportPersonUi("Тестовый п. с. д. и.".let { sheet.teacher!!.name }, 100), sheet.teacher)
        assertEquals(SportPlaceUi("Кронверкский пр., 49", "спортивный зал", "Кронверкский пр., 49"), sheet.place)
        assertEquals("Возьмите сменную обувь и воду.", sheet.comment)
        assertEquals(
            listOf(
                SportFriendUi("Первый", res(SportStrings.sport_friend_signed_short), 900001),
                SportFriendUi("Второй", res(SportStrings.sport_friend_queued, 3, 12), 900002),
            ),
            sheet.friends,
        )
    }
}
