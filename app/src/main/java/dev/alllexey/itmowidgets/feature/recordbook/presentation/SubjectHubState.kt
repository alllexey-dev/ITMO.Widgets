package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContext

/** A person teaching the subject, with the lesson types they run (`typeId`s), most frequent first. */
data class SubjectTeacher(val name: String, val isu: Long?, val roles: List<Int>)

/** Something to open outside the app; in 2.1 only the MyITMO LMS link, when it exists. */
data class SubjectResource(val url: String)

sealed interface SubjectLessonsState {
    /** Past period or physical education: the section does not exist. */
    data object Hidden : SubjectLessonsState
    data object Loading : SubjectLessonsState
    /** Bound; candidates come from the same window, so a bound subject always has lessons. */
    data class Content(val lessons: List<SubjectLesson>, val source: SubjectContext.Source) : SubjectLessonsState
    data class Proposed(val candidate: ScheduleSubject) : SubjectLessonsState
    data class Ambiguous(val candidates: List<ScheduleSubject>) : SubjectLessonsState
    data object Unmatched : SubjectLessonsState
    data class Error(val error: AppError) : SubjectLessonsState
}

/** The two halves of the subject screen; the schedule tab exists only when the hub has something. */
enum class SubjectTab { SCORES, SCHEDULE }

data class SubjectHubState(
    val lessons: SubjectLessonsState = SubjectLessonsState.Hidden,
    val teachers: List<SubjectTeacher> = emptyList(),
    val resources: List<SubjectResource> = emptyList()
)

/** Whether there is anything to put on the schedule tab. */
val SubjectHubState.hasScheduleContent: Boolean
    get() = lessons != SubjectLessonsState.Hidden || teachers.isNotEmpty() || resources.isNotEmpty()
