package dev.alllexey.itmowidgets.feature.sport.presentation.common

import dev.alllexey.itmowidgets.core.coroutines.ApplicationScope
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.errorOrNull
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyEvent
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportBookingDelegate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime

/**
 * The `Мой спорт` data as the whole app sees it: the tab, the feed and the schedule load it, look bookings up and
 * cancel them through this one holder. It outlives every screen and holds no user data: the repositories do, and
 * their session cleaners clear it.
 */
@Singleton
class SportBookingsHolder @Inject constructor(
    private val sportBookingRepository: SportBookingRepository,
    private val sportDataRepository: SportDataRepository,
    private val bookingDelegate: SportBookingDelegate,
    private val timeProvider: AcademicTimeProvider,
    @ApplicationScope private val scope: CoroutineScope
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

    /**
     * The tab's merged bookings. Every source behind them is a replay flow that only emits after its own refresh,
     * so the first caller loads the tab's data exactly as opening the tab would; afterwards the answer is immediate.
     * Null when the bookings failed or did not arrive within [BOOKINGS_WAIT]; a `Loading` state is no answer.
     */
    suspend fun bookingsSnapshot(): List<SportBooking>? {
        ensureDataLoaded()
        val state = withTimeoutOrNull(BOOKINGS_WAIT) {
            sportBookingRepository.observeSportBookings().first { it !is LoadState.Loading }
        }
        return state?.valueOrNull()
    }

    suspend fun findSportBooking(lessonId: Long): SportBooking? =
        bookingsSnapshot()?.firstOrNull { it.lessonId == lessonId }

    /**
     * Several items can share a slot: a confirmed booking and a queue for another
     * section. The confirmed one wins, and the section name breaks the remaining ties.
     */
    suspend fun findSportBookingAt(date: LocalDate, start: LocalTime, subject: String): SportBooking? {
        val candidates = bookingsSnapshot()?.filter { booking ->
            val local = booking.start.toLocalDateTime(timeProvider.timeZone)
            local.date == date && local.time == start
        }.orEmpty()
        val wanted = subject.trim().lowercase()
        fun SportBooking.named() = wanted.isNotEmpty() && sectionName.raw.trim().lowercase().let { it in wanted || wanted in it }
        return candidates.firstOrNull { it.signed && it.named() }
            ?: candidates.firstOrNull { it.signed }
            ?: candidates.firstOrNull { it.named() }
            ?: candidates.firstOrNull()
    }

    /**
     * The booking a details sheet asked to cancel, when the sheet's [action] is still what the booking offers now.
     * Null for a missing booking, an action that changed meanwhile or one that offers nothing.
     */
    suspend fun cancelCandidate(lessonId: Long, action: String?): SportBooking? {
        val booking = findSportBooking(lessonId) ?: return null
        val expected = booking.toDetailsArgs().bookingAction(timeProvider.now())
        return booking.takeIf { expected != SportBookingAction.NONE && expected.name == action }
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

    private companion object {
        val BOOKINGS_WAIT = 8.seconds
    }
}
