package dev.alllexey.itmowidgets.core.schedule

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.Flow

/** What the viewer's own academic lessons with one teacher tell: their flows and subject names, newest first. */
data class TeacherLessons(val flowIds: Set<Long>, val subjects: List<String>)

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
