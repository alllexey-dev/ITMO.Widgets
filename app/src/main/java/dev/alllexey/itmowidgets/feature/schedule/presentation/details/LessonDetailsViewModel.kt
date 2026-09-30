package dev.alllexey.itmowidgets.feature.schedule.presentation.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.schedule.LessonOccurrence
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Friends on one lesson occurrence, the tone of its teacher's reviews and its latest schedule change. The lesson
 * itself arrives with the sheet; only friends and the tone need Backend, and only behind the opt-in.
 */
@HiltViewModel
class LessonDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val friendsRepository: LessonFriendsRepository,
    private val customServices: CustomServicesRepository,
    private val teacherLevels: TeacherLevelsRepository,
    changesRepository: ScheduleChangesRepository,
) : ViewModel() {

    val pairId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_PAIR_ID)) { "Lesson details need a pair id" }
    val date: LocalDate = LocalDate.parse(checkNotNull(savedStateHandle.get<String>(ARG_DATE)) { "Lesson details need a date" })

    private val _friends = MutableStateFlow<LessonFriendsState>(LessonFriendsState.Loading)
    val friends: StateFlow<LessonFriendsState> = _friends.asStateFlow()

    private val teacherIsu: Int? = savedStateHandle.get<Int>(ARG_TEACHER_ISU)?.takeIf { it > 0 }
    private val _teacherLevel = MutableStateFlow<TeacherLevel?>(null)
    val teacherLevel: StateFlow<TeacherLevel?> = _teacherLevel.asStateFlow()

    /** The newest change of the last 30 days that touches this occurrence, from the local store only. */
    val change: StateFlow<ScheduleChange?> = changesRepository.observeChanges()
        .map { changes ->
            val occurrence = LessonOccurrence(pairId, date)
            changes.filter { occurrence in it.occurrences() }.maxByOrNull(ScheduleChange::detectedAt)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        load()
        loadTeacherLevel()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            if (!customServices.isEnabled()) {
                _friends.value = LessonFriendsState.Disabled
                return@launch
            }
            _friends.value = LessonFriendsState.Loading
            _friends.value = when (val result = friendsRepository.friendsOnLesson(pairId, date)) {
                is AppResult.Success -> LessonFriendsState.Content(result.value)
                is AppResult.Failure ->
                    if (result.error == AppError.CustomServicesDisabled) LessonFriendsState.Disabled
                    else LessonFriendsState.Error(result.error)
            }
        }
    }

    private fun loadTeacherLevel() {
        val isu = teacherIsu ?: return
        viewModelScope.launch {
            if (!customServices.isEnabled()) return@launch
            _teacherLevel.value = teacherLevels.levels(setOf(isu))[isu]
        }
    }

    companion object {
        const val ARG_PAIR_ID = "pair_id"
        const val ARG_DATE = "date"
        const val ARG_TEACHER_ISU = "teacher_isu"
    }
}
