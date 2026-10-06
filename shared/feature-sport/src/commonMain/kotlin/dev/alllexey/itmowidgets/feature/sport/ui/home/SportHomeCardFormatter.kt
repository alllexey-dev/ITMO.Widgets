package dev.alllexey.itmowidgets.feature.sport.ui.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.home_pending_predicted
import dev.alllexey.itmowidgets.shared.core.home_pending_waiting
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The sport card as it is drawn: [queue] holds the first rows of the own queues and [more] the number left out. */
data class HomeSportCardUi(val score: HomeSportScoreUi?, val queue: List<HomeSportRowUi>, val more: Int)

/** The sport score out of 100 and the points still missing. */
data class HomeSportScoreUi(val total: Int, val remaining: Int)

/** An own queue entry: the section, `вт, 1 сент.`, the start and the end and `ждём запись` or `прогноз`. */
data class HomeSportRowUi(
    val title: String,
    val subtitle: String,
    val badge: UiText,
    val args: PendingSportDetailsArgs,
)

/** Turns the sport source's card into what the sport card draws; times are written in [timeZone], the academic one. */
class SportHomeCardFormatter(private val timeZone: TimeZone) {

    fun format(card: HomeCard.Sport): HomeSportCardUi = HomeSportCardUi(
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
            badge = UiText.Res(
                if (booking.isPrediction) Res.string.home_pending_predicted else Res.string.home_pending_waiting,
            ),
            args = booking.toDetailsArgs(timeZone),
        )
    }

    private fun time(instant: Instant): String = instant.toLocalDateTime(timeZone).time.format(DateTexts.TIME)

    companion object {
        /** Rows of the card's queue. */
        const val QUEUE_LIMIT = 3

        /** A middle dot between spaces, between the date and the time of a row. */
        const val SEPARATOR = " \u00B7 "

        /** The en dash of a time range, "16:00-17:30" with U+2013. */
        const val RANGE_DASH = "\u2013"
    }
}
