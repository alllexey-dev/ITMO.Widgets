package dev.alllexey.itmowidgets.core.lesson

import dev.alllexey.itmowidgets.core.navigation.AppRoute
import kotlinx.datetime.LocalDate

/**
 * The subject of a lesson for the lesson sheet's `Предмет` and `Ссылки 3`, from the feature that owns subjects. An open
 * set (Koin `getAll()`): the sheet asks each source and shows the two actions only when one of them knows the subject.
 */
fun interface LessonSubjectSource {
    /** The subject of MyITMO's schedule [subjectId] taught on [date], or null when this source does not know it. */
    suspend fun subject(subjectId: Long, subjectName: String, date: LocalDate): LessonSubject?
}

/** [page] opens the subject; [links] counts its links and [materials] opens them. */
data class LessonSubject(val page: AppRoute, val links: Int, val materials: AppRoute)
