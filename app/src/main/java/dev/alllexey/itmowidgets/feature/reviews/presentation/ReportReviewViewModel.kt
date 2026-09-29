package dev.alllexey.itmowidgets.feature.reviews.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.ui.toUiText
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface ReportReviewEvent {
    data object Done : ReportReviewEvent
    data class Failed(val text: UiText) : ReportReviewEvent
}

/** Reports another viewer's review; a blank comment is sent as none. */
@HiltViewModel
class ReportReviewViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repository: TeacherReviewsRepository,
) : ViewModel() {
    private val teacherIsu: Int = checkNotNull(handle[TeacherReviewArgs.TEACHER_ISU])
    private val reviewId: String = checkNotNull(handle[TeacherReviewArgs.REVIEW_ID])
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()
    private val channel = Channel<ReportReviewEvent>(Channel.BUFFERED)
    val events: Flow<ReportReviewEvent> = channel.receiveAsFlow()

    fun send(reason: ReviewReportReason, comment: String?) {
        if (_sending.value) return
        _sending.value = true
        viewModelScope.launch {
            val result = repository.report(teacherIsu, reviewId, reason, comment?.trim()?.ifEmpty { null })
            _sending.value = false
            channel.send(when (result) {
                is AppResult.Success -> ReportReviewEvent.Done
                is AppResult.Failure -> ReportReviewEvent.Failed(result.error.toUiText())
            })
        }
    }
}
