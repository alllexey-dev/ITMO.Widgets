package dev.alllexey.itmowidgets.feature.schedule

// The data tests that stay in :app keep their own copy of this file until the schedule data moves here (KM-11a).

import kotlinx.datetime.LocalTime

/** [minutes] later on the same day; schedule fixtures never cross midnight. */
internal fun LocalTime.plusMinutes(minutes: Int): LocalTime = LocalTime.fromSecondOfDay(toSecondOfDay() + minutes * 60)
