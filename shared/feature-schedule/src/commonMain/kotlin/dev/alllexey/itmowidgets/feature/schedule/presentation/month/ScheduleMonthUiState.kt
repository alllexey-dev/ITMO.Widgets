package dev.alllexey.itmowidgets.feature.schedule.presentation.month

import dev.alllexey.itmowidgets.core.text.UiText
import kotlinx.datetime.LocalDate

/**
 * The month sheet behind `Сентябрь ▾`: [title] `Сентябрь 2026`, the weekday names, [leading] empty cells before the
 * 1st, the [days] with a dot per lesson, and `Открыть четверг, 17 сентября` ([open]) for the [selected] day.
 */
data class ScheduleMonthUiState(
    val title: String,
    val weekdays: List<String>,
    val leading: Int,
    val days: List<MonthDay>,
    val selected: LocalDate,
    val open: UiText,
)

/** A day of the month grid: [number] `17`, [lessons] dots (at most four), [today] ringed, [past] dimmed. */
data class MonthDay(
    val date: LocalDate,
    val number: String,
    val lessons: Int,
    val today: Boolean,
    val past: Boolean,
    val selected: Boolean,
)
