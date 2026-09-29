package dev.alllexey.itmowidgets.core.schedule

import dev.alllexey.itmowidgets.core.result.AppResult

/** What the viewer's own academic lessons with one teacher tell: their flows and subject names, newest first. */
data class TeacherLessons(val flowIds: Set<Long>, val subjects: List<String>)

/**
 * Reads the viewer's personal My ITMO schedule for the last 8 study periods. Read only: these lessons are
 * never uploaded to Backend.
 */
interface TeacherLessonsGateway {
    suspend fun taughtBy(teacherIsu: Int): AppResult<TeacherLessons>
}
