package dev.alllexey.itmowidgets.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.platform.AppForeground
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.DayLessonsGateway
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.today.TodayTodo
import dev.alllexey.itmowidgets.core.today.TodayTodoSource
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

/**
 * Сегодня on both platforms: today and tomorrow from the schedule cache ([DayLessonsGateway]) and `Нужно сделать`
 * from every Koin [TodayTodoSource], re-evaluated every minute so the hero and the timeline move with the academic
 * clock. The first show refreshes once; each return of the app to the foreground ([AppForeground]) refreshes data
 * older than five minutes. Nothing else does: a tab switch or a sheet closing over Сегодня leaves the data as it is.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val lessons: DayLessonsGateway,
    todoSources: List<TodayTodoSource>,
    private val time: AcademicTimeProvider,
    foreground: AppForeground,
) : ViewModel() {

    private val tracker = RefreshTracker(viewModelScope)
    private val refresh = MutableStateFlow<TodayRefresh>(TodayRefresh.Pending)
    private var loaded = false
    private var refreshedAt: Instant? = null

    private val clock: Flow<LocalDateTime> = flow {
        while (true) {
            emit(time.localNow())
            delay(TICK)
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    private val days = clock.map { it.date }.distinctUntilChanged().flatMapLatest { today ->
        lessons.observe(today, today.plus(1, DateTimeUnit.DAY))
    }

    private val todos: Flow<List<TodayTodo>> = if (todoSources.isEmpty()) {
        flowOf(emptyList())
    } else {
        combine(todoSources.map { it.observe().catch { emit(emptyList()) } }) { lists -> lists.flatMap { it } }
    }

    val uiState: StateFlow<TodayUiState> = combine(clock, days, todos, refresh, tracker.refreshing) { now, days, todos, result, busy ->
        TodayPresenter.state(now, days, todos, result, busy, time.timeZone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TodayUiState.Loading)

    init {
        foreground.returns().onEach { refreshIfStale() }.launchIn(viewModelScope)
    }

    /** The first show refreshes once; later the app's returns to the foreground go through [refreshIfStale]. */
    fun ensureDataLoaded() {
        if (loaded) return
        loaded = true
        refresh(RefreshMode.Silent)
    }

    /** Refreshes only data older than five minutes. */
    private fun refreshIfStale() {
        val at = refreshedAt ?: return
        if (time.now() - at >= STALE_AFTER) refresh(RefreshMode.Silent)
    }

    fun refresh(mode: RefreshMode) {
        tracker.launch(mode) {
            val today = time.today()
            refresh.value = when (val result = lessons.refresh(today, today.plus(1, DateTimeUnit.DAY))) {
                is AppResult.Success -> TodayRefresh.Done
                is AppResult.Failure -> TodayRefresh.Failed(result.error)
            }
            refreshedAt = time.now()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val TICK = 1.minutes
        val STALE_AFTER = 5.minutes
    }
}
