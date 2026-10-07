package dev.alllexey.itmowidgets.feature.schedule.ui.list

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.schedule.presentation.SelectedUser
import dev.alllexey.itmowidgets.feature.schedule.ui.list.preview.ScheduleListPreviewData

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LS-0 recorded the XML references as
 * `ScheduleScreen_<state>`. Each state therefore is a function called `ScheduleScreen` in a holder class of its own;
 * the scanner instantiates each holder by reflection.
 */

@Composable
private fun SchedulePreview(
    body: ScheduleScreenBody,
    selectedUser: SelectedUser? = null,
    firstVisibleDay: Int = 0,
) = ItmoPreview {
    ScheduleScreen(
        state = ScheduleScreenState(
            body = body,
            selectedUser = selectedUser,
            canPickFriend = selectedUser == null,
            refreshing = false,
        ),
        actions = ScheduleScreenActions(),
        listState = rememberLazyListState(initialFirstVisibleItemIndex = firstVisibleDay),
    )
}

/** The own schedule opened on today, as the host scrolls it, with yesterday above. */
internal class ScheduleScreenOwnPreview {
    @Preview(name = "own")
    @Composable
    fun ScheduleScreen() = SchedulePreview(
        ScheduleScreenBody.Days(ScheduleListPreviewData.days(ScheduleListPreviewData.ownDays)),
        firstVisibleDay = 1,
    )
}

internal class ScheduleScreenFriendPreview {
    @Preview(name = "friend")
    @Composable
    fun ScheduleScreen() = SchedulePreview(
        ScheduleScreenBody.Days(ScheduleListPreviewData.days(ScheduleListPreviewData.friendDays)),
        selectedUser = ScheduleListPreviewData.friend,
    )
}

internal class ScheduleScreenSkeletonPreview {
    @Preview(name = "skeleton")
    @Composable
    fun ScheduleScreen() = SchedulePreview(ScheduleScreenBody.Loading)
}

internal class ScheduleScreenEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun ScheduleScreen() = SchedulePreview(ScheduleScreenBody.Empty)
}

internal class ScheduleScreenErrorPreview {
    @Preview(name = "error")
    @Composable
    fun ScheduleScreen() = SchedulePreview(ScheduleScreenBody.Failed(AppError.Network))
}

@Composable
private fun UserSchedulePreview() = ItmoPreview {
    UserScheduleScreen(name = ScheduleListPreviewData.friend.name, onBack = {}) {
        ScheduleScreen(
            state = ScheduleScreenState(
                body = ScheduleScreenBody.Days(ScheduleListPreviewData.days(ScheduleListPreviewData.friendDays)),
                selectedUser = null,
                canPickFriend = false,
                refreshing = false,
            ),
            actions = ScheduleScreenActions(),
        )
    }
}

/** A friend's schedule as its own screen (from the profile): the titled bar, then the list without the friends button. */
internal class UserScheduleScreenContentPreview {
    @Preview(name = "content")
    @Composable
    fun UserScheduleScreen() = UserSchedulePreview()
}
