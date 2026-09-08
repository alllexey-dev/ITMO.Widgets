package dev.alllexey.itmowidgets.feature.schedule.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val timeProvider: AcademicTimeProvider,
    private val savedStateHandle: SavedStateHandle,
    private val preferences: SchedulePreferencesRepository,
    private val pendingRepository: PendingSportBookingsRepository
) : ViewModel() {

    private val rootUserIsu = savedStateHandle
        .get<Int>(ARG_USER_ISU)
        ?.takeIf { it != NO_USER_ISU }

    private var selectedUser = restoreSelectedUser()
    private var currentStart = initialStart()
    private var currentEnd = initialEnd()
    private var currentDays = emptyList<DaySchedule>()
    private var observedUserIsu: Int? = activeUserIsu()
    private var isLoading = false
    private var hasSuccessfulOfficialLoad = false
    private var lastError: AppError? = null
    private var observeJob: Job? = null
    private var refreshJob: Job? = null
    private var hasStarted = false
    private var sportAutoSignEnabled = false
    private var pendingSport = emptyList<PendingSportBooking>()
    private var pendingObserveJob: Job? = null
    private var pendingRefreshJob: Job? = null

    private val _uiState = MutableStateFlow<ScheduleUiState>(
        ScheduleUiState.Loading(selectedUser)
    )
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<ScheduleEvent>(capacity = Channel.BUFFERED)
    val events: Flow<ScheduleEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            preferences.observeSportAutoSignEnabled().distinctUntilChanged().collect { enabled ->
                sportAutoSignEnabled = enabled
                updatePendingObservation()
                if (hasStarted) emitCurrentState()
            }
        }
    }

    fun ensureDataLoaded() {
        if (observeJob == null) {
            loadInitialSchedule()
        }
    }

    fun updateTimeState() {
        if (hasStarted) emitCurrentState()
    }

    fun loadInitialSchedule(forceRefresh: Boolean = false) {
        hasStarted = true
        refreshJob?.cancel()
        val userIsu = activeUserIsu()
        val keepLoadedRange = forceRefresh && observedUserIsu == userIsu && observeJob != null
        if (!keepLoadedRange) {
            resetRange()
        }
        prepareForUser(userIsu)
        isLoading = true
        lastError = null
        emitCurrentState()
        if (!keepLoadedRange) {
            observeRange()
        }
        updatePendingObservation(refresh = true)

        val startDate = currentStart
        val endDate = currentEnd

        refreshJob = viewModelScope.launch {
            val result = repository.refreshSchedule(
                userIsu = userIsu,
                startDate = startDate,
                endDate = endDate
            )
            ensureActive()
            handleRefreshResult(result)
        }
    }

    fun fetchNextDays() {
        if (isLoading) return

        val newStart = currentEnd.plusDays(1)
        val newEnd = currentEnd.plusDays(PAGE_SIZE_DAYS)
        currentEnd = newEnd
        isLoading = true
        lastError = null
        emitCurrentState()
        observeRange()

        val userIsu = activeUserIsu()
        refreshJob = viewModelScope.launch {
            val result = repository.refreshSchedule(
                userIsu = userIsu,
                startDate = newStart,
                endDate = newEnd
            )
            ensureActive()
            handleRefreshResult(result)
        }
    }

    fun setSelectedUser(user: SelectedUser?) {
        selectedUser = user
        savedStateHandle[STATE_SELECTED_USER_ISU] = user?.isu
        savedStateHandle[STATE_SELECTED_USER_NAME] = user?.name
        savedStateHandle[STATE_SELECTED_USER_AVATAR] = user?.avatar
        updatePendingObservation()
        emitCurrentState()
    }

    private fun canShowPendingSport() = hasStarted && sportAutoSignEnabled &&
        activeUserIsu() == null && observedUserIsu == null

    private fun updatePendingObservation(refresh: Boolean = false) {
        if (!canShowPendingSport()) {
            pendingObserveJob?.cancel()
            pendingObserveJob = null
            pendingRefreshJob?.cancel()
            pendingRefreshJob = null
            pendingSport = emptyList()
            return
        }
        val startObservation = pendingObserveJob == null
        if (startObservation) {
            pendingObserveJob = viewModelScope.launch {
                pendingRepository.observePendingBookings().collect { state ->
                    ensureActive()
                    // When either queue or confirmed-booking data is unavailable,
                    // old pending rows may already be signed. Hide this optional
                    // overlay rather than inventing a still-active queue.
                    pendingSport = when (state) {
                        is DataState.Success -> state.data
                        is DataState.Error -> emptyList()
                    }
                    emitCurrentState()
                }
            }
        }
        if (refresh || startObservation) {
            pendingRefreshJob?.cancel()
            pendingRefreshJob = viewModelScope.launch { pendingRepository.refresh() }
        }
    }

    private fun prepareForUser(userIsu: Int?) {
        if (observedUserIsu != userIsu) {
            currentDays = emptyList()
            hasSuccessfulOfficialLoad = false
            observedUserIsu = userIsu
        }
    }

    private fun observeRange() {
        observeJob?.cancel()
        val userIsu = activeUserIsu()
        val start = currentStart
        val end = currentEnd

        observeJob = viewModelScope.launch {
            repository.observeScheduleForRange(userIsu, start, end).collect { days ->
                currentDays = days
                emitCurrentState()
            }
        }
    }

    private suspend fun handleRefreshResult(result: AppResult<Unit>) {
        isLoading = false

        when (result) {
            is AppResult.Success -> {
                hasSuccessfulOfficialLoad = true
                lastError = null
            }
            is AppResult.Failure -> {
                if (!hasDisplayableContent(currentDisplayDays())) {
                    lastError = result.error
                } else {
                    eventChannel.send(ScheduleEvent.ShowError(result.error))
                }
            }
        }

        emitCurrentState()
    }

    private fun currentDisplayDays() = buildScheduleDisplayDays(
        currentDays, if (canShowPendingSport()) pendingSport else emptyList(),
        currentStart, currentEnd, timeProvider.zoneId, timeProvider.now()
    )

    // A successful empty academic response is still a usable snapshot. Keep its
    // pending rows during refresh/pagination, but never mask an initial failure
    // or retain rows after their optional source has been disabled or invalidated.
    private fun hasDisplayableContent(displayDays: List<ScheduleDisplayDay>) =
        currentDays.isNotEmpty() ||
            (hasSuccessfulOfficialLoad && lastError == null && displayDays.isNotEmpty())

    private fun emitCurrentState() {
        val displayDays = currentDisplayDays()
        _uiState.value = when {
            hasDisplayableContent(displayDays) -> ScheduleUiState.Content(
                schedule = currentDays,
                loadingMore = isLoading,
                selectedUser = selectedUser,
                displayDays = displayDays
            )

            isLoading -> ScheduleUiState.Loading(selectedUser)
            lastError != null -> ScheduleUiState.Error(
                error = checkNotNull(lastError),
                selectedUser = selectedUser
            )

            else -> ScheduleUiState.Empty(selectedUser)
        }
    }

    private fun restoreSelectedUser(): SelectedUser? {
        val isu = savedStateHandle.get<Int>(STATE_SELECTED_USER_ISU) ?: return null
        val name = savedStateHandle.get<String>(STATE_SELECTED_USER_NAME) ?: return null
        return SelectedUser(
            isu = isu,
            name = name,
            avatar = savedStateHandle[STATE_SELECTED_USER_AVATAR]
        )
    }

    private fun activeUserIsu(): Int? = selectedUser?.isu ?: rootUserIsu

    private fun resetRange() {
        currentStart = initialStart()
        currentEnd = initialEnd()
    }

    private fun initialStart(): LocalDate = timeProvider.today().minusDays(1)

    private fun initialEnd(): LocalDate = timeProvider.today().plusDays(PAGE_SIZE_DAYS)

    companion object {
        const val ARG_USER_ISU = "user_isu"
        private const val NO_USER_ISU = -1
        private const val PAGE_SIZE_DAYS = 14L
        private const val STATE_SELECTED_USER_ISU = "selected_user_isu"
        private const val STATE_SELECTED_USER_NAME = "selected_user_name"
        private const val STATE_SELECTED_USER_AVATAR = "selected_user_avatar"
    }
}
