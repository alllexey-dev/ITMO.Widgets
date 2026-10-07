package dev.alllexey.itmowidgets.feature.friendselector.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorEvent
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorScope
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorUiState
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorViewModel
import dev.alllexey.itmowidgets.shared.core.common_close
import dev.alllexey.itmowidgets.shared.core.friend_picker_title
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_apply_friend
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_apply_my
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of [FriendSelectorSheetContent], for host tests and the Android host's instrumented tests. */
object FriendSelectorTestTags {
    const val RECENT = "friend_picker_recent"
    const val OWN_CHIP = "friend_picker_chip_own"
    const val SCOPE = "friend_picker_scope"
    const val SEARCH = "friend_picker_search"
    const val CLEAR = "friend_picker_search_clear"
    const val LIST = "friend_picker_list"
    const val LOADING = "friend_picker_loading"

    /** The empty, disabled or error state in the list's place. */
    const val STATE = "friend_picker_state"
    const val APPLY = "friend_picker_apply"

    fun chip(isu: Int): String = "friend_picker_chip_$isu"

    fun row(isu: Int): String = "friend_picker_row_$isu"
}

/**
 * What the picker asks of its host. Every effect is the host's: [onOpenProfile] closes the sheet before it opens the
 * person, [onClose] dismisses it, [onApply] confirms the choice. Defaults let previews pass `FriendSelectorActions()`.
 */
@Immutable
class FriendSelectorActions(
    val onQueryChange: (String) -> Unit = {},
    val onScope: (FriendSelectorScope) -> Unit = {},
    val onSelect: (UserSummary) -> Unit = {},
    val onSelectOwn: () -> Unit = {},
    val onOpenProfile: (UserSummary) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onApply: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/**
 * The friend picker with its Koin ViewModel, which reads the opening ISU from the host's arguments
 * (`FriendSelectionContract.ARG_SELECTED_ISU`). The query lives here, saved with the composition, and reaches the
 * ViewModel again after process death. A confirmed choice goes to [onDeliver] once: the person, or `null` for the own
 * schedule. Android hosts it in `FriendSelectorDialogFragment`; the iOS shell hosts the same route.
 */
@Composable
fun FriendSelectorSheetRoute(
    onDeliver: (UserSummary?) -> Unit,
    onOpenProfile: (UserSummary) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendSelectorViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val deliver by rememberUpdatedState(onDeliver)
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(viewModel) {
        if (query.isNotEmpty()) viewModel.onQueryChanged(query)
    }
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is FriendSelectorEvent.Apply -> deliver(event.target)
                }
            }
        }
    }

    FriendSelectorSheetContent(
        state = state,
        actions = FriendSelectorActions(
            onQueryChange = { text ->
                query = text
                viewModel.onQueryChanged(text)
            },
            onScope = viewModel::selectScope,
            onSelect = viewModel::select,
            onSelectOwn = viewModel::selectOwnSchedule,
            onOpenProfile = onOpenProfile,
            onRetry = viewModel::retry,
            onApply = viewModel::apply,
            onClose = onClose,
        ),
        modifier = modifier,
        query = query,
    )
}

/**
 * The body of the friend picker sheet (`dialog_friend_selector.xml`): the title with close, the recent chips (the own
 * schedule first, with its avatar), the `Друзья / Все` scope once the friend list has loaded, the search field whose
 * hint follows the scope, then the rows or a state, and the apply button labelled by the choice, enabled once the
 * friend list has loaded. A closed schedule's row and a long press on any row lead to [FriendSelectorActions.onOpenProfile].
 * Stateless: [FriendSelectorSheetRoute] feeds it. The host owns the sheet's container (90 % of the screen) and every
 * effect.
 */
@Composable
fun FriendSelectorSheetContent(
    state: FriendSelectorUiState,
    actions: FriendSelectorActions,
    modifier: Modifier = Modifier,
    query: String = "",
) {
    SheetScaffold(
        title = stringResource(CoreRes.string.friend_picker_title),
        modifier = modifier,
        close = SheetClose(stringResource(CoreRes.string.common_close), actions.onClose),
        footer = { ApplyButton(state, actions.onApply) },
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val margin = Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin)
            RecentChips(state, actions, margin)
            if (state.showsScope) {
                ScopeToggle(state.scope, actions.onScope, margin.padding(top = ItmoTheme.spacing.content))
            }
            PickerSearchField(
                query = query,
                scope = state.scope,
                onQueryChange = actions.onQueryChange,
                modifier = margin
                    .fillMaxWidth()
                    .padding(top = ItmoTheme.spacing.compact),
            )
            // The kit row pads 16 dp, so its avatars line up with the chips' avatars at 28 dp, as in the XML.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = ItmoTheme.spacing.content)
                    .padding(top = ItmoTheme.spacing.related),
            ) {
                PickerBody(state, actions)
            }
        }
    }
}

/** `apply_button`: a filled 56 dp button with a check, `Показать моё расписание` or the chosen person's first name. */
@Composable
private fun ApplyButton(state: FriendSelectorUiState, onApply: () -> Unit) {
    val target = state.applyTarget
    val label = if (target == null) {
        stringResource(Res.string.friend_picker_apply_my)
    } else {
        stringResource(Res.string.friend_picker_apply_friend, target.name.substringBefore(" "))
    }
    ProgressButton(
        label = label,
        onClick = onApply,
        modifier = Modifier
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(top = ItmoTheme.spacing.compact)
            .fillMaxWidth()
            .heightIn(min = ApplyButtonHeight)
            .testTag(FriendSelectorTestTags.APPLY),
        enabled = state.canApply,
        icon = painterResource(KitRes.drawable.ic_check),
    )
}

/** `apply_button`'s `minHeight`. */
private val ApplyButtonHeight = 56.dp
