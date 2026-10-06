package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkChips
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContext
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** A link of the subject whose address is a Google Sheet; [mine] for the viewer's own links. */
data class SheetLinkOption(val url: String, val title: String?, val mine: Boolean)

/** The own total from a sheet on the subject page: a connection, or the offer to make one. */
sealed interface SubjectSheetState {
    /** [updatedAt] is the last successful reading in the academic time zone; [today] decides how it is written. */
    data class Connected(val score: SheetScore, val updatedAt: LocalDateTime?, val today: LocalDate) : SubjectSheetState

    /** «Мои баллы из таблицы»: the subject's sheet links, own first, then pinned, scores, the rest by rank. */
    data class Hint(val links: List<SheetLinkOption>) : SubjectSheetState
}

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
 * no links or chats. [chips] are the short list of at most [LINK_ROWS] link rows and also hold the
 * MyITMO LMS page while the links load or fail. [linkCount] counts what the links sheet lists;
 * [canVote] is false without the connection or under a `VOTE` restriction.
 * [teacherLevels] are the review tones of [teachers] by ISU; a teacher without one is absent.
 */
data class SubjectHubState(
    val lessons: SubjectLessonsState = SubjectLessonsState.Hidden,
    val lessonsExpanded: Boolean = false,
    val teachers: List<SubjectTeacher> = emptyList(),
    val resourceScope: ResourceScope? = null,
    val links: SubjectLinksState? = null,
    val chips: SubjectLinkChips = SubjectLinkChips(emptyList(), 0),
    val chats: List<SubjectLink> = emptyList(),
    val linkCount: Int = 0,
    val canVote: Boolean = false,
    val teacherLevels: Map<Long, TeacherLevel> = emptyMap(),
    /** Null for physical education and for a subject without sheet links or a connection. */
    val sheet: SubjectSheetState? = null
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
        /** Pinned, LMS and the best ranked links on the page; the rest is behind «Все ссылки». */
        const val LINK_ROWS = 3
    }
}
