package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkChips
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContext

/** A person teaching the subject, with the lesson types they run (`typeId`s), most frequent first. */
data class SubjectTeacher(val name: String, val isu: Long?, val roles: List<Int>)

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

/**
 * The schedule and links half of the subject page. Physical education has no [resourceScope]:
 * no links, chips or chats. [chips] also hold the MyITMO LMS page while the links load or fail.
 */
data class SubjectHubState(
    val lessons: SubjectLessonsState = SubjectLessonsState.Hidden,
    val lessonsExpanded: Boolean = false,
    val teachers: List<SubjectTeacher> = emptyList(),
    val resourceScope: ResourceScope? = null,
    val links: SubjectLinksState? = null,
    val chips: SubjectLinkChips = SubjectLinkChips(emptyList(), 0),
    val chats: List<SubjectLink> = emptyList()
) {
    /** The nearest lessons first; the rest only after «Все пары». */
    val visibleLessons: List<SubjectLesson>
        get() = (lessons as? SubjectLessonsState.Content)?.lessons.orEmpty()
            .let { if (lessonsExpanded) it else it.take(COLLAPSED_LESSONS) }

    /** How many lessons «Все пары · N» would show; 0 when nothing is hidden. */
    val allLessonsCount: Int
        get() = (lessons as? SubjectLessonsState.Content)?.lessons?.size
            ?.takeIf { !lessonsExpanded && it > COLLAPSED_LESSONS } ?: 0

    companion object {
        const val COLLAPSED_LESSONS = 2
    }
}
