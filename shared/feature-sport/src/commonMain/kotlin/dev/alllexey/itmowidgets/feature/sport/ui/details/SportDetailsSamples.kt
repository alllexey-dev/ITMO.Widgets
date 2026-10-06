package dev.alllexey.itmowidgets.feature.sport.ui.details

import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingObstacle
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingRestriction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportDetailsCondition
import dev.alllexey.itmowidgets.feature.sport.presentation.common.toDetailsArgs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Synthetic snapshots for the details sheet's previews and host tests, seen on Monday 7 September 2026 at noon, the
 * day before [SportCardFixtures]'s lesson, as the debug host (`SportCardsPreviewActivity.FixedTime`) and LP-1d's XML
 * references saw them.
 */
internal object SportDetailsSamples {

    val now: Instant = LocalDateTime(2026, 9, 7, 12, 0).toInstant(TimeZone.of("Europe/Moscow"))

    val time: AcademicTimeProvider = FixedTime(now)

    /** A lesson with free places, two friends and a time conflict: the `lesson` reference. */
    val lesson: SportCommonDetailsArgs = SportCardFixtures.lesson().copy(
        friendsBookings = friends(),
        intersection = true,
        unavailableReasons = listOf(UnavailableReason.TimeConflict),
    ).toDetailsArgs()

    /** A booking waiting in the free queue, notified, with its history: the `queue` reference. */
    val queue: SportCommonDetailsArgs = SportCardFixtures.booking(2).copy(
        signed = false,
        sectionName = SectionName("""Современные танцы (Клуб парных танцев "Потанцуем")"""),
        signEntry = SportCardFixtures.entry(SportQueueEntryStatus.NOTIFIED),
        friendsBookings = friends(),
    ).toDetailsArgs()

    /** A predicted lesson: no places yet, the prediction hint: the `prediction` reference. */
    val prediction: SportCommonDetailsArgs =
        SportCardFixtures.lesson().copy(isLessonReal = false, canSignIn = false).toDetailsArgs()

    /** A signed booking of `Мой спорт`: the status, no capacity, the cancel action. */
    val booking: SportCommonDetailsArgs = SportCardFixtures.booking().toDetailsArgs()

    /** A plain open lesson with free places. */
    val open: SportCommonDetailsArgs = SportCardFixtures.lesson().toDetailsArgs()

    /** A signed booking with a signed, a queued and a not signed friend, and no comment. */
    val withFriends: SportCommonDetailsArgs = SportCardFixtures.booking(3).copy(
        friendsBookings = friends() + FriendSportBooking(
            friend("Третий тестовый друг", 900003),
            1,
            SportCardFixtures.entry(SportQueueEntryStatus.EXPIRED),
        ),
    ).toDetailsArgs()

    /** One condition of every tone, as a lesson could never show them at once; the conditions preview's rows. */
    val everyTone: List<SportDetailsCondition> = listOf(
        SportDetailsCondition.Allowed,
        SportDetailsCondition.Waiting(predicted = false),
        SportDetailsCondition.ScheduleOverlap,
        SportDetailsCondition.Restricted(
            listOf(
                SportBookingRestriction(SportBookingObstacle.HEALTH),
                SportBookingRestriction(SportBookingObstacle.DENIED, "Явный запрет MyITMO"),
            ),
        ),
    )

    private fun friends() = listOf(
        FriendSportBooking(friend("Тестовый друг с длинным именем", 900001), 1, null),
        FriendSportBooking(friend("Второй тестовый друг", 900002), 1, SportCardFixtures.entry()),
    )

    private fun friend(name: String, isu: Int) = UserSummary(isu, name, null, emptyList(), UserSharing(true, true))

    /** A clock stopped at [at]; a test moves it by setting [at]. */
    class FixedTime(var at: Instant) : AcademicTimeProvider {
        override val timeZone: TimeZone = TimeZone.of("Europe/Moscow")
        override fun today(): LocalDate = at.toLocalDateTime(timeZone).date
        override fun now(): Instant = at
    }
}
