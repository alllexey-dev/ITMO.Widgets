package dev.alllexey.itmowidgets.feature.schedule.ui.feed

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.ScheduleFeedEvent
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.ScheduleFeedViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.SelectedUser
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString

/** What the host of the feed delivers from outside the screen. */
sealed interface ScheduleFeedRequest {
    /** The `Сегодня` shortcut: the own schedule on today. */
    data object Today : ScheduleFeedRequest

    /** The friend picker answered: [user]'s schedule, or the own one for null. */
    data class SelectUser(val user: SelectedUser?) : ScheduleFeedRequest

    /** The month sheet's `Открыть`. */
    data class ShowDate(val date: LocalDate) : ScheduleFeedRequest
}

/** The sheets and screens the feed opens through its host. */
data class ScheduleFeedHost(
    /** The month sheet on [date] of [userIsu]'s lessons (null: the own). */
    val onMonth: (date: LocalDate, userIsu: Int?) -> Unit = { _, _ -> },
    /** The friend picker, told whose schedule is shown (null: the own). */
    val onPickFriend: (shownIsu: Int?) -> Unit = {},
    val onLesson: (LessonTarget) -> Unit = {},
    /** A pushed screen's back; null on the tab. */
    val onBack: (() -> Unit)? = null,
)

/**
 * The feed with its ViewModel: the first show loads once, the host's [requests] (the shortcut, the picker, the month
 * sheet) reach the ViewModel, and a refresh that fails behind content shows a snackbar with `Повторить`.
 */
@Composable
fun ScheduleFeedRoute(
    host: ScheduleFeedHost,
    viewModel: ScheduleFeedViewModel,
    requests: Flow<ScheduleFeedRequest> = emptyFlow(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.refresh(RefreshMode.Silent) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ScheduleFeedEvent.ShowError -> {
                    val result = snackbars.showSnackbar(
                        message = event.error.toUiText().resolve(),
                        actionLabel = getString(Res.string.common_retry),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.refresh(RefreshMode.Force)
                }
            }
        }
    }
    LaunchedEffect(requests, viewModel) {
        requests.collect { request ->
            when (request) {
                ScheduleFeedRequest.Today -> viewModel.openToday()
                is ScheduleFeedRequest.SelectUser -> viewModel.selectUser(request.user)
                is ScheduleFeedRequest.ShowDate -> viewModel.showDate(request.date)
            }
        }
    }
    val actions = remember(viewModel, host) {
        ScheduleFeedActions(
            onRefresh = { viewModel.refresh(RefreshMode.Pull) },
            onRetry = { viewModel.refresh(RefreshMode.Force) },
            onVisibleDate = viewModel::onVisibleDate,
            onNearStart = viewModel::onNearStart,
            onNearEnd = viewModel::onNearEnd,
            onDay = viewModel::showDate,
            onWeek = viewModel::showWeek,
            onWeekPagingStarted = viewModel::beginWeekPaging,
            onToday = viewModel::showToday,
            onJumped = viewModel::onJumped,
            onMonth = { host.onMonth(viewModel.uiState.value.selected, null) },
            onPickFriend = { host.onPickFriend(viewModel.uiState.value.friend?.isu) },
            onBackToMine = { viewModel.selectUser(null) },
            onLesson = host.onLesson,
            onBack = host.onBack,
        )
    }
    ScheduleFeedScreen(state, actions, snackbars = snackbars)
}
