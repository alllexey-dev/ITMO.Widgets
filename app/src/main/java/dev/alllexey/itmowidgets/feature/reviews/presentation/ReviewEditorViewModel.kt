package dev.alllexey.itmowidgets.feature.reviews.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewLimits
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Writes the viewer's review of a teacher, or edits the one in the cached reviews. */
@HiltViewModel
class ReviewEditorViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val repository: TeacherReviewsRepository,
    private val lessons: TeacherLessonsGateway,
) : ViewModel() {
    private val teacherIsu: Int = checkNotNull(handle[TeacherReviewArgs.TEACHER_ISU])
    val teacherName: String = checkNotNull(handle[TeacherReviewArgs.TEACHER_NAME])
    private val state = MutableStateFlow(restoredState())
    private val eventQueue = EventQueue<ReviewEditorEvent>()
    /** Flows of the viewer's lessons with the teacher, growing as the schedule weeks answer. */
    private var flowIds: Set<Long> = emptySet()

    val uiState: StateFlow<ReviewEditorUiState> = state.asStateFlow()

    val events: Flow<ReviewEditorEvent> = eventQueue.events

    init {
        viewModelScope.launch {
            lessons.taughtBy(teacherIsu).collect { result ->
                if (result is AppResult.Success) {
                    flowIds = result.value.flowIds
                    state.update { it.copy(suggestions = result.value.subjects) }
                }
            }
        }
    }

    fun onSubjectChanged(subject: String) {
        handle[KEY_SUBJECT] = subject
        state.update { it.copy(subject = subject, subjectError = null) }
    }

    fun onTextChanged(text: String) {
        handle[KEY_TEXT] = text
        state.update { it.copy(text = text, textError = null) }
    }

    fun onAnonymousChanged(anonymous: Boolean) {
        handle[KEY_ANONYMOUS] = anonymous
        state.update { it.copy(anonymous = anonymous) }
    }

    /** Sends with the flows collected so far; a slow schedule history never delays the review. */
    fun save() {
        val form = state.value
        if (form.saving) return
        val subject = form.subject.trim()
        val text = form.text.replace("\r\n", "\n").trim()
        val textLength = TeacherReviewLimits.length(text)
        val textError = when {
            textLength < TeacherReviewLimits.MIN_TEXT -> ReviewFieldError.TEXT_TOO_SHORT
            textLength > TeacherReviewLimits.MAX_TEXT -> ReviewFieldError.TEXT_TOO_LONG
            else -> null
        }
        val subjectError =
            if (TeacherReviewLimits.length(subject) > TeacherReviewLimits.MAX_SUBJECT) ReviewFieldError.SUBJECT_TOO_LONG else null
        if (textError != null || subjectError != null) {
            state.update { it.copy(textError = textError, subjectError = subjectError) }
            return
        }
        state.update { it.copy(saving = true) }
        val draft = TeacherReviewDraft(subject.ifEmpty { null }, text, form.anonymous, flowIds)
        viewModelScope.launch {
            val result = repository.save(teacherIsu, draft)
            state.update { it.copy(saving = false) }
            eventQueue.send(when (result) {
                is AppResult.Success -> ReviewEditorEvent.Saved
                is AppResult.Failure -> ReviewEditorEvent.Failed(result.error)
            })
        }
    }

    fun hasChanges(): Boolean {
        val form = state.value
        return form.subject != handle[KEY_INITIAL_SUBJECT] || form.text != handle[KEY_INITIAL_TEXT] ||
            form.anonymous != handle[KEY_INITIAL_ANONYMOUS]
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
