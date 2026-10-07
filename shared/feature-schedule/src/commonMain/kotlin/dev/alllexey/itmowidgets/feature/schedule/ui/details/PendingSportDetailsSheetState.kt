package dev.alllexey.itmowidgets.feature.schedule.ui.details

import androidx.compose.runtime.Immutable
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.char
import kotlinx.datetime.format.optional
import kotlinx.datetime.toLocalDateTime

/**
 * What [PendingSportDetailsContent] draws: the queued or predicted [booking] the sheet was opened with, its times
 * read in the academic [timeZone] (the args carry them at the offset they were written at).
 */
@Immutable
data class PendingSportDetailsSheetState(
    val booking: PendingSportDetailsArgs,
    val timeZone: TimeZone,
) {
    val start: LocalDateTime = booking.start.atZone(timeZone)
    val end: LocalDateTime = booking.end.atZone(timeZone)

    /** The map hand-off needs a place to look for: the room name as the sport data spells it. */
    val mapAvailable: Boolean get() = booking.roomName.isNotBlank()
}

/**
 * What the sheet asks of its host. The host performs every effect: the map of the room, the profile of an ISU and
 * the sport tab (each after closing the sheet), closing itself.
 */
@Immutable
class PendingSportDetailsActions(
    val onMap: () -> Unit = {},
    val onProfile: (isu: Int) -> Unit = {},
    val onOpenSport: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/** Tags of the sheet's parts for host tests and instrumented flows. */
object PendingSportDetailsTestTags {
    const val SCROLL = "pending_sport_details_scroll"
    const val CONDITIONS = "pending_sport_details_conditions"
    const val OPEN_SPORT = "pending_sport_details_open_sport"
}

/** `OffsetDateTime.parse` of what `PendingSportDetailsArgs` writes: seconds and the fraction only when not zero. */
private val OffsetDateTimeText = DateTimeComponents.Format {
    date(LocalDate.Formats.ISO)
    char('T')
    hour()
    char(':')
    minute()
    optional {
        char(':')
        second()
        optional {
            char('.')
            secondFraction(1, 9)
        }
    }
    offset(UtcOffset.Formats.ISO)
}

private fun String.atZone(zone: TimeZone): LocalDateTime =
    OffsetDateTimeText.parse(this).toInstantUsingOffset().toLocalDateTime(zone)
