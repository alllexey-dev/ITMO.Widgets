package dev.alllexey.itmowidgets.client.schedule

import dev.alllexey.itmowidgets.client.support.RouteCase
import dev.alllexey.itmowidgets.client.users.SyntheticUsers
import kotlinx.datetime.LocalDate

/** One [RouteCase] per public function of [ScheduleApi], with synthetic arguments. */
object ScheduleRouteCases {
    const val PAIR_ID = 2_147_483_648L
    val occurrence = LocalDate(2026, 9, 8)

    val syncLessons = RouteCase("syncLessons") { schedule.syncLessons(SyntheticLessons.request) }
    val userLessons = RouteCase("userLessons") {
        schedule.userLessons(SyntheticUsers.OTHER_ISU, SyntheticLessons.from, SyntheticLessons.to)
    }
    val friendsOnLesson = RouteCase("friendsOnLesson") { schedule.friendsOnLesson(PAIR_ID, occurrence) }

    val all: List<RouteCase> = listOf(syncLessons, userLessons, friendsOnLesson)
}
