package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlEntry
import dev.alllexey.itmowidgets.feature.recordbook.domain.GradeStep
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookControlGroups
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookGradeScale
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import kotlinx.datetime.TimeZone

sealed interface RecordbookSubjectUiState {
    data object Loading : RecordbookSubjectUiState
    data class Content(
        val subject: RecordbookSubject,
        val controls: List<RecordbookControl>,
        val sport: RecordbookSportState?,
        val controlsError: AppError? = null,
        val refreshing: Boolean = false,
        val refreshError: AppError? = null,
        /** BARS journal failed; subject and controls are MyITMO values. */
        val barsError: AppError? = null,
        /** Schedule and links sections: links, chats, teachers, upcoming lessons. */
        val hub: SubjectHubState = SubjectHubState(),
        /** The academic time zone control dates are shown in. */
        val timeZone: TimeZone
    ) : RecordbookSubjectUiState {
        /** Lone controls and groups of related ones, in server order. */
        val controlGroups: List<ControlEntry> = RecordbookControlGroups.groupControls(controls)

        /** The hint to the next grade; none once a final result is set or for physical education. */
        val gradeStep: GradeStep?
            get() = if (subject.isPhysicalEducation || subject.normalizedRate != RecordbookRate.InProgress) null
                else RecordbookGradeScale.nextStep(subject.score, subject.assessmentKind)
    }
    data class Error(val error: AppError) : RecordbookSubjectUiState
}

sealed interface RecordbookSubjectEvent {
    /** A vote on the page did not reach the server. */
    data class VoteFailed(val error: AppError) : RecordbookSubjectEvent
}
