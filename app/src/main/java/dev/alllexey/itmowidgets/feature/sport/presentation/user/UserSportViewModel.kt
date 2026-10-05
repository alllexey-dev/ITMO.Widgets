package dev.alllexey.itmowidgets.feature.sport.presentation.user

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportRepository
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class UserSportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userSport: UserSportRepository,
    private val sportSchedule: SportScheduleRepository
) : ViewModel() {

    private val isu: Int = checkNotNull(savedStateHandle.get<Int>(UserScreenArgs.ISU)) { "Sport needs an ISU" }
    val name: String = savedStateHandle.get<String>(UserScreenArgs.NAME).orEmpty()

    private val refreshes = RefreshTracker(viewModelScope)

    /** The last answer; `null` until the first one. */
    private val loaded = MutableStateFlow<AppResult<List<SportBooking>>?>(null)

    val uiState: StateFlow<UserSportUiState> = combine(loaded, refreshes.refreshing, ::toUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSportUiState.Loading)

    init {
        refresh(RefreshMode.Silent)
    }

    fun refresh(mode: RefreshMode) {
        refreshes.launch(mode) { loaded.value = loadBookings() }
    }

    private suspend fun loadBookings(): AppResult<List<SportBooking>> = coroutineScope {
        // Confirmed IDs are resolved against the ITMO catalog alone: the merged schedule
        // also waits for the viewer's queues and friends, which only the sport tab loads.
        val catalog = async { sportSchedule.refreshSportSchedule(); sportSchedule.observeSportCatalog().first() }
        when (val bookings = userSport.getUserBookings(isu)) {
            is AppResult.Failure -> AppResult.Failure(bookings.error)
            is AppResult.Success -> AppResult.Success(
                merge(bookings.value.confirmedLessonIds, bookings.value.pending, catalog.await())
            )
        }
    }

    /** A retry over an error shows progress until the answer; a pull keeps the list under the indicator. */
    private fun toUiState(result: AppResult<List<SportBooking>>?, refreshing: Boolean): UserSportUiState =
        when (result) {
            null -> UserSportUiState.Loading
            is AppResult.Failure -> if (refreshing) UserSportUiState.Loading else UserSportUiState.Error(result.error)
            is AppResult.Success -> UserSportUiState.Content(result.value, refreshing)
        }

    private fun merge(
        confirmedIds: List<Long>,
        pending: List<SportBooking>,
        catalog: AppResult<List<SportLesson>>
    ): List<SportBooking> {
        val lessons = catalog.valueOrNull().orEmpty().associateBy { it.lessonId }
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
