package dev.alllexey.itmowidgets.feature.sport.presentation.my

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.errorOrNull
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingsHolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The `Мой спорт` screen over [SportBookingsHolder], which the feed and the schedule share. */
class SportMyViewModel(
    sportBookingRepository: SportBookingRepository,
    sportDataRepository: SportDataRepository,
    private val holder: SportBookingsHolder
) : ViewModel() {

    private var lastContent: SportMyUiState.Content? = null

    val uiState: StateFlow<SportMyUiState> = combine(
        sportDataRepository.observeSportAttempts(),
        sportDataRepository.observeSportScore(),
        sportBookingRepository.observeSportBookings(),
        holder.operations,
        holder.refreshing,
        ::toUiState
    ).stateIn(viewModelScope, SharingStarted.Eagerly, SportMyUiState.Loading)

    val events: Flow<SportMyEvent> = holder.events

    /** See [SportBookingsHolder.ensureDataLoaded]. */
    fun ensureDataLoaded() = holder.ensureDataLoaded()

    /** A pull or a retry shows the indicator; the first load and background reloads stay silent. */
    fun refresh(mode: RefreshMode) = holder.refresh(mode)

    fun cancelBooking(booking: SportBooking) = holder.cancel(booking)

    private fun toUiState(
        attemptsState: LoadState<SportAttempts>,
        scoreState: LoadState<SportScore>,
        bookingsState: LoadState<List<SportBooking>>,
        operationCount: Int,
        byUser: Boolean
    ): SportMyUiState {
        val attempts = attemptsState.valueOrNull()
        val score = scoreState.valueOrNull()
        val bookings = bookingsState.valueOrNull()
        val errors = listOfNotNull(attemptsState.errorOrNull(), scoreState.errorOrNull(), bookingsState.errorOrNull())
        // Both sources are only `Loading` again after the session was cleared: the last content was another session's.
        if (attemptsState == LoadState.Loading && scoreState == LoadState.Loading) lastContent = null
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
