package dev.alllexey.itmowidgets.feature.sport.ui.my

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateActionStyle
import dev.alllexey.itmowidgets.designsystem.components.state.Skeleton
import dev.alllexey.itmowidgets.designsystem.components.state.SkeletonStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.bookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.toDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyEvent
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyUiState
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.sport_bookings_empty_description
import dev.alllexey.itmowidgets.shared.feature.sport.sport_bookings_empty_title
import dev.alllexey.itmowidgets.shared.feature.sport.sport_bookings_open_schedule
import kotlin.time.Instant
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.common_back
import dev.alllexey.itmowidgets.shared.core.common_load_error_title
import dev.alllexey.itmowidgets.shared.core.common_partial_load_error
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.core.sport_cancel_booking_action
import dev.alllexey.itmowidgets.shared.core.sport_cancel_booking_question
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_calendar_add
import dev.alllexey.itmowidgets.shared.designsystem.ic_error

/**
 * What the `Мой спорт` screen asks of its host. [onRefresh] is the user's pull and [onRetry] a retry after an error;
 * [onRequestCancel] asks to show the confirmation for a booking ([SportMyScreen]'s `pendingCancel`), which ends in
 * [onConfirmCancel] or [onDismissCancel]. [onOpenSign] leaves for `Запись`.
 */
@Immutable
class SportMyActions(
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenSign: () -> Unit = {},
    val onOpenDetails: (SportBooking) -> Unit = {},
    val onOpenMap: (SportBooking) -> Unit = {},
    val onRequestCancel: (SportBooking) -> Unit = {},
    val onConfirmCancel: (SportBooking) -> Unit = {},
    val onDismissCancel: () -> Unit = {},
)

/**
 * The `Мой спорт` page: placeholder cards before the first snapshot, the error with a retry when nothing loaded,
 * otherwise the score card over the bookings, which collapses into a compact bar as the list scrolls under it
 * ([SportScoreCollapsingLayout]). Without bookings the expanded card stays above an empty state whose button opens
 * `Запись`. Pull to refresh shows [SportMyUiState.Content.refreshing], which only the user starts; a partial failure
 * keeps the content and offers a retry in a snackbar. [pendingCancel] shows the cancellation's confirmation.
 *
 * [animateScore] runs the score card's count-up; previews switch it off to capture the end state.
 */
