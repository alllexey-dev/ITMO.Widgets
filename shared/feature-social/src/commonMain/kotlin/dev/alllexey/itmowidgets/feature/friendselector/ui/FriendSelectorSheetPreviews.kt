package dev.alllexey.itmowidgets.feature.friendselector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorBody
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorScope
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorUiState
import dev.alllexey.itmowidgets.feature.friendselector.ui.FriendSelectorSamples.friends
import dev.alllexey.itmowidgets.feature.friendselector.ui.FriendSelectorSamples.state

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LC-1c recorded the XML references as
 * `FriendSelectorSheetContent_<state>`. Each state therefore is a function called `FriendSelectorSheetContent` in a
 * holder class of its own; the scanner instantiates each holder by reflection. Every preview is the sheet at its 90 %
 * of the 891 dp capture window, on the sheet's colour, as `FriendSelectorDialogFragment` opens it.
 */

/** The friend list, the own schedule chosen, the chips from the history. */
internal class FriendSelectorSheetFriendsPreview {
    @Preview(name = "friends", heightDp = SHEET_HEIGHT)
    @Composable
    fun FriendSelectorSheetContent() = PickerPreview(state(FriendSelectorBody.Users(friends)))
}

/** A friend chosen: the chip's ring and badge, the row's surface and check, the apply button with the first name. */
internal class FriendSelectorSheetSelectionPreview {
    @Preview(name = "selection", heightDp = SHEET_HEIGHT)
    @Composable
    fun FriendSelectorSheetContent() = PickerPreview(
        state(FriendSelectorBody.Users(friends), selected = friends[1], currentUser = FriendSelectorSamples.me),
    )
}

/** Closed schedules: dimmed rows with `Расписание скрыто` and a lock, and no chip. */
internal class FriendSelectorSheetLockedPreview {
    @Preview(name = "locked", heightDp = SHEET_HEIGHT)
    @Composable
    fun FriendSelectorSheetContent() = PickerPreview(
        state(FriendSelectorBody.Users(FriendSelectorSamples.locked), FriendSelectorSamples.locked),
    )
}

/** Groups: none, two, and more than two. */
internal class FriendSelectorSheetGroupsPreview {
    @Preview(name = "groups", heightDp = SHEET_HEIGHT)
    @Composable
    fun FriendSelectorSheetContent() =
        PickerPreview(state(FriendSelectorBody.Users(FriendSelectorSamples.groupCases)))
}

/** A filter that matches no friend. */
internal class FriendSelectorSheetEmptyFilterPreview {
    @Preview(name = "empty-filter", heightDp = SHEET_HEIGHT)
    @Composable
    fun FriendSelectorSheetContent() =
        PickerPreview(state(FriendSelectorBody.NoMatches), query = FriendSelectorSamples.NO_MATCH_QUERY)
}

/** «Все» with people found by name. */
internal class FriendSelectorSheetPeoplePreview {
    @Preview(name = "people", heightDp = SHEET_HEIGHT)
    @Composable
    fun FriendSelectorSheetContent() = PickerPreview(
        state(FriendSelectorBody.Users(FriendSelectorSamples.people), scope = FriendSelectorScope.ALL),
        query = FriendSelectorSamples.PEOPLE_QUERY,
    )
}

/** «Все» before a name is typed. */
internal class FriendSelectorSheetPeopleIdlePreview {
    @Preview(name = "people-idle", heightDp = SHEET_HEIGHT)
    @Composable
    fun FriendSelectorSheetContent() =
        PickerPreview(state(FriendSelectorBody.PeopleIdle, scope = FriendSelectorScope.ALL))
}

/** «Все» while the search runs. */
internal class FriendSelectorSheetPeopleLoadingPreview {
    @Preview(name = "people-loading", heightDp = SHEET_HEIGHT)
    @Composable
    fun FriendSelectorSheetContent() = PickerPreview(
        state(FriendSelectorBody.PeopleLoading, scope = FriendSelectorScope.ALL),
        query = FriendSelectorSamples.PEOPLE_QUERY,
    )
}

/** «Все» found nobody. */
internal class FriendSelectorSheetPeopleEmptyPreview {
    @Preview(name = "people-empty", heightDp = SHEET_HEIGHT)
    @Composable
    fun FriendSelectorSheetContent() = PickerPreview(
        state(FriendSelectorBody.PeopleEmpty, scope = FriendSelectorScope.ALL),
        query = FriendSelectorSamples.NO_MATCH_QUERY,
    )
}

/** «Все» failed: the error with its retry. */
internal class FriendSelectorSheetPeopleErrorPreview {
    @Preview(name = "people-error", heightDp = SHEET_HEIGHT)
    @Composable
    fun FriendSelectorSheetContent() = PickerPreview(
        state(FriendSelectorBody.PeopleError(FriendSelectorSamples.networkError), scope = FriendSelectorScope.ALL),
        query = FriendSelectorSamples.PEOPLE_QUERY,
    )
}

/** The friend list loaded empty: only the own chip, apply enabled. */
internal class FriendSelectorSheetNoFriendsPreview {
    @Preview(name = "no-friends", heightDp = SHEET_HEIGHT)
    @Composable
    fun FriendSelectorSheetContent() =
        PickerPreview(state(FriendSelectorBody.NoFriends, friends = emptyList(), currentUser = FriendSelectorSamples.me))
}

/** The friend list is not known yet: no scope, apply disabled. */
internal class FriendSelectorSheetLoadingPreview {
    @Preview(name = "loading", heightDp = SHEET_HEIGHT)
    @Composable
    fun FriendSelectorSheetContent() = PickerPreview(FriendSelectorUiState())
}

/** Custom services are off: no scope, no retry, apply disabled. */
internal class FriendSelectorSheetDisabledPreview {
    @Preview(name = "disabled", heightDp = SHEET_HEIGHT)
    @Composable
    fun FriendSelectorSheetContent() = PickerPreview(state(FriendSelectorBody.Disabled))
}

/** The friend list failed: the chips stay, the error offers a retry, apply disabled. */
internal class FriendSelectorSheetErrorPreview {
    @Preview(name = "error", heightDp = SHEET_HEIGHT)
    @Composable
    fun FriendSelectorSheetContent() =
        PickerPreview(state(FriendSelectorBody.Error(FriendSelectorSamples.networkError)))
}

/** The sheet's container as the host draws it: `surfaceContainerLow` with the top corners rounded. */
@Composable
private fun PickerPreview(state: FriendSelectorUiState, query: String = "") = ItmoPreview {
    val shape = ItmoTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(ItmoTheme.colorScheme.surfaceContainerLow),
    ) {
        FriendSelectorSheetContent(state, FriendSelectorActions(), Modifier.fillMaxSize(), query)
    }
}

/** 90 % of the 891 dp capture window, the sheet's `SheetHeight.Tall`. */
private const val SHEET_HEIGHT = 802
