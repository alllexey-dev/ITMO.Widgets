package dev.alllexey.itmowidgets.feature.reviews.presentation

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** The history answers only what a test sends through [answer]; it stays open until [finish]. */
internal class FakeTeacherLessonsGateway : TeacherLessonsGateway {
    private val answers = Channel<AppResult<TeacherLessons>>(Channel.UNLIMITED)
    var teacherIsu: Int? = null
        private set

    fun answer(result: AppResult<TeacherLessons>) {
        check(answers.trySend(result).isSuccess)
    }

    fun finish() {
        answers.close()
    }

    override fun taughtBy(teacherIsu: Int): Flow<AppResult<TeacherLessons>> {
        this.teacherIsu = teacherIsu
        return answers.receiveAsFlow()
    }
}
