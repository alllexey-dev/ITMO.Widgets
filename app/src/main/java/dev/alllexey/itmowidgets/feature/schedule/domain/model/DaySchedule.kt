package dev.alllexey.itmowidgets.feature.schedule.domain.model

import java.time.LocalDate

data class DaySchedule(
    val dayNumber: Int,
    val weekNumber: Int,
    val date: LocalDate,
    val note: String?,
    val lessons: List<Lesson>
)
