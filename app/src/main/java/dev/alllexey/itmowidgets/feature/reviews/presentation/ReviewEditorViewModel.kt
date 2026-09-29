package dev.alllexey.itmowidgets.feature.reviews.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewLimits
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.ui.toUiText
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewEditorUiState(
    val subject: String = "",
    val text: String = "",
    val anonymous: Boolean = true,
    /** Subjects of the viewer's own lessons with the teacher, newest first. */
    val suggestions: List<String> = emptyList(),
    val subjectError: UiText? = null,
    val textError: UiText? = null,
    val saving: Boolean = false,
    val editing: Boolean = false,
) {
    val canSave: Boolean get() = text.isNotBlank() && !saving

    /** The minimum length is a quiet hint while typing; it turns into an error only on send. */
    val showsMinimumHint: Boolean get() = textError == null && TeacherReviewLimits.length(text.trim()) < TeacherReviewLimits.MIN_TEXT
}

sealed interface ReviewEditorEvent {
    data object Saved : ReviewEditorEvent
    data class Failed(val text: UiText) : ReviewEditorEvent
}

/** Writes the viewer's review of a teacher, or edits the one in the cached reviews. */
@HiltViewModel
class ReviewEditorViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val repository: TeacherReviewsRepository,
    private val lessons: TeacherLessonsGateway,
) : ViewModel() {
    val teacherIsu: Int = checkNotNull(handle[TeacherReviewArgs.TEACHER_ISU])
    val teacherName: String = checkNotNull(handle[TeacherReviewArgs.TEACHER_NAME])
    private val _uiState = MutableStateFlow(restoredState())
    val uiState: StateFlow<ReviewEditorUiState> = _uiState.asStateFlow()
    private val channel = Channel<ReviewEditorEvent>(Channel.BUFFERED)
    val events: Flow<ReviewEditorEvent> = channel.receiveAsFlow()
    /** Flows of the viewer's lessons with the teacher; empty until the schedule history answers. */
    private var flowIds: Set<Long> = emptySet()

    init {
        viewModelScope.launch {
            val result = lessons.taughtBy(teacherIsu)
            if (result is AppResult.Success) {
                flowIds = result.value.flowIds
                _uiState.update { it.copy(suggestions = result.value.subjects) }
            }
        }
    }

    fun onSubjectChanged(subject: String) {
        handle[KEY_SUBJECT] = subject
        _uiState.update { it.copy(subject = subject, subjectError = null) }
    }

    fun onTextChanged(text: String) {
        handle[KEY_TEXT] = text
        _uiState.update { it.copy(text = text, textError = null) }
    }

    fun onAnonymousChanged(anonymous: Boolean) {
        handle[KEY_ANONYMOUS] = anonymous
        _uiState.update { it.copy(anonymous = anonymous) }
    }

    /** Sends with the flows collected so far; a slow schedule history never delays the review. */
    fun save() {
        val state = _uiState.value
        if (state.saving) return
        val subject = state.subject.trim()
        val text = state.text.replace("\r\n", "\n").trim()
        val textLength = TeacherReviewLimits.length(text)
        val textError = when {
            textLength < TeacherReviewLimits.MIN_TEXT -> UiText.Resource(R.string.review_text_too_short, listOf(TeacherReviewLimits.MIN_TEXT))
            textLength > TeacherReviewLimits.MAX_TEXT -> UiText.Resource(R.string.review_text_too_long, listOf(TeacherReviewLimits.MAX_TEXT))
            else -> null
        }
        val subjectError = if (TeacherReviewLimits.length(subject) > TeacherReviewLimits.MAX_SUBJECT) {
            UiText.Resource(R.string.review_subject_too_long, listOf(TeacherReviewLimits.MAX_SUBJECT))
        } else null
        if (textError != null || subjectError != null) {
            _uiState.update { it.copy(textError = textError, subjectError = subjectError) }
            return
        }
        _uiState.update { it.copy(saving = true) }
        val draft = TeacherReviewDraft(subject.ifEmpty { null }, text, state.anonymous, flowIds)
        viewModelScope.launch {
            val result = repository.save(teacherIsu, draft)
            _uiState.update { it.copy(saving = false) }
            channel.send(when (result) {
                is AppResult.Success -> ReviewEditorEvent.Saved
                is AppResult.Failure -> ReviewEditorEvent.Failed(result.error.toUiText())
            })
        }
    }

    fun hasChanges(): Boolean {
        val state = _uiState.value
        return state.subject != handle[KEY_INITIAL_SUBJECT] || state.text != handle[KEY_INITIAL_TEXT] ||
            state.anonymous != handle[KEY_INITIAL_ANONYMOUS]
    }

    /** The first opening starts from the viewer's cached review; later ones, after process death too, from the handle. */
    private fun restoredState(): ReviewEditorUiState {
        if (handle.get<Boolean>(KEY_INITIALIZED) != true) {
            val mine = repository.cachedReviews(teacherIsu)?.mine
            val subject = mine?.subject.orEmpty()
            val text = mine?.text.orEmpty()
            val anonymous = mine?.anonymous ?: true
            handle[KEY_SUBJECT] = subject
            handle[KEY_TEXT] = text
            handle[KEY_ANONYMOUS] = anonymous
            handle[KEY_INITIAL_SUBJECT] = subject
            handle[KEY_INITIAL_TEXT] = text
            handle[KEY_INITIAL_ANONYMOUS] = anonymous
            handle[KEY_EDITING] = mine != null
            handle[KEY_INITIALIZED] = true
        }
        return ReviewEditorUiState(
            subject = handle[KEY_SUBJECT] ?: "",
            text = handle[KEY_TEXT] ?: "",
            anonymous = handle[KEY_ANONYMOUS] ?: true,
            editing = handle[KEY_EDITING] ?: false,
        )
    }

    private companion object {
        const val KEY_SUBJECT = "review_editor_subject"
        const val KEY_TEXT = "review_editor_text"
        const val KEY_ANONYMOUS = "review_editor_anonymous"
        const val KEY_INITIALIZED = "review_editor_initialized"
        const val KEY_INITIAL_SUBJECT = "review_editor_initial_subject"
        const val KEY_INITIAL_TEXT = "review_editor_initial_text"
        const val KEY_INITIAL_ANONYMOUS = "review_editor_initial_anonymous"
        const val KEY_EDITING = "review_editor_editing"
    }
}
