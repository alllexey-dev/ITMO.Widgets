package dev.alllexey.itmowidgets.feature.sport.presentation.user

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.util.MergedDataState
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportRepository
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface UserSportUiState {
    data object Loading : UserSportUiState
    data class Error(val error: AppError) : UserSportUiState
    data class Content(val bookings: List<SportBooking>, val refreshing: Boolean) : UserSportUiState
}

@HiltViewModel
class UserSportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userSport: UserSportRepository,
    private val sportSchedule: SportScheduleRepository
) : ViewModel() {

    val isu: Int = checkNotNull(savedStateHandle.get<Int>(UserScreenArgs.ISU)) { "Sport needs an ISU" }
    val name: String = savedStateHandle.get<String>(UserScreenArgs.NAME).orEmpty()

    private val _uiState = MutableStateFlow<UserSportUiState>(UserSportUiState.Loading)
    val uiState: StateFlow<UserSportUiState> = _uiState.asStateFlow()

    private var loading = false

    init {
        load()
    }

    fun load() {
        if (loading) return
        loading = true
        val current = _uiState.value
        _uiState.value = if (current is UserSportUiState.Content) current.copy(refreshing = true) else UserSportUiState.Loading
        viewModelScope.launch {
            try {
                _uiState.value = coroutineScope {
                    // Confirmed IDs are resolved against the shared catalog, which must be loaded.
                    val catalog = async { sportSchedule.refreshSportSchedule(); sportSchedule.observeSportSchedule().first() }
                    when (val bookings = userSport.getUserBookings(isu)) {
                        is AppResult.Failure -> UserSportUiState.Error(bookings.error)
                        is AppResult.Success -> UserSportUiState.Content(
                            bookings = merge(bookings.value.confirmedLessonIds, bookings.value.pending, catalog.await()),
                            refreshing = false
                        )
                    }
                }
            } finally {
                loading = false
            }
        }
    }

    private fun merge(
        confirmedIds: List<Long>,
        pending: List<SportBooking>,
        catalog: MergedDataState<List<SportLesson>>
    ): List<SportBooking> {
        val lessons = when (catalog) {
            is MergedDataState.Success -> catalog.data
            is MergedDataState.PartialSuccess -> catalog.data
            is MergedDataState.Error -> emptyList()
        }.associateBy { it.lessonId }
        val confirmed = confirmedIds.mapNotNull { lessons[it]?.toBooking() }
        val pendingWithoutDuplicates = pending.filter { it.lessonId !in confirmedIds }
        return (confirmed + pendingWithoutDuplicates).sortedBy { it.start }
    }

    private fun SportLesson.toBooking() = SportBooking(
        isLessonReal = true,
        lessonId = lessonId,
        sectionName = sectionName,
        start = start,
        end = end,
        roomName = roomName,
        teacherFio = teacherFio,
        teacherIsu = teacherIsu,
        sectionLevel = sectionLevel,
        lessonLevel = lessonLevel,
        signed = true,
        signEntry = null,
        friendsBookings = emptyList()
    )
}
