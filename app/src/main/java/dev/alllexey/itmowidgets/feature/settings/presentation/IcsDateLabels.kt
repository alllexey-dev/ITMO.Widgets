package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format

/** Russian day ranges: «5 октября», «5–11 октября», «28 сентября – 4 октября», years only across a year's end. */
object IcsDateLabels {

    fun day(date: LocalDate): String = date.format(DateTexts.DAY_MONTH)

    fun range(dates: ClosedRange<LocalDate>): UiText {
        val start = dates.start
        val end = dates.endInclusive
        val text = when {
            start == end -> day(start)
            start.year != end.year ->
                "${start.format(DateTexts.DAY_MONTH_YEAR)} – ${end.format(DateTexts.DAY_MONTH_YEAR)}"
            start.month == end.month -> "${start.format(DateTexts.DAY)}–${day(end)}"
            else -> "${day(start)} – ${day(end)}"
        }
        return UiText.Dynamic(text)
    }
}
