package dev.alllexey.itmowidgets.feature.sport.presentation.my

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.util.dataOrNull
import dev.alllexey.itmowidgets.core.util.errorOrNull
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportBookingDelegate
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SportMyUiState {

    object Loading : SportMyUiState()

    data class Content(
        val attempts: SportAttempts,
        val score: SportScore,
        val bookings: List<SportBooking>,
        val hasPartialError: Boolean = false,
        /** A refresh is running behind content that stays on screen. */
        val refreshing: Boolean = false
    ) : SportMyUiState()

    data class Error(val error: AppError) : SportMyUiState()
}

sealed interface SportMyEvent {
    data class ShowError(val error: AppError) : SportMyEvent
}

@HiltViewModel
class SportMyViewModel @Inject constructor(
    private val sportBookingRepository: SportBookingRepository,
    private val sportDataRepository: SportDataRepository,
    private val bookingDelegate: SportBookingDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow<SportMyUiState>(SportMyUiState.Loading)
    val uiState: StateFlow<SportMyUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<SportMyEvent>(Channel.BUFFERED)
    val events: Flow<SportMyEvent> = eventChannel.receiveAsFlow()

    private val isRefreshing = MutableStateFlow(false)

    private var observeJob: Job? = null
    private var lastContent: SportMyUiState.Content? = null

    init {
        observeData()
    }

    fun ensureDataLoaded() {
        if (_uiState.value is SportMyUiState.Loading && !isRefreshing.value) {
            refreshAllData()
        }
    }

    fun refreshAllData() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                awaitAll(
                    async { sportDataRepository.refreshSportAttempts() },
                    async { sportDataRepository.refreshSportScore() },
                    async { sportBookingRepository.refreshSportBookings() },
                    async { sportDataRepository.refreshSportAutoSignLimits() },
                    async { sportDataRepository.refreshSportQueueEntries() },
                    async { sportDataRepository.refreshFriendsBookings() }
                )
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun refreshMyItmoData() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                awaitAll(
                    async { sportDataRepository.refreshSportAttempts() },
                    async { sportDataRepository.refreshSportScore() },
                    async { sportBookingRepository.refreshSportBookings() }
                )
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun refreshCustomData() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                awaitAll(
                    async { sportDataRepository.refreshSportAutoSignLimits() },
                    async { sportDataRepository.refreshSportQueueEntries() },
                    async { sportDataRepository.refreshFriendsBookings() }
                )
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun cancelBooking(booking: SportBooking) {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                when (val result = bookingDelegate.cancel(booking)) {
                    is AppResult.Success -> Unit
                    is AppResult.Failure -> {
                        eventChannel.send(SportMyEvent.ShowError(result.error))
                    }
                }
            } finally {
                isRefreshing.value = false
            }
        }
    }

    private fun observeData() {
        observeJob?.cancel()

        observeJob = viewModelScope.launch {
            combine(
                sportDataRepository.observeSportAttempts(),
                sportDataRepository.observeSportScore(),
                sportBookingRepository.observeSportBookings(),
                isRefreshing
            ) { attemptsState, scoreState, bookingsState, refreshing ->
                val attempts = attemptsState.dataOrNull()
                val score = scoreState.dataOrNull()
                val bookings = bookingsState.dataOrNull()

                val errors = listOfNotNull(
                    attemptsState.errorOrNull(),
                    scoreState.errorOrNull(),
                    bookingsState.errorOrNull()
                )

                when {
                    attempts == null || score == null || bookings == null -> when {
                        // Sources that failed keep the last content on screen with a snackbar.
                        refreshing -> lastContent?.copy(refreshing = true) ?: SportMyUiState.Loading
                        lastContent != null -> lastContent!!.copy(hasPartialError = true, refreshing = false)
                        errors.isNotEmpty() -> SportMyUiState.Error(errors.first())
                        else -> SportMyUiState.Loading
                    }

                    else -> {
                        SportMyUiState.Content(
                            attempts = attempts,
                            score = score,
                            bookings = bookings,
                            hasPartialError = errors.isNotEmpty(),
                            refreshing = refreshing
                        ).also { lastContent = it }
                    }
                }
            }
                .collect {
                    _uiState.value = it
                }
        }
    }
}
