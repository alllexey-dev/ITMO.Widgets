package dev.alllexey.itmowidgets.feature.home.presentation

import dev.alllexey.itmowidgets.core.text.UiText

/** The Сегодня root header: the tab's name over the academic date, `понедельник, 7 сентября`, and the bell's [badge]. */
data class TodayHeaderUiState(val title: UiText, val subtitle: UiText, val badge: Int = 0, val showPass: Boolean = true)
