package dev.alllexey.itmowidgets.feature.sport.presentation.my

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.errorOrNull
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportBookingDelegate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SportMyViewModel @Inject constructor(
    private val sportBookingRepository: SportBookingRepository,
    private val sportDataRepository: SportDataRepository,
    private val bookingDelegate: SportBookingDelegate
) : ViewModel() {

    private val refreshes = RefreshTracker(viewModelScope)
    private val eventQueue = EventQueue<SportMyEvent>()

    /** Refreshes and cancellations in flight, silent ones included: `Loading` waits for them instead of an error. */
    private val operations = MutableStateFlow(0)
    private var lastContent: SportMyUiState.Content? = null

    val uiState: StateFlow<SportMyUiState> = combine(
        sportDataRepository.observeSportAttempts(),
        sportDataRepository.observeSportScore(),
        sportBookingRepository.observeSportBookings(),
        operations,
        refreshes.refreshing,
        ::toUiState
    ).stateIn(viewModelScope, SharingStarted.Eagerly, SportMyUiState.Loading)

    val events: Flow<SportMyEvent> = eventQueue.events

    /**
     * Loads the tab only while nothing is shown and nothing is in flight, so re-entering the tab with content
     * loaded starts no request. A guard, not a refresh: `refresh(Silent)` would reload loaded content.
     */
    fun ensureDataLoaded() {
        if (uiState.value is SportMyUiState.Loading && operations.value == 0) refresh(RefreshMode.Silent)
    }

    /** A pull or a retry shows the indicator; the first load and background reloads stay silent. */
    fun refresh(mode: RefreshMode) {
        refreshes.launch(mode) { tracked { refreshAll() } }
    }

    fun cancelBooking(booking: SportBooking) {
        viewModelScope.launch {
            val result = tracked { bookingDelegate.cancel(booking) }
            if (result is AppResult.Failure) eventQueue.send(SportMyEvent.ShowError(result.error))
        }
    }

    private suspend fun refreshAll() {
        coroutineScope {
            awaitAll(
                async { sportDataRepository.refreshSportAttempts() },
                async { sportDataRepository.refreshSportScore() },
                async { sportBookingRepository.refreshSportBookings() },
                async { sportDataRepository.refreshSportAutoSignLimits() },
                async { sportDataRepository.refreshSportQueueEntries() },
                async { sportDataRepository.refreshFriendsBookings() }
            )
        }
    }

    private suspend fun <T> tracked(operation: suspend () -> T): T {
        operations.update { it + 1 }
        try {
            return operation()
        } finally {
            operations.update { it - 1 }
        }
    }

    private fun toUiState(
        attemptsState: AppResult<SportAttempts>,
        scoreState: AppResult<SportScore>,
        bookingsState: LoadState<List<SportBooking>>,
        operationCount: Int,
        byUser: Boolean
    ): SportMyUiState {
        val attempts = attemptsState.valueOrNull()
        val score = scoreState.valueOrNull()
        val bookings = bookingsState.valueOrNull()
        val errors = listOfNotNull(attemptsState.errorOrNull(), scoreState.errorOrNull(), bookingsState.errorOrNull())
        val previous = lastContent

        if (attempts == null || score == null || bookings == null) {
            return when {
                // Sources that failed keep the last content on screen with a snackbar.
                operationCount > 0 -> previous?.copy(refreshing = byUser) ?: SportMyUiState.Loading
                previous != null -> previous.copy(hasPartialError = true, refreshing = false)
                errors.isNotEmpty() -> SportMyUiState.Error(errors.first())
                else -> SportMyUiState.Loading
            }
        }
        return SportMyUiState.Content(
            attempts = attempts,
            score = score,
            bookings = bookings,
            hasPartialError = errors.isNotEmpty(),
            refreshing = byUser
        ).also { lastContent = it }
    }
}
