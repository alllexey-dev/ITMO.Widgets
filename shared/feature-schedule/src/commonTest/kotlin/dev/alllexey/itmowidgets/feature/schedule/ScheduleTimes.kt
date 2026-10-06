package dev.alllexey.itmowidgets.feature.schedule

// :app's calendar and widget tests keep their own copy of this file (`app/src/test/.../feature/schedule/`).

import kotlinx.datetime.LocalTime

/** [minutes] later on the same day; schedule fixtures never cross midnight. */
internal fun LocalTime.plusMinutes(minutes: Int): LocalTime = LocalTime.fromSecondOfDay(toSecondOfDay() + minutes * 60)
