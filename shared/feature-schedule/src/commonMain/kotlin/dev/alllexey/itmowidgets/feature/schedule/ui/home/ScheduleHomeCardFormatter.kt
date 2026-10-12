package dev.alllexey.itmowidgets.feature.schedule.ui.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeLessonState
import dev.alllexey.itmowidgets.core.home.HomeScheduleRow
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.schedule.lessonTypeName
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.buildingShortTitle
import dev.alllexey.itmowidgets.core.text.headline
import dev.alllexey.itmowidgets.core.text.roomShortTitle
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.schedule.SchedulePlurals
import dev.alllexey.itmowidgets.shared.feature.schedule.ScheduleStrings
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The schedule card as it is drawn: [title] is `Сегодня` or `Завтра`; [footer] counts finished lessons or says the
 * day is empty or over.
 */
data class HomeScheduleCardUi(
    val title: UiText,
    val date: String,
    val rows: List<HomeScheduleRowUi>,
    val footer: UiText?,
)

/** [unread] changes of lessons not yet over; [latest] is the newest of them in one line. */
data class HomeScheduleChangesCardUi(val unread: Int, val latest: UiText)

/** What a schedule row opens. */
sealed interface HomeRowTarget {
    data class Lesson(val args: LessonDetailsArgs) : HomeRowTarget

    data class PendingSport(val args: PendingSportDetailsArgs) : HomeRowTarget
}

/**
 * A lesson or a pending sport lesson of the schedule card: `HH:mm` times, the lesson type's colour by [typeId], a
 * [badge] (`сейчас`, `далее`, `ждём запись`, `прогноз`) and, for the lesson in progress, its elapsed share in
 * [progress]; a row with a [progress] is the focused one.
 */
data class HomeScheduleRowUi(
    val start: String,
    val end: String,
    val title: String,
    val subtitle: UiText,
    val typeId: Int,
    val badge: UiText?,
    val progress: Float?,
    val target: HomeRowTarget,
)

/**
 * Turns the schedule sources' cards into what the schedule cards draw. Times and dates are written in [timeZone], the
 * academic one, so a lesson keeps its MyITMO time whatever the device zone; lesson times arrive as `HH:mm`, pending
 * sport times as ISO offset date-times.
 */
class ScheduleHomeCardFormatter(private val timeZone: TimeZone) {

    fun schedule(card: HomeCard.Schedule): HomeScheduleCardUi {
        val footer = when {
            card.rows.isEmpty() && card.completed == 0 -> UiText.Res(ScheduleStrings.home_schedule_empty)
            card.rows.isEmpty() -> UiText.Res(ScheduleStrings.home_schedule_done)
            card.completed > 0 && !card.tomorrow ->
                UiText.Plural(SchedulePlurals.home_schedule_completed, card.completed, listOf(card.completed))
            else -> null
        }
        return HomeScheduleCardUi(
            title = UiText.Res(
                if (card.tomorrow) ScheduleStrings.home_schedule_tomorrow else CoreStrings.home_schedule_today,
            ),
            date = card.date.format(DateTexts.WEEKDAY_DAY_MONTH),
            rows = card.rows.map(::scheduleRow),
            footer = footer,
        )
    }

    fun changes(card: HomeCard.ScheduleChanges): HomeScheduleChangesCardUi =
        HomeScheduleChangesCardUi(card.unread, card.latest.headline())

    private fun scheduleRow(row: HomeScheduleRow): HomeScheduleRowUi = when (row) {
        is HomeScheduleRow.Lesson -> {
            val args = row.args
            HomeScheduleRowUi(
                start = LocalTime.parse(args.start).format(DateTexts.TIME),
                end = LocalTime.parse(args.end).format(DateTexts.TIME),
                title = args.subjectName,
                subtitle = UiText.Joined(
                    listOfNotNull(
                        lessonTypeName(args.typeId),
                        args.room?.let(::roomShortTitle),
                        args.building?.let { buildingShortTitle(it) },
                    ),
                    SEPARATOR,
                ),
                typeId = args.typeId,
                badge = when (row.state) {
                    HomeLessonState.CURRENT -> UiText.Res(ScheduleStrings.home_schedule_now)
                    HomeLessonState.NEXT -> UiText.Res(ScheduleStrings.home_schedule_next)
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
                        UiText.Res(CoreStrings.title_sport),
                        args.roomName.takeIf { it.isNotBlank() }?.let(UiText::Dynamic),
                    ),
                    SEPARATOR,
                ),
                typeId = SPORT_TYPE_ID,
                badge = UiText.Res(
                    if (row.predicted) CoreStrings.home_pending_predicted else CoreStrings.home_pending_waiting,
                ),
                progress = null,
                target = HomeRowTarget.PendingSport(args),
            )
        }
    }

    private fun time(instant: Instant): String = instant.toLocalDateTime(timeZone).time.format(DateTexts.TIME)

    companion object {
        /** MyITMO's sport lesson type: a pending row takes its colour. */
        const val SPORT_TYPE_ID = 11

        /** A middle dot between spaces, between the parts of a row's second line. */
        const val SEPARATOR = " \u00B7 "
    }
}
