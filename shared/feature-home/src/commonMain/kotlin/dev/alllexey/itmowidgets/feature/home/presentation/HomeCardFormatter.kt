package dev.alllexey.itmowidgets.feature.home.presentation

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeLessonState
import dev.alllexey.itmowidgets.core.home.HomeScheduleRow
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.model.primaryGroup
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.schedule.lessonTypeName
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.buildingShortTitle
import dev.alllexey.itmowidgets.core.text.headline
import dev.alllexey.itmowidgets.core.text.markSubjectList
import dev.alllexey.itmowidgets.core.text.roomShortTitle
import dev.alllexey.itmowidgets.shared.core.home_schedule_today
import dev.alllexey.itmowidgets.shared.core.title_sport
import dev.alllexey.itmowidgets.shared.feature.home.Res
import dev.alllexey.itmowidgets.shared.feature.home.home_pending_predicted
import dev.alllexey.itmowidgets.shared.feature.home.home_pending_waiting
import dev.alllexey.itmowidgets.shared.feature.home.home_schedule_completed
import dev.alllexey.itmowidgets.shared.feature.home.home_schedule_done
import dev.alllexey.itmowidgets.shared.feature.home.home_schedule_empty
import dev.alllexey.itmowidgets.shared.feature.home.home_schedule_next
import dev.alllexey.itmowidgets.shared.feature.home.home_schedule_now
import dev.alllexey.itmowidgets.shared.feature.home.home_schedule_tomorrow
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes

/**
 * Turns the sources' `HomeCard`s into what the screen draws. Times and dates are written in [timeZone], the academic
 * one, so a lesson keeps its MyITMO time whatever the device zone; lesson times arrive as `HH:mm`, pending sport
 * times as ISO offset date-times.
 */
class HomeCardFormatter(private val timeZone: TimeZone) {

    fun format(card: HomeCard): HomeCardUi = when (card) {
        is HomeCard.Schedule -> schedule(card)
        is HomeCard.ScheduleChanges -> HomeCardUi.ScheduleChanges(card.unread, card.latest.headline())
        is HomeCard.Marks -> HomeCardUi.Marks(card.subjects.size, markSubjectList(card.subjects))
        is HomeCard.Sport -> sport(card)
        is HomeCard.FriendRequests -> HomeCardUi.FriendRequests(
            count = card.incoming.size,
            incoming = card.incoming.take(FRIENDS_LIMIT).map(::friend),
        )
        is HomeCard.Hint -> HomeCardUi.Hint(card.hint)
    }

    private fun schedule(card: HomeCard.Schedule): HomeCardUi.Schedule {
        val footer = when {
            card.rows.isEmpty() && card.completed == 0 -> UiText.Res(Res.string.home_schedule_empty)
            card.rows.isEmpty() -> UiText.Res(Res.string.home_schedule_done)
            card.completed > 0 && !card.tomorrow ->
                UiText.Plural(Res.plurals.home_schedule_completed, card.completed, listOf(card.completed))
            else -> null
        }
        return HomeCardUi.Schedule(
            title = UiText.Res(if (card.tomorrow) Res.string.home_schedule_tomorrow else CoreRes.string.home_schedule_today),
            date = card.date.format(DateTexts.WEEKDAY_DAY_MONTH),
            rows = card.rows.map(::scheduleRow),
            footer = footer,
        )
    }

    private fun scheduleRow(row: HomeScheduleRow): HomeScheduleRowUi = when (row) {
        is HomeScheduleRow.Lesson -> {
            val args = row.args
            HomeScheduleRowUi(
                start = LocalTime.parse(args.start).format(DateTexts.TIME),
                end = LocalTime.parse(args.end).format(DateTexts.TIME),
                title = args.subjectName,
                subtitle = UiText.Joined(
                    listOfNotNull(
                        UiText.Res(lessonTypeName(args.typeId)),
                        args.room?.let(::roomShortTitle),
                        args.building?.let { buildingShortTitle(it) },
                    ),
                    SEPARATOR,
                ),
                typeId = args.typeId,
                badge = when (row.state) {
                    HomeLessonState.CURRENT -> UiText.Res(Res.string.home_schedule_now)
                    HomeLessonState.NEXT -> UiText.Res(Res.string.home_schedule_next)
                    HomeLessonState.UPCOMING -> null
                },
                progress = row.progress?.coerceIn(0f, 1f),
                target = HomeRowTarget.Lesson(args),
            )
        }
        is HomeScheduleRow.PendingSport -> {
            val args = row.args
            HomeScheduleRowUi(
                start = time(DateTexts.parseOffsetInstant(args.start)),
                end = time(DateTexts.parseOffsetInstant(args.end)),
                title = args.sectionName,
                subtitle = UiText.Joined(
                    listOfNotNull(
                        UiText.Res(CoreRes.string.title_sport),
                        args.roomName.takeIf { it.isNotBlank() }?.let(UiText::Dynamic),
                    ),
                    SEPARATOR,
                ),
                typeId = SPORT_TYPE_ID,
                badge = pendingBadge(row.predicted),
                progress = null,
                target = HomeRowTarget.PendingSport(args),
            )
        }
    }

    private fun sport(card: HomeCard.Sport): HomeCardUi.Sport = HomeCardUi.Sport(
        score = card.score?.let { HomeSportScoreUi(it.totalCapped, it.remaining) },
        queue = card.queue.take(QUEUE_LIMIT).map(::sportRow),
        more = (card.queue.size - QUEUE_LIMIT).coerceAtLeast(0),
    )

    private fun sportRow(booking: PendingSportBooking): HomeSportRowUi {
        val start = booking.start.toLocalDateTime(timeZone)
        return HomeSportRowUi(
            title = booking.sectionName,
            subtitle = start.date.format(DateTexts.SHORT_WEEKDAY_DAY_SHORT_MONTH) + SEPARATOR +
                start.time.format(DateTexts.TIME) + RANGE_DASH + time(booking.end),
            badge = pendingBadge(booking.isPrediction),
            args = booking.toDetailsArgs(timeZone),
        )
    }

    private fun friend(user: UserSummary) = HomeFriendUi(
        isu = user.isu,
        name = user.name,
        pictureUrl = user.pictureUrl,
        group = user.primaryGroup()?.name?.takeIf { it.isNotBlank() },
    )

    private fun pendingBadge(predicted: Boolean) =
        UiText.Res(if (predicted) Res.string.home_pending_predicted else Res.string.home_pending_waiting)

    private fun time(instant: Instant): String = instant.toLocalDateTime(timeZone).time.format(DateTexts.TIME)

    companion object {
        /** Rows of the sport card's queue and of the friend requests card. */
        const val QUEUE_LIMIT = 3
        const val FRIENDS_LIMIT = 3

        /** MyITMO's sport lesson type: a pending row takes its colour. */
        const val SPORT_TYPE_ID = 11

        /** A middle dot between spaces, between the parts of a row's second line. */
        const val SEPARATOR = " \u00B7 "

        /** The en dash of a time range, "16:00-17:30" with U+2013. */
        const val RANGE_DASH = "\u2013"
    }
}
