package dev.alllexey.itmowidgets.core.text

import dev.alllexey.itmowidgets.shared.core.CoreStrings

/** `2 ч 10 мин`, `2 ч`, `48 мин`: a span of whole [minutes], as gaps and countdowns say it. */
fun durationText(minutes: Int): UiText {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0 -> UiText.Res(CoreStrings.duration_minutes, listOf(rest))
        rest == 0 -> UiText.Res(CoreStrings.duration_hours, listOf(hours))
        else -> UiText.Res(CoreStrings.duration_hours_minutes, listOf(hours, rest))
    }
}

private const val MINUTES_PER_HOUR = 60
