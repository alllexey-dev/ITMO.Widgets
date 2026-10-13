package dev.alllexey.itmowidgets.feature.sport.presentation.common

import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.errorOrNull
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyEvent
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportBookingDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The `Мой спорт` data as the whole app sees it: the sport pages load it and cancel bookings through this one
 * holder. It outlives every screen and holds no user data: the repositories do, and
 * their session cleaners clear it.
 */
class SportBookingsHolder(
    private val sportBookingRepository: SportBookingRepository,
    private val sportDataRepository: SportDataRepository,
    private val bookingDelegate: SportBookingDelegate,
    private val scope: CoroutineScope
) {

    private val refreshes = RefreshTracker(scope)
    private val eventQueue = EventQueue<SportMyEvent>()
    private val inFlight = MutableStateFlow(0)

    /**
     * Whether the tab has nothing to show yet: a source has neither a value nor an error. Derived from the
     * repositories, never stored, so a cleared repository makes the next entry or lookup load again.
     */
    private val unloaded: StateFlow<Boolean> = combine(
        sportDataRepository.observeSportAttempts(),
        sportDataRepository.observeSportScore(),
        sportBookingRepository.observeSportBookings()
    ) { attempts, score, bookings ->
        val missing = attempts.valueOrNull() == null || score.valueOrNull() == null || bookings.valueOrNull() == null
        val failed = attempts.errorOrNull() != null || score.errorOrNull() != null || bookings.errorOrNull() != null
        missing && !failed
    }.stateIn(scope, SharingStarted.Eagerly, true)

    /** Refreshes and cancellations in flight, silent ones included: `Loading` waits for them instead of an error. */
    val operations: StateFlow<Int> = inFlight.asStateFlow()

    /** `true` while a refresh the user asked for is in flight. */
    val refreshing: StateFlow<Boolean> = refreshes.refreshing

    /** Cancellation errors; they wait for the tab when it is not on screen. */
    val events: Flow<SportMyEvent> = eventQueue.events

    /**
     * Loads the tab only while nothing is shown and nothing is in flight, so re-entering the tab with content
     * loaded starts no request. A guard, not a refresh: `refresh(Silent)` would reload loaded content.
     */
    fun ensureDataLoaded() {
        if (unloaded.value && inFlight.value == 0) refresh(RefreshMode.Silent)
    }

    /** A pull or a retry shows the indicator; the first load and background reloads stay silent. */
    fun refresh(mode: RefreshMode) {
        refreshes.launch(mode) { tracked { refreshAll() } }
    }

    fun cancel(booking: SportBooking) {
        scope.launch {
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
        inFlight.update { it + 1 }
        try {
            return operation()
        } finally {
            inFlight.update { it - 1 }
        }
    }
}
