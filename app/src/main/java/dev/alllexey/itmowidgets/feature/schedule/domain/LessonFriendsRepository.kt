package dev.alllexey.itmowidgets.feature.schedule.domain

import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppResult
import java.time.LocalDate

/** The viewer's friends attending one lesson occurrence, from Backend; needs the opt-in. */
interface LessonFriendsRepository {
    suspend fun friendsOnLesson(pairId: Long, date: LocalDate): AppResult<List<UserSummary>>
}