@Composable
fun SportMyScreen(
    state: SportMyUiState,
    time: AcademicTimeProvider,
    actions: SportMyActions,
    modifier: Modifier = Modifier,
    pendingCancel: SportBooking? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
    animateScore: Boolean = true,
) {
    val content = state as? SportMyUiState.Content
    AppRefreshBox(
        refreshing = content?.refreshing == true,
        onRefresh = actions.onRefresh,
        modifier = modifier
            .fillMaxSize()
            .background(ItmoTheme.colorScheme.background),
    ) {
        when (state) {
            SportMyUiState.Loading -> Skeleton(
                SkeletonStyle.Cards,
                Modifier
                    .fillMaxSize()
                    .testTag(SportMyScreenTestTags.SKELETON),
                rows = SKELETON_ROWS,
                rowHeight = SkeletonRowHeight,
            )
            is SportMyUiState.Error -> ContentState(
                title = stringResource(CoreRes.string.common_load_error_title),
                modifier = Modifier.testTag(SportMyScreenTestTags.ERROR),
                icon = painterResource(KitRes.drawable.ic_error),
                description = stringResource(state.error.textResource()),
                action = ContentStateAction(stringResource(CoreRes.string.common_retry), actions.onRetry),
            )
            is SportMyUiState.Content -> Bookings(state, time, actions, listState, animateScore)
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    PartialFailureSnackbar(content?.hasPartialError == true, snackbarHostState, actions.onRetry)
    if (pendingCancel != null) {
        ConfirmDialog(
            title = null,
            text = stringResource(CoreRes.string.sport_cancel_booking_question),
            confirmLabel = stringResource(CoreRes.string.sport_cancel_booking_action),
            dismissLabel = stringResource(CoreRes.string.common_back),
            onConfirm = { actions.onConfirmCancel(pendingCancel) },
            onDismiss = actions.onDismissCancel,
        )
    }
}

/**
 * The booking a details sheet's result asks to cancel: the one with [lessonId] while its offer at [now] is still
 * the [action] the sheet showed. Null when the booking is gone or its offer changed since, so a stale result does
 * nothing.
 */
fun SportMyUiState.cancelCandidate(lessonId: Long, action: String?, now: Instant): SportBooking? {
    val booking = (this as? SportMyUiState.Content)?.bookings?.firstOrNull { it.lessonId == lessonId } ?: return null
    val current = booking.toDetailsArgs().bookingAction(now)
    return booking.takeIf { current != SportBookingAction.NONE && current.name == action }
}

/** Shows a one-off [SportMyEvent] (a cancellation failed) in [SportMyScreen]'s snackbar. */
suspend fun SnackbarHostState.showSportMyEvent(event: SportMyEvent) {
    when (event) {
        is SportMyEvent.ShowError -> showSnackbar(getString(event.error.textResource()), duration = SnackbarDuration.Long)
    }
}

/** Tags for host tests and the screen's instrumented flow. */
object SportMyScreenTestTags {
    const val LIST = "sport_my_list"
    const val SKELETON = "sport_my_skeleton"
    const val EMPTY = "sport_my_empty"
    const val ERROR = "sport_my_error"
}

@Composable
private fun Bookings(
    state: SportMyUiState.Content,
    time: AcademicTimeProvider,
    actions: SportMyActions,
    listState: LazyListState,
    animateScore: Boolean,
) {
    val collapse = rememberSportScoreCollapseState(listState)
    val cardActions = remember(actions) {
        SportBookingActions(
            onOpen = actions.onOpenDetails,
            onCancel = actions.onRequestCancel,
            onOpenMap = actions.onOpenMap,
        )
    }
    val bottom = ItmoTheme.spacing.group
    SportScoreCollapsingLayout(
        state = collapse,
        card = { SportScoreCard(state.score, collapse = collapse, animated = animateScore) },
        modifier = Modifier.fillMaxSize(),
    ) { reserved ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .testTag(SportMyScreenTestTags.LIST),
            state = listState,
            contentPadding = PaddingValues(top = reserved.calculateTopPadding(), bottom = bottom),
        ) {
            if (state.bookings.isEmpty()) {
                item(key = EMPTY_KEY, contentType = EMPTY_KEY) {
                    ContentState(
                        title = stringResource(Res.string.sport_bookings_empty_title),
                        modifier = Modifier
                            .fillParentMaxSize()
                            .testTag(SportMyScreenTestTags.EMPTY),
                        icon = painterResource(KitRes.drawable.ic_calendar_add),
                        description = stringResource(Res.string.sport_bookings_empty_description),
                        action = ContentStateAction(
                            stringResource(Res.string.sport_bookings_open_schedule),
                            actions.onOpenSign,
                            ContentStateActionStyle.Filled,
                        ),
                    )
                }
            } else {
                items(state.bookings, key = { it.lessonId }, contentType = { BOOKING_CONTENT_TYPE }) { booking ->
                    SportBookingCard(booking, time, cardActions)
                }
            }
        }
    }
}

/** While [shown], a long snackbar with a retry; it goes away when the failure does, as the View's did. */
@Composable
private fun PartialFailureSnackbar(shown: Boolean, snackbarHostState: SnackbarHostState, onRetry: () -> Unit) {
    val message = stringResource(CoreRes.string.common_partial_load_error)
    val retry = stringResource(CoreRes.string.common_retry)
    val currentOnRetry by rememberUpdatedState(onRetry)
    LaunchedEffect(shown, snackbarHostState) {
        if (!shown) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(message, retry, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) currentOnRetry()
    }
}

/** The View page's skeleton: four 120 dp cards. */
private const val SKELETON_ROWS = 4
private val SkeletonRowHeight = 120.dp

private const val EMPTY_KEY = "sport_my_empty"
private const val BOOKING_CONTENT_TYPE = "sport_booking"
