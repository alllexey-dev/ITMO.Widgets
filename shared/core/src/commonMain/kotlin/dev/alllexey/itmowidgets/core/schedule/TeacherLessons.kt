package dev.alllexey.itmowidgets.core.schedule

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * What the viewer's own academic lessons with one teacher tell: their flows and subject names, newest first; [past]
 * holds the term of the last lesson of each subject not taught in the current term (absent: taught now).
 */
data class TeacherLessons(val flowIds: Set<Long>, val subjects: List<String>, val past: Map<String, StudyTerm> = emptyMap())

/** A half of the academic year: autumn (September to January) or spring (February to August) of [year]. */
data class StudyTerm(val year: Int, val autumn: Boolean) {
    companion object {
        fun of(date: LocalDate): StudyTerm = when {
            date.month >= Month.SEPTEMBER -> StudyTerm(date.year, autumn = true)
            date.month == Month.JANUARY -> StudyTerm(date.year - 1, autumn = true)
            else -> StudyTerm(date.year, autumn = false)
        }
    }
}

/**
 * Reads sampled weeks of the viewer's personal My ITMO schedule. Read only: these lessons are never uploaded to
 * Backend.
 */
interface TeacherLessonsGateway {

    /**
     * Emits the lessons with the teacher accumulated so far each time a week answers, newest weeks first, and completes
     * once every week has answered. A failed week is left out; a [AppResult.Failure] comes only when every week failed.
     */
    fun taughtBy(teacherIsu: Int): Flow<AppResult<TeacherLessons>>
}
