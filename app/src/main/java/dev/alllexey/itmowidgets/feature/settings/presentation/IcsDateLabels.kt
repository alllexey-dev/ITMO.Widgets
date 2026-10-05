package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.text.UiText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Russian day ranges: «5 октября», «5–11 октября», «28 сентября – 4 октября», years only across a year's end. */
object IcsDateLabels {
    private val RUSSIAN: Locale = Locale.forLanguageTag("ru")
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", RUSSIAN)
    private val DAY_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", RUSSIAN)

    fun day(date: LocalDate): String = DAY.format(date)

    fun range(dates: ClosedRange<LocalDate>): UiText {
        val start = dates.start
        val end = dates.endInclusive
        val text = when {
            start == end -> DAY.format(start)
            start.year != end.year -> "${DAY_YEAR.format(start)} – ${DAY_YEAR.format(end)}"
            start.month == end.month -> "${start.dayOfMonth}–${DAY.format(end)}"
            else -> "${DAY.format(start)} – ${DAY.format(end)}"
        }
        return UiText.Dynamic(text)
    }
}
