package dev.alllexey.itmowidgets.core.text

import dev.alllexey.itmowidgets.shared.core.CorePlurals
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
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

/**
 * The age of a stored answer shown after a refresh failed: `Обновлено только что`, `Обновлено 5 мин назад`,
 * `Обновлено 3 ч назад`, `Обновлено 2 дня назад`.
 */
fun updatedAgo(at: Instant, now: Instant): UiText {
    val elapsed = now - at
    return when {
        elapsed < 1.minutes -> UiText.Res(CoreStrings.common_updated_now)
        elapsed < 1.hours -> UiText.Res(CoreStrings.common_updated_minutes, listOf(elapsed.inWholeMinutes.toInt()))
        elapsed < 1.days -> UiText.Res(CoreStrings.common_updated_hours, listOf(elapsed.inWholeHours.toInt()))
        else -> elapsed.inWholeDays.toInt().let { UiText.Plural(CorePlurals.common_updated_days, it, listOf(it)) }
    }
}
