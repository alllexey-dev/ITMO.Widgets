package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContext
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

// What the subject page's loaders bring, before RecordbookSubjectViewModel puts it into words.

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
    /** Past period or physical education: no lessons ahead. */
    data object Hidden : SubjectLessonsState
    data object Loading : SubjectLessonsState
    /** Bound; candidates come from the same window, so a bound subject always has lessons. */
    data class Content(val lessons: List<SubjectLesson>, val source: SubjectContext.Source) : SubjectLessonsState
    data class Proposed(val candidate: ScheduleSubject) : SubjectLessonsState
    data class Ambiguous(val candidates: List<ScheduleSubject>) : SubjectLessonsState
    data object Unmatched : SubjectLessonsState
    data class Error(val error: AppError) : SubjectLessonsState
}
