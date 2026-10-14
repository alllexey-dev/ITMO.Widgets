package dev.alllexey.itmowidgets.feature.schedule.presentation.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.platform.AppForeground
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleDays
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * The schedule feed on both platforms (docs/features/schedule.md): a window of whole weeks around the visible day,
 * read from [ScheduleDays] and drawn by [ScheduleFeedPresenter], re-evaluated every minute. The view reports the day at
 * the top ([onVisibleDate]) and its ends ([onNearStart], [onNearEnd]): the window grows by two weeks there. A jump to a day (the
 * strip, the month sheet, `Сегодня`) brings its weeks in and asks the view to scroll there once ([jump] in the state).
 *
 * With `ARG_USER_ISU` the feed is that user's on a pushed screen; otherwise it is the tab's own schedule, or a friend's
 * picked with [selectUser], kept across process death. The first show loads the window once ([refresh] with
 * `Silent`); after that only a pull, a retry and each return of the app to the foreground ([AppForeground]) reload it,
 * never a tab switch, a pop or a sheet closing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleFeedViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val days: ScheduleDays,
    private val time: AcademicTimeProvider,
    private val calendarSync: CalendarSync,
    foreground: AppForeground,
) : ViewModel() {

    private val owner = MutableStateFlow(initialOwner())
    private val window = MutableStateFlow(around(time.today()))
    private val visible = MutableStateFlow(time.today())
    private val known = MutableStateFlow<Set<LocalDate>>(emptySet())
    private val status = MutableStateFlow(Status())
    private val jump = MutableStateFlow<FeedJump?>(FeedJump(time.today(), 0))
    private var jumps = 0
    private var generation = 0
    private var started = false
    private var cachedEmpty = true

    init {
        foreground.returns().onEach { if (started) reloadWindow() }.launchIn(viewModelScope)
    }

    private val eventQueue = EventQueue<ScheduleFeedEvent>()
    val events: Flow<ScheduleFeedEvent> = eventQueue.events

    private val clock: Flow<LocalDateTime> = flow {
        while (true) {
            val now = time.localNow()
            emit(now)
            delay(1.minutes - (now.second * MILLIS_PER_SECOND + now.nanosecond / NANOS_PER_MILLI).milliseconds)
        }
    }

    private val cached: Flow<Cached> = combine(owner, window) { owner, window -> owner to window }
        .flatMapLatest { (owner, window) ->
            days.observe(owner.user?.isu, window.start, window.endInclusive).map { Cached(owner, window, it) }
        }
        .onEach { cachedEmpty = it.days.isEmpty() }

    val uiState: StateFlow<ScheduleFeedUiState> = combine(
        clock,
        cached,
        combine(known, visible, ::Pair),
        status,
        jump,
    ) { now, cached, (known, visible), status, jump ->
        ScheduleFeedPresenter.state(
            FeedInput(
                now = now,
                owner = cached.owner,
                window = cached.window,
                days = cached.days,
                known = known,
                visible = visible,
                loading = status.loading > 0,
                error = status.error,
                denied = status.denied,
                refreshing = status.refreshing,
                jump = jump,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initialState())

    /**
     * [RefreshMode.Silent] loads the window once, on the first show; [RefreshMode.Pull] and [RefreshMode.Force]
     * reload it with the indicator, and on the own schedule bring the phone's calendar up to date when it is synced.
     */
    fun refresh(mode: RefreshMode) {
        if (mode == RefreshMode.Silent && started) return
        started = true
        reloadWindow(indicator = mode.showsIndicator)
    }

    private fun reloadWindow(indicator: Boolean = false) {
        val range = window.value
        load(range.start, range.endInclusive, indicator)
    }

    /** The view's top day changed: the header, the strip and `Сегодня` follow it. */
    fun onVisibleDate(date: LocalDate) {
        visible.value = date
    }

    /** The view came near the end of the feed: two more weeks join it and load. */
    fun onNearEnd() {
        val range = window.value
        val end = range.endInclusive.plus(GROWTH_WEEKS, DateTimeUnit.WEEK)
        window.value = range.start..end
        load(range.endInclusive.plus(1, DateTimeUnit.DAY), end, indicator = false)
    }

    /** The view came near the start of the feed: two earlier weeks join it and load. */
    fun onNearStart() {
        val range = window.value
        val start = range.start.minus(GROWTH_WEEKS, DateTimeUnit.WEEK)
        window.value = start..range.endInclusive
        load(start, range.start.minus(1, DateTimeUnit.DAY), indicator = false)
    }

    /** Scrolls the feed to [date] (a strip day, the month sheet's `Открыть`), bringing its weeks in first. */
    fun showDate(date: LocalDate) {
        val range = window.value
        val target = around(date)
        when {
            date in range -> Unit
            target.start <= range.endInclusive.plus(GROWTH_WEEKS, DateTimeUnit.WEEK) &&
                target.endInclusive >= range.start.minus(GROWTH_WEEKS, DateTimeUnit.WEEK) -> {
                window.value = minOf(range.start, target.start)..maxOf(range.endInclusive, target.endInclusive)
                if (target.start < range.start) load(target.start, range.start.minus(1, DateTimeUnit.DAY), false)
                if (target.endInclusive > range.endInclusive) {
                    load(range.endInclusive.plus(1, DateTimeUnit.DAY), target.endInclusive, false)
                }
            }
            else -> {
                window.value = target
                load(target.start, target.endInclusive, indicator = false)
            }
        }
        visible.value = date
        jumps++
        jump.value = FeedJump(date, jumps)
    }

    /** The strip was swiped to the week at [index]: its Monday, or today on the current week. */
    fun showWeek(index: Int) {
        val monday = ScheduleFeedPresenter.monday(window.value.start).plus(index, DateTimeUnit.WEEK)
        val today = time.today()
        showDate(if (ScheduleFeedPresenter.monday(today) == monday) today else monday)
    }

    /** `Сегодня`. */
    fun showToday() = showDate(time.today())

    /** The `Сегодня` shortcut: the own schedule on today, whatever was shown. */
    fun openToday() {
        if (owner.value is FeedOwner.Friend) selectUser(null)
        showToday()
    }

    /** The view scrolled to the jump [id]. */
    fun onJumped(id: Int) {
        if (jump.value?.id == id) jump.value = null
    }

    /** The friend picker answered: [user]'s schedule, or the own one for null, on the same day. */
    fun selectUser(user: SelectedUser?) {
        if (owner.value is FeedOwner.Other) return
        val next = user?.let(FeedOwner::Friend) ?: FeedOwner.Own
        if (next == owner.value) return
        savedStateHandle[STATE_FRIEND_ISU] = user?.isu
        savedStateHandle[STATE_FRIEND_NAME] = user?.name
        savedStateHandle[STATE_FRIEND_AVATAR] = user?.avatar
        savedStateHandle[STATE_FRIEND_GROUP] = user?.group
        generation++
        known.value = emptySet()
        status.value = Status()
        cachedEmpty = true
        owner.value = next
        val range = window.value
        load(range.start, range.endInclusive, indicator = false)
    }

    private fun load(from: LocalDate, to: LocalDate, indicator: Boolean) {
        val loadOwner = owner.value
        val loadGeneration = generation
        status.update { it.copy(loading = it.loading + 1, refreshing = it.refreshing || indicator) }
        viewModelScope.launch {
            val result = days.refresh(loadOwner.user?.isu, from, to)
            // A switch of schedules dropped this answer's status; it must not land on the other schedule.
            if (loadGeneration != generation) return@launch
            when (result) {
                is AppResult.Success -> {
                    known.update { it + datesOf(from, to) }
                    status.update { it.copy(error = null) }
                    if (indicator && loadOwner == FeedOwner.Own) calendarSync.requestSync()
                }
                is AppResult.Failure -> when {
                    loadOwner.user != null && result.error in DENIALS -> status.update { it.copy(denied = result.error) }
                    cachedEmpty && known.value.isEmpty() -> status.update { it.copy(error = result.error) }
                    else -> eventQueue.send(ScheduleFeedEvent.ShowError(result.error))
                }
            }
            status.update {
                val loading = it.loading - 1
                it.copy(loading = loading, refreshing = it.refreshing && loading > 0)
            }
        }
    }

    private fun initialOwner(): FeedOwner {
        val isu = savedStateHandle.get<Int>(ARG_USER_ISU)?.takeIf { it > 0 }
        if (isu != null) return FeedOwner.Other(SelectedUser(isu, savedStateHandle.get<String>(ARG_USER_NAME).orEmpty(), null))
        val friend = savedStateHandle.get<Int>(STATE_FRIEND_ISU) ?: return FeedOwner.Own
        return FeedOwner.Friend(
            SelectedUser(
                isu = friend,
                name = savedStateHandle.get<String>(STATE_FRIEND_NAME).orEmpty(),
                avatar = savedStateHandle[STATE_FRIEND_AVATAR],
                group = savedStateHandle[STATE_FRIEND_GROUP],
            ),
        )
    }

    private fun initialState() = ScheduleFeedPresenter.state(
        FeedInput(
            now = time.localNow(),
            owner = owner.value,
            window = window.value,
            days = emptyList(),
            known = emptySet(),
            visible = visible.value,
            loading = true,
            error = null,
            denied = null,
            refreshing = false,
            jump = jump.value,
        ),
    )

    /** The weeks the feed shows around [date]: the one before, its own and two after. */
    private fun around(date: LocalDate): ClosedRange<LocalDate> {
        val monday = ScheduleFeedPresenter.monday(date)
        return monday.minus(1, DateTimeUnit.WEEK)..monday.plus(WINDOW_WEEKS_AHEAD, DateTimeUnit.WEEK).minus(1, DateTimeUnit.DAY)
    }

    private fun datesOf(from: LocalDate, to: LocalDate): Set<LocalDate> =
        generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= to }.toSet()

    private data class Cached(val owner: FeedOwner, val window: ClosedRange<LocalDate>, val days: List<LessonDay>)

    /** [loading] counts the refreshes in flight; [refreshing] is a pull's indicator. */
    private data class Status(
        val loading: Int = 0,
        val refreshing: Boolean = false,
        val error: AppError? = null,
        val denied: AppError? = null,
    )

    companion object {
        const val ARG_USER_ISU = UserScreenArgs.ISU
        const val ARG_USER_NAME = UserScreenArgs.NAME
        private const val STATE_FRIEND_ISU = "friend_isu"
        private const val STATE_FRIEND_NAME = "friend_name"
        private const val STATE_FRIEND_AVATAR = "friend_avatar"
        private const val STATE_FRIEND_GROUP = "friend_group"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val GROWTH_WEEKS = 2
        private const val WINDOW_WEEKS_AHEAD = 3
        private const val MILLIS_PER_SECOND = 1_000
        private const val NANOS_PER_MILLI = 1_000_000

        /** Answers that close another user's schedule to the viewer rather than fail a refresh. */
        private val DENIALS = setOf(
            AppError.Forbidden,
            AppError.Unauthorized,
            AppError.NotFound,
            AppError.CustomServicesDisabled,
        )
    }
}
