package dev.alllexey.itmowidgets.feature.reviews.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.presentation.BusyKeys
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Reports another viewer's review; a blank comment is sent as none. */
class ReportReviewViewModel(
    handle: SavedStateHandle,
    private val repository: TeacherReviewsRepository,
) : ViewModel() {
    private val teacherIsu: Int = checkNotNull(handle[TeacherReviewArgs.TEACHER_ISU])
    private val reviewId: String = checkNotNull(handle[TeacherReviewArgs.REVIEW_ID])
    /** One report at a time: the only key is [Unit]. */
    private val sending = BusyKeys<Unit>(viewModelScope)
    private val eventQueue = EventQueue<ReportReviewEvent>()

    val uiState: StateFlow<ReportReviewUiState> = sending.busy
        .map { ReportReviewUiState(sending = it.isNotEmpty()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReportReviewUiState())

    val events: Flow<ReportReviewEvent> = eventQueue.events

    fun send(reason: ReviewReportReason, comment: String?) {
        sending.launch(Unit) {
            val result = repository.report(teacherIsu, reviewId, reason, comment?.trim()?.ifEmpty { null })
            eventQueue.send(when (result) {
                is AppResult.Success -> ReportReviewEvent.Done
                is AppResult.Failure -> ReportReviewEvent.Failed(result.error)
            })
        }
    }
}
