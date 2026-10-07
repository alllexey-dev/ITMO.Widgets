package dev.alllexey.itmowidgets.feature.sport.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBar
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarAction
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.Skeleton
import dev.alllexey.itmowidgets.designsystem.components.state.SkeletonStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.presentation.user.UserSportUiState
import dev.alllexey.itmowidgets.feature.sport.ui.my.SportBookingActions
import dev.alllexey.itmowidgets.feature.sport.ui.my.SportBookingCard
import dev.alllexey.itmowidgets.shared.core.common_back
import dev.alllexey.itmowidgets.shared.core.common_load_error_title
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.core.user_profile_hidden
import dev.alllexey.itmowidgets.shared.core.user_profile_sport_title
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_back
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_exercise
import dev.alllexey.itmowidgets.shared.designsystem.ic_lock
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.user_sport_empty_description
import dev.alllexey.itmowidgets.shared.feature.sport.user_sport_empty_title
import dev.alllexey.itmowidgets.shared.feature.sport.user_sport_hidden_title
import dev.alllexey.itmowidgets.shared.feature.sport.user_sport_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** What another user's sport asks of its host: back, the user's pull, a retry after an error, a building's map. */
@Immutable
class UserSportActions(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenMap: (SportBooking) -> Unit = {},
)

/** Tags for host tests and the instrumented flows. */
object UserSportScreenTestTags {
    const val LIST = "user_sport_list"
    const val LOADING = "user_sport_loading"

    /** The empty, hidden or error state in the list's place. */
    const val STATE = "user_sport_state"
}

/**
 * Another user's confirmed lessons and pending queues, read-only: a card opens nothing and offers no cancellation, only
 * the building's map. The title names the user by first name (`Спорт: Иван`), or reads `Спорт` without a name. The
 * first load shows placeholder cards; a list follows a pull; no bookings, privacy (`Forbidden`: the lock, nothing to
 * retry) and errors (a retry) take the list's place. Stateless; [UserSportRoute] feeds it.
 */
@Composable
fun UserSportScreen(
    state: UserSportUiState,
    name: String,
    time: AcademicTimeProvider,
    actions: UserSportActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(ItmoTheme.colorScheme.surface),
    ) {
        AppTopBar(
            title = if (name.isBlank()) {
                stringResource(CoreRes.string.user_profile_sport_title)
            } else {
                stringResource(Res.string.user_sport_title, name.substringBefore(" "))
            },
            navigation = {
                AppTopBarAction(
                    painterResource(KitRes.drawable.ic_arrow_back),
                    stringResource(CoreRes.string.common_back),
                    onClick = actions.onBack,
                )
            },
        )
        val body = Modifier
            .fillMaxWidth()
            .weight(1f)
        when (state) {
            UserSportUiState.Loading ->
                Skeleton(SkeletonStyle.Cards, body.testTag(UserSportScreenTestTags.LOADING))
            is UserSportUiState.Error -> if (state.error == AppError.Forbidden) {
                UserSportState(
                    painterResource(KitRes.drawable.ic_lock),
                    stringResource(Res.string.user_sport_hidden_title),
                    stringResource(CoreRes.string.user_profile_hidden),
                    body,
                )
            } else {
                UserSportState(
                    painterResource(KitRes.drawable.ic_error),
                    stringResource(CoreRes.string.common_load_error_title),
                    stringResource(state.error.textResource()),
                    body,
                    ContentStateAction(stringResource(CoreRes.string.common_retry), actions.onRetry),
                )
            }
            is UserSportUiState.Content -> if (state.bookings.isEmpty()) {
                UserSportState(
                    painterResource(KitRes.drawable.ic_exercise),
                    stringResource(Res.string.user_sport_empty_title),
                    stringResource(Res.string.user_sport_empty_description),
                    body,
                )
            } else {
                AppRefreshBox(state.refreshing, actions.onRefresh, body) {
                    Bookings(state.bookings, time, actions)
                }
            }
        }
    }
}

@Composable
private fun Bookings(bookings: List<SportBooking>, time: AcademicTimeProvider, actions: UserSportActions) {
    val cardActions = remember(actions) { SportBookingActions(onOpenMap = actions.onOpenMap) }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .testTag(UserSportScreenTestTags.LIST),
        contentPadding = PaddingValues(bottom = ItmoTheme.spacing.group),
    ) {
        items(bookings, key = { it.lessonId }, contentType = { BOOKING_CONTENT_TYPE }) { booking ->
            SportBookingCard(booking, time, cardActions, readOnly = true)
        }
    }
}

@Composable
private fun UserSportState(
    icon: Painter,
    title: String,
    description: String,
    modifier: Modifier,
    action: ContentStateAction? = null,
) {
    ContentState(
        title = title,
        modifier = modifier.testTag(UserSportScreenTestTags.STATE),
        icon = icon,
        description = description,
        action = action,
    )
}

private const val BOOKING_CONTENT_TYPE = "user_sport_booking"
