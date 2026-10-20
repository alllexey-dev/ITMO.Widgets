package dev.alllexey.itmowidgets.feature.social.ui.profile

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.feature.social.presentation.CardAction
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileEvent
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileViewModel
import dev.alllexey.itmowidgets.feature.social.ui.reviews.TeacherReviewActions
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.compose.strings.common_cancel
import dev.alllexey.itmowidgets.shared.compose.strings.common_partial_load_error
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.compose.strings.friends_action_failed
import dev.alllexey.itmowidgets.shared.compose.strings.friends_remove_confirm_message
import dev.alllexey.itmowidgets.shared.compose.strings.friends_remove_confirm_title
import dev.alllexey.itmowidgets.shared.compose.strings.teacher_review_delete
import dev.alllexey.itmowidgets.shared.compose.strings.teacher_review_delete_confirm
import dev.alllexey.itmowidgets.shared.compose.strings.user_action_remove
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Where the person profile leaves to; every exit is the host's (share, clipboard, navigation, links). The exits that
 * need the person get the page on screen at the moment of the tap and do not fire without one.
 */
class UserProfileExits(
    val onBack: () -> Unit = {},
    /** `Поделиться`, offered only with a page. */
    val onShare: (UserProfileUiState.Content) -> Unit = {},
    val onCopyIsu: (isu: Int) -> Unit = {},
    val onFriends: (UserProfileUiState.Content) -> Unit = {},
    val onSchedule: (UserProfileUiState.Content) -> Unit = {},
    val onSport: (UserProfileUiState.Content) -> Unit = {},
    /** `Пригласить в ITMO.Widgets`: shares the invitation. */
    val onInvite: () -> Unit = {},
    /** `Написать отзыв` and the own review's `Изменить`: the editor for this teacher. */
    val onReviewEditor: (UserProfileUiState.Content) -> Unit = {},
    val onReport: (UserProfileUiState.Content, reviewId: String) -> Unit = { _, _ -> },
    /** A review author's name. */
    val onOpenProfile: (isu: Int) -> Unit = {},
    /** The source link of a copied review. */
    val onSource: (url: String) -> Unit = {},
)

/**
 * The person profile with its Koin ViewModel, which reads the ISU from the destination's arguments. A page with a part
 * that failed shows the single `Часть данных не загрузилась` snackbar with `Повторить`; a failed action shows a
 * snackbar of its own. Removing a friend and deleting the own review ask in a dialog first. Android hosts it in the
 * Compose shell; iOS draws the same ViewModel in SwiftUI.
 */
@Composable
fun UserProfileRoute(exits: UserProfileExits, viewModel: UserProfileViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    var pendingRemoval by remember { mutableStateOf<UiText?>(null) }
    var pendingReviewDeletion by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    // A snackbar waits until it is gone; a confirmation must not wait behind it.
                    is UserProfileEvent.ActionFailed -> launch {
                        snackbars.showSnackbar(
                            message = getString(Res.string.friends_action_failed, event.error.toUiText().resolve()),
                            duration = SnackbarDuration.Long,
                        )
                    }
                    UserProfileEvent.LoadFailed -> launch {
                        val result = snackbars.showSnackbar(
                            message = getString(Res.string.common_partial_load_error),
                            actionLabel = getString(Res.string.common_retry),
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.refresh(RefreshMode.Force)
                    }
                    is UserProfileEvent.ConfirmRemove -> pendingRemoval = event.name
                    UserProfileEvent.ConfirmDeleteReview -> pendingReviewDeletion = true
                }
            }
        }
    }

    fun withPage(exit: (UserProfileUiState.Content) -> Unit) {
        (viewModel.uiState.value as? UserProfileUiState.Content)?.let(exit)
    }

    val actions = UserProfileActions(
        onBack = exits.onBack,
        onShare = { withPage(exits.onShare) },
        onRetry = { viewModel.refresh(RefreshMode.Force) },
        onCopyIsu = exits.onCopyIsu,
        onAction = { action ->
            when (action) {
                CardAction.WRITE_REVIEW -> withPage(exits.onReviewEditor)
                CardAction.INVITE -> exits.onInvite()
                else -> viewModel.onAction(action)
            }
        },
        onRemoveFriend = viewModel::requestRemoveFriend,
        onFriends = { withPage(exits.onFriends) },
        onSchedule = { withPage(exits.onSchedule) },
        onSport = { withPage(exits.onSport) },
        onToggleSummary = viewModel::toggleSummaryScales,
        review = TeacherReviewActions(
            onVote = viewModel::vote,
            onReport = { id -> withPage { exits.onReport(it, id) } },
            onAuthor = exits.onOpenProfile,
            onSource = exits.onSource,
            onEdit = { withPage(exits.onReviewEditor) },
            onDelete = viewModel::requestDeleteOwnReview,
        ),
    )

    UserProfileScreen(
        state = state,
        actions = actions,
        snackbarHostState = snackbars,
    )

    pendingRemoval?.let { name ->
        ConfirmDialog(
            title = stringResource(Res.string.friends_remove_confirm_title),
            confirmLabel = stringResource(Res.string.user_action_remove),
            dismissLabel = stringResource(Res.string.common_cancel),
            onConfirm = {
                pendingRemoval = null
                viewModel.removeFriend()
            },
            onDismiss = { pendingRemoval = null },
            text = stringResource(Res.string.friends_remove_confirm_message, name.asString()),
        )
    }
    if (pendingReviewDeletion) {
        ConfirmDialog(
            title = stringResource(Res.string.teacher_review_delete_confirm),
            confirmLabel = stringResource(Res.string.teacher_review_delete),
            dismissLabel = stringResource(Res.string.common_cancel),
            onConfirm = {
                pendingReviewDeletion = false
                viewModel.deleteOwnReview()
            },
            onDismiss = { pendingReviewDeletion = false },
        )
    }
}
