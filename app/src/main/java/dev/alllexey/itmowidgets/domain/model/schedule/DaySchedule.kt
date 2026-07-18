package dev.alllexey.itmowidgets.domain.model.schedule

import java.time.LocalDate

data class DaySchedule(
    val dayNumber: Int,
    val weekNumber: Int,
    val date: LocalDate,
    val note: String?,
    val lessons: List<Lesson>
)
