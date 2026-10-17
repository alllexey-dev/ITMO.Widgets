package dev.alllexey.itmowidgets.core.navigation

import kotlinx.serialization.Serializable

/**
 * What the campus map opens with: the [lesson] whose room it shows, and with [route] the «Маршрут» to it from the
 * previous lesson of that day or the entrance; no lesson opens the first building to browse.
 */
@Serializable
data class CampusMapArgs(val lesson: LessonDetailsArgs? = null, val route: Boolean = false)
