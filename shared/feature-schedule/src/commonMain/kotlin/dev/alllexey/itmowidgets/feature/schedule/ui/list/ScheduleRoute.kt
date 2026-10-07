package dev.alllexey.itmowidgets.feature.schedule.ui.list

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleEvent
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.SelectedUser
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.common_retry
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** What the host of [ScheduleRoute] asks of the schedule from outside the screen. */
sealed interface ScheduleRouteRequest {
    /** The `Сегодня` shortcut: the own schedule on today, whatever was read before. */
    data object Today : ScheduleRouteRequest

    /** The friend picker answered: [user]'s schedule, or the own one for null. */
    data class SelectUser(val user: SelectedUser?) : ScheduleRouteRequest
}

/** The navigation [ScheduleRoute] leaves to its host: the sheets and the friend picker. */
@Immutable
class ScheduleRouteActions(
    val onLessonClick: (Lesson, LocalDate) -> Unit = { _, _ -> },
    val onPendingClick: (PendingSportBooking) -> Unit = {},
    /** The picker, told which friend is shown now (null: the own schedule). */
    val onPickFriend: (SelectedUser?) -> Unit = {},
)

/**
 * The schedule list with its Koin ViewModel (`docs/features/schedule.md` § Behaviour). The first composition loads
 * the first page behind the cached days; the list asks for the next page near its end; a minute ticker re-resolves
 * the lesson states while the screen is started; a refresh failure behind content shows a snackbar with
 * `Повторить`. Where the list stands is [ScheduleListPlacement]'s: today once, the read day across a switch of
 * schedules, today again on [ScheduleRouteRequest.Today]; [listState] keeps the reader's position across recreation.
 *
 * [ownTab] is the schedule tab (the friends button, the `Сегодня` shortcut); a user's schedule screen passes false.
 * [requests] carries the host's results (the picker, the shortcut). Android hosts it in `ScheduleFragment` and
 * `UserScheduleFragment`, iOS in IO-09b.
 */
@Composable
fun ScheduleRoute(
    ownTab: Boolean,
    actions: ScheduleRouteActions,
    modifier: Modifier = Modifier,
    requests: Flow<ScheduleRouteRequest> = emptyFlow(),
    viewModel: ScheduleViewModel = koinViewModel(),
    timeProvider: AcademicTimeProvider = koinInject(),
    listState: LazyListState = rememberLazyListState(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val placement = rememberSaveable(saver = ScheduleListPlacement.Saver) { ScheduleListPlacement() }
    var now by remember(timeProvider) { mutableStateOf(timeProvider.localNow()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val todayPeek = with(LocalDensity.current) { TodayPeek.roundToPx() }

    val screenState = remember(uiState, now, ownTab, placement.positioning) {
        scheduleScreenState(uiState, now, timeProvider.timeZone, ownTab).copy(positioning = placement.positioning)
    }
    val shownDays by rememberUpdatedState((screenState.body as? ScheduleScreenBody.Days)?.days?.map { it.date })

    LaunchedEffect(viewModel) { viewModel.refresh(RefreshMode.Silent) }

    LaunchedEffect(viewModel, timeProvider, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                // Time changes the presentation only: nothing reloads and the list keeps its place.
                viewModel.updateTimeState()
                val current = timeProvider.localNow()
                now = current
                delay(untilNextMinute(current))
            }
        }
    }

    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is ScheduleEvent.ShowError -> {
                        val result = snackbars.showSnackbar(
                            message = getString(event.error.textResource()),
                            actionLabel = getString(Res.string.common_retry),
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.refresh(RefreshMode.Force)
                    }
                }
            }
        }
    }

    val selectUser: (SelectedUser?) -> Unit = remember(viewModel, placement, listState) {
        { user ->
            // Another schedule of the same days keeps the reader on the day and offset they were reading.
            placement.anchorOn(user?.isu, shownDays?.let { visibleDayAnchor(listState, it) })
            viewModel.setSelectedUser(user)
            viewModel.refresh(RefreshMode.Force)
        }
    }

    LaunchedEffect(requests, viewModel, placement) {
        requests.collect { request ->
            when (request) {
                ScheduleRouteRequest.Today -> {
                    placement.showToday()
                    if (viewModel.uiState.value.selectedUser != null) {
                        // The list goes to today once the own schedule arrives.
                        viewModel.setSelectedUser(null)
                        viewModel.refresh(RefreshMode.Force)
                    }
                }
                is ScheduleRouteRequest.SelectUser -> selectUser(request.user)
            }
        }
    }

    LaunchedEffect(uiState, placement.requests) {
        val content = uiState as? ScheduleUiState.Content ?: return@LaunchedEffect
        val days = shownDays ?: return@LaunchedEffect
        while (
            placement.place(
                days = days,
                userIsu = content.selectedUser?.isu,
                loadingMore = content.loadingMore,
                today = timeProvider.today(),
                todayPeek = todayPeek,
                listState = listState,
            )
        ) {
            viewModel.fetchNextDays()
            // A page that changes the content recomposes and places again; one that adds no day (it can answer
            // before a frame shows its loading) lets the placement decide again on the same days.
            val paged = viewModel.uiState.first { it !is ScheduleUiState.Content || !it.loadingMore }
            if (paged != content) return@LaunchedEffect
        }
    }

    val screenActions = remember(viewModel, actions, selectUser) {
        ScheduleScreenActions(
            onRefresh = { viewModel.refresh(RefreshMode.Pull) },
            onRetry = { viewModel.refresh(RefreshMode.Force) },
            onLoadMore = viewModel::fetchNextDays,
            onLessonClick = actions.onLessonClick,
            onPendingClick = actions.onPendingClick,
            onPickFriend = { actions.onPickFriend(viewModel.uiState.value.selectedUser) },
            onClearFriend = { selectUser(null) },
        )
    }
    ScheduleScreen(
        state = screenState,
        actions = screenActions,
        modifier = modifier,
        listState = listState,
        snackbarHostState = snackbars,
    )
}

/** Milliseconds from [now] to the start of the next minute, when lesson states can change. */
private fun untilNextMinute(now: LocalDateTime): Long =
    1.minutes.inWholeMilliseconds - now.second * MILLIS_PER_SECOND - now.nanosecond / NANOS_PER_MILLI

private const val MILLIS_PER_SECOND = 1_000L
private const val NANOS_PER_MILLI = 1_000_000L

/** Today opens with a sliver of the day before above it (the fragment's 20 px). */
private val TodayPeek = 8.dp
