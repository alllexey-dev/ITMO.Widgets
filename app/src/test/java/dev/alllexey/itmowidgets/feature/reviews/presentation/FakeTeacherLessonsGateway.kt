package dev.alllexey.itmowidgets.feature.reviews.presentation

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway

internal class FakeTeacherLessonsGateway : TeacherLessonsGateway {
    var result: AppResult<TeacherLessons> = AppResult.Success(TeacherLessons(emptySet(), emptyList()))
    var gate: suspend () -> Unit = {}
    var calls = 0
        private set
    var teacherIsu: Int? = null
        private set

    override suspend fun taughtBy(teacherIsu: Int): AppResult<TeacherLessons> {
        calls += 1
        this.teacherIsu = teacherIsu
        gate()
        return result
    }
}
