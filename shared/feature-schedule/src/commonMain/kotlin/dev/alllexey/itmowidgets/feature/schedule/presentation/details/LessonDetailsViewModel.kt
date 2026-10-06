package dev.alllexey.itmowidgets.feature.schedule.presentation.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.schedule.LessonOccurrence
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Friends on one lesson occurrence, the tone of its teacher's reviews and its latest schedule change. The lesson
 * itself arrives with the sheet; only friends and the tone need Backend, and only behind the opt-in.
 */
class LessonDetailsViewModel(
    savedStateHandle: SavedStateHandle,
    private val friendsRepository: LessonFriendsRepository,
    private val customServices: CustomServicesRepository,
    private val teacherLevels: TeacherLevelsRepository,
    changesRepository: ScheduleChangesRepository,
) : ViewModel() {

    private val occurrence = LessonOccurrence(
        pairId = checkNotNull(savedStateHandle.get<Long>(ARG_PAIR_ID)) { "Lesson details need a pair id" },
        date = LocalDate.parse(checkNotNull(savedStateHandle.get<String>(ARG_DATE)) { "Lesson details need a date" })
    )
    private val teacherIsu: Int? = savedStateHandle.get<Int>(ARG_TEACHER_ISU)?.takeIf { it > 0 }

    private val refreshes = RefreshTracker(viewModelScope)
    private val friends = MutableStateFlow<LessonFriendsState>(LessonFriendsState.Loading)
    private val teacherLevel = MutableStateFlow<TeacherLevel?>(null)
    private val change = changesRepository.observeChanges()
        .map { changes -> changes.filter { occurrence in it.occurrences() }.maxByOrNull(ScheduleChange::detectedAt) }
        .onStart { emit(null) }

    val uiState: StateFlow<LessonDetailsUiState> = combine(
        friends,
        refreshes.refreshing,
        teacherLevel,
        change
    ) { friends, refreshing, level, change ->
        // A retry shows progress while it may still replace the error.
        val shownFriends = if (refreshing && friends is LessonFriendsState.Error) LessonFriendsState.Loading else friends
        LessonDetailsUiState(shownFriends, level, change)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LessonDetailsUiState())

    init {
        refresh(RefreshMode.Silent)
        loadTeacherLevel()
    }

    /** Asks for the friends again; the tone is read once per sheet. */
    fun refresh(mode: RefreshMode) {
        refreshes.launch(mode) { friends.value = loadFriends() }
    }

    private suspend fun loadFriends(): LessonFriendsState {
        if (!customServices.isEnabled()) return LessonFriendsState.Disabled
        return when (val result = friendsRepository.friendsOnLesson(occurrence.pairId, occurrence.date)) {
            is AppResult.Success -> LessonFriendsState.Content(result.value)
            is AppResult.Failure ->
                if (result.error == AppError.CustomServicesDisabled) LessonFriendsState.Disabled
                else LessonFriendsState.Error(result.error)
        }
    }

    private fun loadTeacherLevel() {
        val isu = teacherIsu ?: return
        viewModelScope.launch {
            if (!customServices.isEnabled()) return@launch
            teacherLevel.value = teacherLevels.levels(setOf(isu))[isu]
        }
    }

    companion object {
        const val ARG_PAIR_ID = "pair_id"
        const val ARG_DATE = "date"
        const val ARG_TEACHER_ISU = "teacher_isu"
    }
}
