package dev.alllexey.itmowidgets.core.text

import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** When an event happened, as the bell and the friend requests say it: `12:05` today, `вчера`, else `7 сент.`. */
fun eventMoment(at: Instant, today: LocalDate, zone: TimeZone): UiText {
    val local = at.toLocalDateTime(zone)
    return when (local.date) {
        today -> UiText.Dynamic(local.time.format(DateTexts.TIME))
        today.minus(1, DateTimeUnit.DAY) -> UiText.Res(CoreStrings.event_moment_yesterday)
        else -> UiText.Dynamic("${local.date.format(DateTexts.DAY)} ${local.date.format(DateTexts.MONTH_SHORT)}")
    }
}
