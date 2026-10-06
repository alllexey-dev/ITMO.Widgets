package dev.alllexey.itmowidgets.feature.social.ui.userfriends

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.social.presentation.UserFriendsUiState
import dev.alllexey.itmowidgets.feature.social.ui.list.preview.UserListPreviewData

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LC-1c recorded the XML references as
 * `UserFriendsScreen_<state>`. Each state therefore is a function called `UserFriendsScreen` in a holder class of its
 * own; the scanner instantiates each holder by reflection.
 */

@Composable
private fun UserFriendsPreview(state: UserFriendsUiState) = ItmoPreview {
    UserFriendsScreen(
        state = state,
        ownerName = UserListPreviewData.OWNER_NAME,
        onRefresh = {},
        onRetry = {},
        onOpenProfile = {},
        onOpenSettings = {},
        onBack = {},
    )
}

internal class UserFriendsScreenContentPreview {
    @Preview(name = "content")
    @Composable
    fun UserFriendsScreen() = UserFriendsPreview(UserFriendsUiState.Content(UserListPreviewData.Friends))
}

internal class UserFriendsScreenLoadingPreview {
    @Preview(name = "loading")
    @Composable
    fun UserFriendsScreen() = UserFriendsPreview(UserFriendsUiState.Loading)
}

internal class UserFriendsScreenEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun UserFriendsScreen() = UserFriendsPreview(UserFriendsUiState.Content(emptyList()))
}

internal class UserFriendsScreenDeniedPreview {
    @Preview(name = "denied")
    @Composable
    fun UserFriendsScreen() = UserFriendsPreview(UserFriendsUiState.Hidden)
}

internal class UserFriendsScreenDisabledPreview {
    @Preview(name = "disabled")
    @Composable
    fun UserFriendsScreen() = UserFriendsPreview(UserFriendsUiState.Disabled)
}

internal class UserFriendsScreenErrorPreview {
    @Preview(name = "error")
    @Composable
    fun UserFriendsScreen() = UserFriendsPreview(UserFriendsUiState.Error(AppError.Network))
}
