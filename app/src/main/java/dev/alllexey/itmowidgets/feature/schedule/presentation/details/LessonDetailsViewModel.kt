package dev.alllexey.itmowidgets.feature.schedule.presentation.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Friends on one lesson occurrence. The lesson itself arrives with the sheet;
 * only the friend list needs Backend, and only behind the opt-in.
 */
@HiltViewModel
class LessonDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val friendsRepository: LessonFriendsRepository,
    private val customServices: CustomServicesRepository
) : ViewModel() {

    val pairId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_PAIR_ID)) { "Lesson details need a pair id" }
    val date: LocalDate = LocalDate.parse(checkNotNull(savedStateHandle.get<String>(ARG_DATE)) { "Lesson details need a date" })

    private val _friends = MutableStateFlow<LessonFriendsState>(LessonFriendsState.Loading)
    val friends: StateFlow<LessonFriendsState> = _friends.asStateFlow()

    init {
        load()
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

    companion object {
        const val ARG_PAIR_ID = "pair_id"
        const val ARG_DATE = "date"
    }
}
